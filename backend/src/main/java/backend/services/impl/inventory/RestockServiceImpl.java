package backend.services.impl.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.inventory.CreateRestockRequest;
import backend.dtos.requests.inventory.UpdateRestockRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.RestockRequestResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.CompanyCapability;
import backend.services.intf.company.CompanyAccessService;
import backend.models.core.Company;
import backend.models.core.InventoryAdjustment;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.RestockRequest;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.RestockStatus;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.RestockRequestRepository;
import backend.repositories.UserRepository;
import backend.events.inventory.StockRestoredEvent;
import backend.services.intf.orders.OrderService;
import backend.services.intf.inventory.RestockService;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

@Service
public class RestockServiceImpl implements RestockService {

    private static final Logger log = LoggerFactory.getLogger(RestockServiceImpl.class);

    private static final String PRODUCT_LOCK_PREFIX = "lock:product:";
    private static final String VARIANT_LOCK_PREFIX = "lock:variant:";
    private static final String LOC_STOCK_LOCK_PREFIX = "lock:locstock:";
    private static final long LOCK_TTL_SECONDS = 10;
    private static final int LOCK_RETRY_ATTEMPTS = 5;
    private static final long LOCK_RETRY_DELAY_MS = 100;

    private final RestockRequestRepository restockRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLocationRepository locationRepository;
    private final LocationStockRepository locationStockRepository;
    private final CompanyAccessService companyAccessService;
    private final UserRepository userRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final backend.services.intf.CacheService cacheService;
    private final StockAlertService stockAlertService;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    public RestockServiceImpl(
            RestockRequestRepository restockRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            InventoryLocationRepository locationRepository,
            LocationStockRepository locationStockRepository,
            CompanyAccessService companyAccessService,
            UserRepository userRepository,
            InventoryAdjustmentRepository adjustmentRepository,
            backend.services.intf.CacheService cacheService,
            StockAlertService stockAlertService,
            OrderService orderService,
            ApplicationEventPublisher eventPublisher) {
        this.restockRepository = restockRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.locationStockRepository = locationStockRepository;
        this.companyAccessService = companyAccessService;
        this.userRepository = userRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.cacheService = cacheService;
        this.stockAlertService = stockAlertService;
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PagedResponse<RestockRequestResponse> listRestockRequests(
            long companyId, long ownerId, RestockStatus status, Long productId, int page, int size) {
        assertCompanyOwnership(companyId, ownerId);

        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (status != null && productId != null) {
            return new PagedResponse<>(
                    restockRepository.findAllByCompanyIdAndStatusAndProductId(companyId, status, productId, pageable)
                            .map(this::toResponse));
        } else if (status != null) {
            return new PagedResponse<>(
                    restockRepository.findAllByCompanyIdAndStatus(companyId, status, pageable)
                            .map(this::toResponse));
        } else if (productId != null) {
            return new PagedResponse<>(
                    restockRepository.findAllByCompanyIdAndProductId(companyId, productId, pageable)
                            .map(this::toResponse));
        }
        return new PagedResponse<>(
                restockRepository.findAllByCompanyId(companyId, pageable)
                        .map(this::toResponse));
    }

    @Override
    @Transactional
    public RestockRequestResponse createRestockRequest(long companyId, long ownerId, CreateRestockRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        Product product = productRepository.findByIdAndCompanyId(request.getProductId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        ProductVariant variant = null;
        if (request.getVariantId() != null) {
            variant = variantRepository.findByIdAndProductId(request.getVariantId(), product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + request.getVariantId()));
        }

        InventoryLocation location = null;
        if (request.getLocationId() != null) {
            location = locationRepository.findByIdAndCompanyId(request.getLocationId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + request.getLocationId()));
        }

        RestockRequest rr = new RestockRequest();
        rr.setCompany(company);
        rr.setProduct(product);
        rr.setVariant(variant);
        rr.setLocation(location);
        rr.setRequestedQty(request.getRequestedQty());
        rr.setExpectedArrivalDate(request.getExpectedArrivalDate());
        rr.setSupplierNote(request.getSupplierNote());
        rr.setStatus(RestockStatus.PENDING);
        rr.setCreatedBy(userRepository.getReferenceById(ownerId));

        return toResponse(restockRepository.save(rr));
    }

