package backend.services.impl.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.annotations.retry.RetryOnConcurrency;
import backend.dtos.requests.inventory.CreateTransferRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.InventoryTransferResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.UnprocessableEntityException;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.InventoryTransfer;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompanyCapability;
import backend.models.enums.TransferStatus;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.InventoryTransferRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.InventoryTransferService;
import backend.services.intf.inventory.LocationInventoryService;

/**
 * Manages {@link InventoryTransfer} lifecycle: PENDING → IN_TRANSIT → RECEIVED (or PENDING →
 * CANCELLED).
 *
 * <p><b>Transfers do not reserve stock.</b> Source stock is validated on create (early feedback)
 * and re-validated on dispatch, but is moved only on receipt — the atomic decrement/increment in
 * {@link LocationInventoryService#applyTransferStock} is the source of truth. A transfer can
 * therefore legitimately fail at receive if source stock was consumed elsewhere in the meantime.
 *
 * <p>v1 is product-level only; variant-managed products are rejected at create.
 */
@Service
public class InventoryTransferServiceImpl implements InventoryTransferService {

    private final InventoryTransferRepository transferRepository;
    private final InventoryLocationRepository locationRepository;
    private final LocationStockRepository locationStockRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final CompanyAccessService companyAccessService;
    private final LocationInventoryService locationInventoryService;

    public InventoryTransferServiceImpl(
            InventoryTransferRepository transferRepository,
            InventoryLocationRepository locationRepository,
            LocationStockRepository locationStockRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            UserRepository userRepository,
            CompanyAccessService companyAccessService,
            LocationInventoryService locationInventoryService) {
        this.transferRepository = transferRepository;
        this.locationRepository = locationRepository;
        this.locationStockRepository = locationStockRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
        this.companyAccessService = companyAccessService;
        this.locationInventoryService = locationInventoryService;
    }

