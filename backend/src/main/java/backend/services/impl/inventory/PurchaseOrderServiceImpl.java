package backend.services.impl.inventory;

import backend.dtos.requests.inventory.CreatePORequest;
import backend.dtos.requests.inventory.POLineItemRequest;
import backend.dtos.requests.inventory.ReceiveItemsRequest;
import backend.dtos.requests.inventory.ReceiveLineItem;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.PurchaseOrderItemResponse;
import backend.dtos.responses.inventory.PurchaseOrderResponse;
import backend.events.email.EmailEvent;
import backend.events.inventory.StockRestoredEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.InventoryAdjustment;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.PurchaseOrder;
import backend.models.core.PurchaseOrderItem;
import backend.models.core.Supplier;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompanyCapability;
import backend.models.enums.POStatus;
import backend.models.enums.RestockStatus;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.PurchaseOrderItemRepository;
import backend.repositories.PurchaseOrderRepository;
import backend.repositories.RestockRequestRepository;
import backend.repositories.SupplierRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.PurchaseOrderService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);

    private static final String PRODUCT_LOCK_PREFIX = "lock:product:";
    private static final String VARIANT_LOCK_PREFIX  = "lock:variant:";
    private static final String LOC_STOCK_LOCK_PREFIX = "lock:locstock:";
    private static final long   LOCK_TTL_SECONDS      = 10;
    private static final int    LOCK_RETRY_ATTEMPTS   = 5;
    private static final long   LOCK_RETRY_DELAY_MS   = 100;

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderItemRepository itemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLocationRepository locationRepository;
    private final RestockRequestRepository restockRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final LocationStockRepository locationStockRepository;
    private final UserRepository userRepository;
    private final CompanyAccessService companyAccessService;
    private final CacheService cacheService;
    private final EmailService emailService;
    private final StockAlertService stockAlertService;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    public PurchaseOrderServiceImpl(
            PurchaseOrderRepository poRepository,
            PurchaseOrderItemRepository itemRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            InventoryLocationRepository locationRepository,
            RestockRequestRepository restockRepository,
            InventoryAdjustmentRepository adjustmentRepository,
            LocationStockRepository locationStockRepository,
            UserRepository userRepository,
            CompanyAccessService companyAccessService,
            CacheService cacheService,
            EmailService emailService,
            StockAlertService stockAlertService,
            OrderService orderService,
            ApplicationEventPublisher eventPublisher) {
        this.poRepository = poRepository;
        this.itemRepository = itemRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.restockRepository = restockRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.locationStockRepository = locationStockRepository;
        this.userRepository = userRepository;
        this.companyAccessService = companyAccessService;
        this.cacheService = cacheService;
        this.emailService = emailService;
        this.stockAlertService = stockAlertService;
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public PurchaseOrderResponse createPO(UUID companyId, UUID ownerId, CreatePORequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        Supplier supplier = supplierRepository.findByIdAndCompanyId(request.getSupplierId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.getSupplierId()));

        PurchaseOrder po = new PurchaseOrder();
        po.setCompany(company);
        po.setSupplier(supplier);
        po.setCreatedBy(userRepository.getReferenceById(ownerId));
        po.setStatus(POStatus.DRAFT);
        po.setExpectedArrivalDate(request.getExpectedArrivalDate());
        po.setNotes(request.getNotes());
        po.setRestockRequestId(request.getRestockRequestId());

        long totalCostCents = 0;
        PurchaseOrder savedPo = poRepository.save(po);

        List<PurchaseOrderItem> savedItems = new java.util.ArrayList<>();
        for (POLineItemRequest lineReq : request.getItems()) {
            Product product = productRepository.findByIdAndCompanyId(lineReq.getProductId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + lineReq.getProductId()));

            ProductVariant variant = null;
            if (lineReq.getVariantId() != null) {
                variant = variantRepository.findByIdAndProductId(lineReq.getVariantId(), product.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + lineReq.getVariantId()));
            }

            InventoryLocation location = null;
            if (lineReq.getLocationId() != null) {
                location = locationRepository.findByIdAndCompanyId(lineReq.getLocationId(), companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + lineReq.getLocationId()));
            }

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(savedPo);
            item.setProduct(product);
            item.setVariant(variant);
            item.setLocation(location);
            item.setOrderedQty(lineReq.getOrderedQty());
            item.setUnitCostCents(lineReq.getUnitCostCents());
            savedItems.add(itemRepository.save(item));

            totalCostCents += (long) lineReq.getOrderedQty() * lineReq.getUnitCostCents();
        }

        savedPo.setTotalCostCents(totalCostCents);
        poRepository.save(savedPo);

        return toResponse(savedPo, savedItems);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse sendPO(UUID companyId, UUID poId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        PurchaseOrder po = loadPO(companyId, poId);
        if (po.getStatus() != POStatus.DRAFT) {
            throw new BadRequestException("PO can only be sent from DRAFT status, current: " + po.getStatus());
        }

        String supplierEmail = po.getSupplier().getEmail();
        if (supplierEmail == null || supplierEmail.isBlank()) {
            throw new BadRequestException(
                    "Supplier has no email address; add one before sending PO " + po.getId());
        }

        po.setStatus(POStatus.SENT);
        po.setSentAt(Instant.now());
        poRepository.save(po);

        List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderId(po.getId());

        List<EmailEvent.POLineItemSummary> emailItems = items.stream()
                .map(i -> new EmailEvent.POLineItemSummary(
                        i.getProduct().getName(),
                        i.getVariant() != null ? i.getVariant().getSku() : null,
                        i.getOrderedQty(),
                        i.getUnitCostCents()))
                .collect(Collectors.toList());

        emailService.sendPurchaseOrderEmail(
                supplierEmail,
                po.getSupplier().getName(),
                po.getCompany().getName(),
                po.getId().toString().substring(0, 8).toUpperCase(),
                emailItems,
                po.getExpectedArrivalDate());

        return toResponse(po, items);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse confirmPO(UUID companyId, UUID poId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        PurchaseOrder po = loadPO(companyId, poId);
        if (po.getStatus() != POStatus.SENT) {
            throw new BadRequestException("PO can only be confirmed from SENT status, current: " + po.getStatus());
        }

        po.setStatus(POStatus.CONFIRMED);
        poRepository.save(po);

        List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderId(po.getId());
        return toResponse(po, items);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse receiveItems(UUID companyId, UUID poId, UUID ownerId, ReceiveItemsRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        PurchaseOrder po = loadPO(companyId, poId);
        if (po.getStatus() == POStatus.DRAFT || po.getStatus() == POStatus.RECEIVED || po.getStatus() == POStatus.CANCELLED) {
            throw new BadRequestException("Cannot receive items for PO in status: " + po.getStatus());
        }

        for (ReceiveLineItem receiveLineItem : request.getItems()) {
            PurchaseOrderItem item = itemRepository.findByIdAndPurchaseOrderId(receiveLineItem.getItemId(), po.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("PO item not found: " + receiveLineItem.getItemId()));

            int delta = receiveLineItem.getReceivedQty();
            int newTotal = item.getReceivedQty() + delta;
            if (newTotal > item.getOrderedQty()) {
                throw new BadRequestException(
                        "Cannot receive more than ordered qty for item " + item.getId() +
                        ": ordered=" + item.getOrderedQty() + ", already received=" + item.getReceivedQty() +
                        ", attempting to receive=" + delta);
            }

            // Atomically guard against concurrent over-receipt BEFORE mutating stock.
            // If another receipt raced ahead, this returns 0 and we abort (the whole
            // @Transactional method rolls back, so no stock is credited).
            int updated = itemRepository.incrementReceivedQty(item.getId(), delta);
            if (updated == 0) {
                throw new ConflictException(
                        "Item " + item.getId() + " was concurrently received; please retry");
            }

            applyItemReceipt(po, item, delta, ownerId);
            item.setReceivedQty(newTotal);
        }

        List<PurchaseOrderItem> allItems = itemRepository.findAllByPurchaseOrderId(po.getId());
        boolean allReceived = allItems.stream().allMatch(i -> i.getReceivedQty() >= i.getOrderedQty());

        if (allReceived) {
            po.setStatus(POStatus.RECEIVED);
            po.setReceivedAt(Instant.now());

            if (po.getRestockRequestId() != null) {
                restockRepository.findById(po.getRestockRequestId()).ifPresent(rr -> {
                    rr.setStatus(RestockStatus.RECEIVED);
                    rr.setReceivedAt(Instant.now());
                    restockRepository.save(rr);
                });
            }
        } else {
            po.setStatus(POStatus.PARTIALLY_RECEIVED);
        }

        poRepository.save(po);

        // applyItemReceipt -> adjustStock uses clearAutomatically=true, which detaches `po`
        // (and its lazy associations) mid-transaction. Re-load fresh, managed entities before
        // building the response so toResponse can initialize the supplier proxy.
        PurchaseOrder refreshed = loadPO(companyId, poId);
        List<PurchaseOrderItem> refreshedItems = itemRepository.findAllByPurchaseOrderId(po.getId());
        return toResponse(refreshed, refreshedItems);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancelPO(UUID companyId, UUID poId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        PurchaseOrder po = loadPO(companyId, poId);
        if (po.getStatus() == POStatus.RECEIVED) {
            throw new ConflictException("Cannot cancel a fully received purchase order");
        }
        if (po.getStatus() == POStatus.CANCELLED) {
            List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderId(po.getId());
            return toResponse(po, items);
        }

        po.setStatus(POStatus.CANCELLED);
        poRepository.save(po);

        List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderId(po.getId());
        return toResponse(po, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPO(UUID companyId, UUID poId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);
        PurchaseOrder po = loadPO(companyId, poId);
        List<PurchaseOrderItem> items = itemRepository.findAllByPurchaseOrderId(po.getId());
        return toResponse(po, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PurchaseOrderResponse> listPOs(UUID companyId, UUID ownerId, POStatus status, Pageable pageable) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        Page<PurchaseOrder> page = (status != null)
                ? poRepository.findAllByCompanyIdAndStatus(companyId, status, pageable)
                : poRepository.findAllByCompanyId(companyId, pageable);

        // Batch-load all line items for the page in a single query to avoid N+1.
        List<UUID> poIds = page.getContent().stream().map(PurchaseOrder::getId).collect(Collectors.toList());
        Map<UUID, List<PurchaseOrderItem>> itemsByPo = poIds.isEmpty()
                ? Collections.emptyMap()
                : itemRepository.findAllByPurchaseOrderIdIn(poIds).stream()
                        .collect(Collectors.groupingBy(i -> i.getPurchaseOrder().getId()));

        return new PagedResponse<>(
                page.map(po -> toResponse(po, itemsByPo.getOrDefault(po.getId(), Collections.emptyList()))));
    }

    // ── Stock receipt logic (mirrors RestockServiceImpl.handleReceivedTransition) ──

    private void applyItemReceipt(PurchaseOrder po, PurchaseOrderItem item, int delta, UUID ownerId) {
        UUID productId = item.getProduct().getId();
        UUID variantId = item.getVariant() != null ? item.getVariant().getId() : null;
        UUID locationId = item.getLocation() != null ? item.getLocation().getId() : null;

        String stockLockKey = (variantId != null)
                ? VARIANT_LOCK_PREFIX + variantId
                : PRODUCT_LOCK_PREFIX + productId;

        UUID variantRefForLoc = variantId;
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

            int previousStock;
            if (variantId != null) {
                previousStock = variantRepository.findById(variantId)
                        .map(v -> v.getStock() != null ? v.getStock() : 0).orElse(0);
            } else {
                previousStock = productRepository.findById(productId)
                        .map(p -> p.getStock() != null ? p.getStock() : 0).orElse(0);
            }

            int updated = (variantId != null)
                    ? variantRepository.adjustStock(variantId, delta)
                    : productRepository.adjustStock(productId, delta);

            if (updated == 0) {
                throw new BadRequestException("Stock is not tracked for this product/variant. Set an initial stock value first.");
            }

            if (locationId != null) {
                LocationStock ls = locationStockRepository
                        .findByLocationIdAndProductIdAndVariantRef(locationId, productId, variantRefForLoc)
                        .orElse(null);

                if (ls == null) {
                    ls = new LocationStock();
                    ls.setLocation(locationRepository.getReferenceById(locationId));
                    ls.setProduct(item.getProduct());
                    ls.setVariant(item.getVariant());
                    ls.setVariantRef(variantRefForLoc);
                    ls.setStock(delta);
                    locationStockRepository.save(ls);
                } else {
                    int lsUpdated = locationStockRepository.adjustStock(ls.getId(), delta);
                    if (lsUpdated == 0) {
                        log.warn("Location stock adjust returned 0 for locationStockId={} — skipping", ls.getId());
                    }
                }
            }

            InventoryAdjustment adj = new InventoryAdjustment();
            adj.setProduct(item.getProduct());
            adj.setVariant(item.getVariant());
            adj.setAdjustedBy(userRepository.getReferenceById(ownerId));
            adj.setDelta(delta);
            adj.setPreviousStock(previousStock);
            adj.setNewStock(previousStock + delta);
            adj.setReason(AdjustmentReason.PURCHASE_ORDER_RECEIPT);
            adj.setNote("PO receipt — PO #" + po.getId().toString().substring(0, 8).toUpperCase());
            adj.setPurchaseOrderId(po.getId());
            adjustmentRepository.save(adj);

            if (previousStock == 0) {
                eventPublisher.publishEvent(new StockRestoredEvent(productId, variantId));
            }

            orderService.fulfillPendingBackorders(productId, variantId, delta, locationId);

            if (variantId != null) {
                variantRepository.findById(variantId).ifPresent(v ->
                        stockAlertService.checkAndAlert(
                                productId, item.getProduct().getName(),
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

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private PurchaseOrder loadPO(UUID companyId, UUID poId) {
        return poRepository.findByIdAndCompanyId(poId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + poId));
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po, List<PurchaseOrderItem> items) {
        return new PurchaseOrderResponse(
                po.getId(),
                po.getCompany().getId(),
                po.getSupplier().getId(),
                po.getSupplier().getName(),
                po.getStatus().name(),
                po.getExpectedArrivalDate(),
                po.getNotes(),
                po.getTotalCostCents(),
                po.getRestockRequestId(),
                po.getCreatedBy().getId(),
                po.getCreatedAt(),
                po.getSentAt(),
                po.getReceivedAt(),
                po.getUpdatedAt(),
                items.stream().map(this::toItemResponse).collect(Collectors.toList())
        );
    }

    private PurchaseOrderItemResponse toItemResponse(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getVariant() != null ? item.getVariant().getId() : null,
                item.getVariant() != null ? item.getVariant().getSku() : null,
                item.getOrderedQty(),
                item.getReceivedQty(),
                item.getUnitCostCents(),
                item.getLocation() != null ? item.getLocation().getId() : null,
                item.getLocation() != null ? item.getLocation().getName() : null
        );
    }
}