    @Override
    public RestockRequestResponse getRestockRequest(long companyId, long restockId, long ownerId) {
        assertCompanyOwnership(companyId, ownerId);
        RestockRequest rr = restockRepository.findByIdAndCompanyId(restockId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Restock request not found with id: " + restockId));
        return toResponse(rr);
    }

    @Override
    @Transactional
    public RestockRequestResponse updateRestockRequest(long companyId, long restockId, long ownerId, UpdateRestockRequest request) {
        assertCompanyOwnership(companyId, ownerId);

        RestockRequest rr = restockRepository.findByIdAndCompanyId(restockId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Restock request not found with id: " + restockId));

        if (request.getExpectedArrivalDate() != null) rr.setExpectedArrivalDate(request.getExpectedArrivalDate());
        if (request.getSupplierNote() != null) rr.setSupplierNote(request.getSupplierNote());

        if (request.getStatus() != null) {
            applyStatusTransition(rr, request, ownerId);
        }

        return toResponse(restockRepository.save(rr));
    }

    @Override
    @Transactional
    public void deleteRestockRequest(long companyId, long restockId, long ownerId) {
        assertCompanyOwnership(companyId, ownerId);

        RestockRequest rr = restockRepository.findByIdAndCompanyId(restockId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Restock request not found with id: " + restockId));

        if (rr.getStatus() != RestockStatus.PENDING && rr.getStatus() != RestockStatus.CANCELLED) {
            throw new BadRequestException("Only PENDING or CANCELLED restock requests can be deleted");
        }

        restockRepository.delete(rr);
    }

    // --- Status transitions ---

    private void applyStatusTransition(RestockRequest rr, UpdateRestockRequest request, long ownerId) {
        RestockStatus current = rr.getStatus();
        RestockStatus target = request.getStatus();

        switch (current) {
            case PENDING -> {
                if (target == RestockStatus.IN_TRANSIT) {
                    rr.setStatus(RestockStatus.IN_TRANSIT);
                } else if (target == RestockStatus.CANCELLED) {
                    rr.setStatus(RestockStatus.CANCELLED);
                } else {
                    throw new BadRequestException("Invalid transition from PENDING to " + target);
                }
            }
            case IN_TRANSIT -> {
                if (target == RestockStatus.RECEIVED) {
                    if (request.getReceivedQty() == null || request.getReceivedQty() < 1) {
                        throw new BadRequestException("receivedQty must be >= 1 when transitioning to RECEIVED");
                    }
                    if (request.getReceivedQty() > rr.getRequestedQty()) {
                        throw new BadRequestException(
                            "receivedQty (" + request.getReceivedQty() + ") cannot exceed requestedQty (" + rr.getRequestedQty() + ")");
                    }
                    handleReceivedTransition(rr, request.getReceivedQty(), ownerId);
                } else if (target == RestockStatus.CANCELLED) {
                    rr.setStatus(RestockStatus.CANCELLED);
                } else {
                    throw new BadRequestException("Invalid transition from IN_TRANSIT to " + target);
                }
            }
            default -> throw new BadRequestException("Cannot transition from status: " + current);
        }
    }

    private void handleReceivedTransition(RestockRequest rr, int receivedQty, long ownerId) {
        long productId = rr.getProduct().getId();
        Long variantId = rr.getVariant() != null ? rr.getVariant().getId() : null;
        Long locationId = rr.getLocation() != null ? rr.getLocation().getId() : null;

        // Acquire locks in sorted order to prevent deadlocks
        String stockLockKey = (variantId != null)
                ? VARIANT_LOCK_PREFIX + variantId
                : PRODUCT_LOCK_PREFIX + productId;

        long variantRefForLoc = variantId != null ? variantId : 0L;
        String locLockKey = locationId != null
                ? LOC_STOCK_LOCK_PREFIX + locationId + ":" + variantRefForLoc
                : null;

        String lockToken = UUID.randomUUID().toString();
        boolean stockLockAcquired = false;
        boolean locLockAcquired = false;

        try {
            stockLockAcquired = acquireLock(stockLockKey, lockToken);
            if (!stockLockAcquired) {
                throw new ConflictException("Stock is currently being updated, please try again shortly");
            }

            if (locLockKey != null) {
                locLockAcquired = acquireLock(locLockKey, lockToken);
                if (!locLockAcquired) {
                    throw new ConflictException("Location stock is currently being updated, please try again shortly");
                }
            }

            // Capture stock before increment (lock is held — safe from TOCTOU)
            int previousStock;
            if (variantId != null) {
                previousStock = variantRepository.findById(variantId)
                        .map(v -> v.getStock() != null ? v.getStock() : 0).orElse(0);
            } else {
                previousStock = productRepository.findById(productId)
                        .map(p -> p.getStock() != null ? p.getStock() : 0).orElse(0);
            }

            // Increment product or variant stock
            int updated = (variantId != null)
                    ? variantRepository.adjustStock(variantId, receivedQty)
                    : productRepository.adjustStock(productId, receivedQty);

            if (updated == 0) {
                throw new BadRequestException("Stock is not tracked for this product/variant. Set an initial stock value first.");
            }

            // Upsert location stock if a location is specified
            if (locationId != null) {
                LocationStock ls = locationStockRepository
                        .findByLocationIdAndProductIdAndVariantRef(locationId, productId, variantRefForLoc)
                        .orElse(null);

                if (ls == null) {
                    ls = new LocationStock();
                    ls.setLocation(locationRepository.getReferenceById(locationId));
                    ls.setProduct(rr.getProduct());
                    ls.setVariant(rr.getVariant());
                    ls.setVariantRef(variantRefForLoc);
                    ls.setStock(receivedQty);
                    locationStockRepository.save(ls);
                } else {
                    int lsUpdated = locationStockRepository.adjustStock(ls.getId(), receivedQty);
                    if (lsUpdated == 0) {
                        log.warn("Location stock adjust returned 0 for locationStockId={} — skipping location stock update", ls.getId());
                    }
                }
            }

            // Audit record
            InventoryAdjustment adj = new InventoryAdjustment();
            adj.setProduct(rr.getProduct());
            adj.setVariant(rr.getVariant());
            adj.setAdjustedBy(userRepository.getReferenceById(ownerId));
            adj.setDelta(receivedQty);
            adj.setPreviousStock(previousStock);
            adj.setNewStock(previousStock + receivedQty);
            adj.setReason(AdjustmentReason.RESTOCK);
            adj.setNote("Restock received — request #" + rr.getId());
            adjustmentRepository.save(adj);

            // Update restock request
            rr.setStatus(RestockStatus.RECEIVED);
            rr.setReceivedQty(receivedQty);
            rr.setReceivedAt(java.time.Instant.now());

            if (previousStock == 0) {
                long variantRefValue = variantId != null ? variantId : 0L;
                eventPublisher.publishEvent(new StockRestoredEvent(productId, variantId, variantRefValue));
            }

            // Trigger backorder fulfillment FIFO
            orderService.fulfillPendingBackorders(productId, variantId, receivedQty, locationId);

            // Low stock check (unlikely to fire after receiving, but correct to call)
            if (variantId != null) {
                variantRepository.findById(variantId).ifPresent(v ->
                        stockAlertService.checkAndAlert(
                                productId, rr.getProduct().getName(),
                                variantId, v.getSku(),
                                v.getStock() != null ? v.getStock() : 0,
                                v.getLowStockThreshold()));
            } else {
                productRepository.findById(productId).ifPresent(p ->
                        stockAlertService.checkAndAlert(
                                productId, p.getName(),
                                null, null,
                                p.getStock() != null ? p.getStock() : 0,
                                p.getLowStockThreshold()));
            }

        } finally {
            if (locLockAcquired) releaseLock(locLockKey, lockToken);
            if (stockLockAcquired) releaseLock(stockLockKey, lockToken);
        }
    }

    private boolean acquireLock(String key, String token) {
        for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
            if (cacheService.tryLock(key, token, LOCK_TTL_SECONDS)) return true;
            try {
                Thread.sleep(LOCK_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConflictException("Operation interrupted, please try again");
            }
        }
        return false;
    }

    private void releaseLock(String key, String token) {
        try {
            cacheService.unlock(key, token);
        } catch (Exception e) {
            log.error("Failed to release lock {}: {}", key, e.getMessage());
        }
    }

    // --- Helpers ---

    private void assertCompanyOwnership(long companyId, long ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);
    }

    private RestockRequestResponse toResponse(RestockRequest rr) {
        return new RestockRequestResponse(
                rr.getId(),
                rr.getCompany().getId(),
                rr.getProduct().getId(),
                rr.getProduct().getName(),
                rr.getVariant() != null ? rr.getVariant().getId() : null,
                rr.getVariant() != null ? rr.getVariant().getSku() : null,
                rr.getLocation() != null ? rr.getLocation().getId() : null,
                rr.getLocation() != null ? rr.getLocation().getName() : null,
                rr.getRequestedQty(),
                rr.getReceivedQty(),
                rr.getExpectedArrivalDate(),
                rr.getStatus().name(),
                rr.getSupplierNote(),
                rr.getCreatedBy().getId(),
                rr.getCreatedAt(),
                rr.getUpdatedAt()
        );
    }
}