    @Override
    @Transactional
    public InventoryTransferResponse createTransfer(UUID companyId, UUID ownerId, CreateTransferRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        // Distinct source/destination is enforced by CreateTransferRequest#isDistinctLocations
        // (Bean Validation, surfaced as 400 before this method runs).
        InventoryLocation from = locationRepository.findByIdAndCompanyId(request.getFromLocationId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + request.getFromLocationId()));
        InventoryLocation to = locationRepository.findByIdAndCompanyId(request.getToLocationId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + request.getToLocationId()));
        Product product = productRepository.findByIdAndCompanyId(request.getProductId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        // v1 moves product-level stock; a variant-managed product would be ambiguous.
        if (variantRepository.existsByProductId(product.getId())) {
            throw new UnprocessableEntityException(
                    "Product '" + product.getName() + "' is variant-managed; product-level transfers are not supported in v1");
        }

        validateSourceStock(request.getFromLocationId(), product.getId(), request.getQuantity());

        InventoryTransfer transfer = new InventoryTransfer();
        transfer.setCompany(company);
        transfer.setProduct(product);
        transfer.setFromLocation(from);
        transfer.setToLocation(to);
        transfer.setQuantity(request.getQuantity());
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setNotes(request.getNotes());
        transfer.setCreatedBy(userRepository.getReferenceById(ownerId));

        return toResponse(transferRepository.save(transfer));
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryTransferResponse getTransfer(UUID companyId, UUID transferId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);
        return toResponse(loadTransfer(companyId, transferId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InventoryTransferResponse> listTransfers(UUID companyId, UUID ownerId,
                                                                  TransferStatus status, UUID locationId,
                                                                  Pageable pageable) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);
        Page<InventoryTransferResponse> page = transferRepository
                .findAllByCompanyFiltered(companyId, status, locationId, pageable)
                .map(this::toResponse);
        return new PagedResponse<>(page);
    }

    @Override
    @RetryOnConcurrency
    @Transactional
    public InventoryTransferResponse markInTransit(UUID companyId, UUID transferId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        InventoryTransfer transfer = loadTransfer(companyId, transferId);
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new ConflictException("Only PENDING transfers can be dispatched; current status: " + transfer.getStatus());
        }

        // Fail at dispatch rather than late at receipt if the source can no longer cover the move.
        validateSourceStock(transfer.getFromLocation().getId(), transfer.getProduct().getId(), transfer.getQuantity());

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transfer.setInTransitAt(Instant.now());
        return toResponse(transferRepository.save(transfer));
    }

    @Override
    @RetryOnConcurrency
    @Transactional
    public InventoryTransferResponse receiveTransfer(UUID companyId, UUID transferId, UUID receivedByUserId) {
        companyAccessService.require(companyId, receivedByUserId, CompanyCapability.MANAGE_INVENTORY);

        InventoryTransfer transfer = loadTransfer(companyId, transferId);
        if (transfer.getStatus() != TransferStatus.IN_TRANSIT) {
            throw new ConflictException("Only IN_TRANSIT transfers can be received; current status: " + transfer.getStatus());
        }

        UUID productId = transfer.getProduct().getId();
        int qty = transfer.getQuantity();
        UUID fromId = transfer.getFromLocation().getId();
        UUID toId = transfer.getToLocation().getId();
        String ref = "Transfer " + transfer.getId();

        // Claim the receipt first: flushing the RECEIVED status (and its @Version bump) before any
        // stock is touched means a concurrent duplicate receive fails the optimistic-lock check here
        // — and @RetryOnConcurrency re-reads the now-RECEIVED transfer and returns a clean 409 —
        // rather than the loser blocking on the source row and throwing a misleading 422 from the
        // stock leg. If a stock leg later fails legitimately, this flush rolls back with it.
        transfer.setStatus(TransferStatus.RECEIVED);
        transfer.setReceivedAt(Instant.now());
        transfer.setReceivedBy(userRepository.getReferenceById(receivedByUserId));
        transferRepository.saveAndFlush(transfer);

        // Apply both legs in deterministic (locationId-ascending) order to avoid DB-row deadlocks,
        // keeping the correct signed delta bound to each location.
        List<TransferLeg> legs = List.of(
                new TransferLeg(fromId, -qty, AdjustmentReason.TRANSFER_OUT, ref + " out to " + toId),
                new TransferLeg(toId, qty, AdjustmentReason.TRANSFER_IN, ref + " in from " + fromId));

        legs.stream()
                .sorted(Comparator.comparing(TransferLeg::locationId))
                .forEach(leg -> locationInventoryService.applyTransferStock(
                        companyId, leg.locationId(), productId, receivedByUserId, leg.delta(), leg.reason(), leg.note()));

        return toResponse(transfer);
    }

    @Override
    @RetryOnConcurrency
    @Transactional
    public InventoryTransferResponse cancelTransfer(UUID companyId, UUID transferId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        InventoryTransfer transfer = loadTransfer(companyId, transferId);
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new ConflictException("Only PENDING transfers can be cancelled; current status: " + transfer.getStatus());
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCancelledAt(Instant.now());
        transfer.setCancelledBy(userRepository.getReferenceById(ownerId));
        return toResponse(transferRepository.save(transfer));
    }

    // --- Helpers ---

    private InventoryTransfer loadTransfer(UUID companyId, UUID transferId) {
        return transferRepository.findByIdAndCompanyId(transferId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with id: " + transferId));
    }

    private void validateSourceStock(UUID fromLocationId, UUID productId, int quantity) {
        LocationStock source = locationStockRepository
                .findByLocationIdAndProductIdAndVariantRef(fromLocationId, productId, null)
                .orElseThrow(() -> new UnprocessableEntityException(
                        "No stock at the source location for this product"));
        if (source.getStock() < quantity) {
            throw new UnprocessableEntityException(
                    "Insufficient stock at source location. Available: " + source.getStock() + ", requested: " + quantity);
        }
    }

    private InventoryTransferResponse toResponse(InventoryTransfer t) {
        return new InventoryTransferResponse(
                t.getId(),
                t.getCompany().getId(),
                t.getProduct().getId(),
                t.getProduct().getName(),
                t.getFromLocation().getId(),
                t.getFromLocation().getName(),
                t.getToLocation().getId(),
                t.getToLocation().getName(),
                t.getQuantity(),
                t.getStatus(),
                t.getNotes(),
                t.getCreatedBy() != null ? t.getCreatedBy().getId() : null,
                t.getReceivedBy() != null ? t.getReceivedBy().getId() : null,
                t.getCancelledBy() != null ? t.getCancelledBy().getId() : null,
                t.getCreatedAt(),
                t.getInTransitAt(),
                t.getReceivedAt(),
                t.getCancelledAt());
    }

    private record TransferLeg(UUID locationId, int delta, AdjustmentReason reason, String note) {}
}
