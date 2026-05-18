package backend.services.impl.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.BulkAdjustItem;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.inventory.UpdateInventorySettingsRequest;
import backend.dtos.responses.general.CursorPagedResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.AdjustmentResponse;
import backend.dtos.responses.inventory.InventoryItemResponse;
import backend.dtos.responses.inventory.InventorySummaryResponse;
import backend.dtos.responses.inventory.ProductSalesMetricResponse;
import backend.repositories.projections.ProductSalesProjection;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.CompanyCapability;
import backend.services.intf.company.CompanyAccessService;
import backend.models.core.Company;
import backend.models.enums.ProductStatus;
import backend.models.core.InventoryAdjustment;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.models.enums.AdjustmentReason;
import backend.repositories.specifications.AdjustmentSpecification;
import backend.repositories.specifications.InventorySpecification;
import backend.events.inventory.StockRestoredEvent;
import backend.services.intf.CacheService;
import backend.services.intf.inventory.InventoryService;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    // Must match the lock key used by OrderServiceImpl to ensure mutual exclusion
    private static final String LOCK_PREFIX = "lock:product:";
    private static final long LOCK_TTL_SECONDS = 10;
    private static final int LOCK_RETRY_ATTEMPTS = 5;
    private static final long LOCK_RETRY_DELAY_MS = 100;

    private static final String VARIANT_LOCK_PREFIX = "lock:variant:";

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CompanyAccessService companyAccessService;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final UserRepository userRepository;
    private final CacheService cacheService;
    private final StockAlertService stockAlertService;
    private final ApplicationEventPublisher eventPublisher;

    public InventoryServiceImpl(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CompanyAccessService companyAccessService,
            InventoryAdjustmentRepository adjustmentRepository,
            UserRepository userRepository,
            CacheService cacheService,
            StockAlertService stockAlertService,
            ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.companyAccessService = companyAccessService;
        this.adjustmentRepository = adjustmentRepository;
        this.userRepository = userRepository;
        this.cacheService = cacheService;
        this.stockAlertService = stockAlertService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CursorPagedResponse<InventoryItemResponse> getInventory(
            UUID companyId, UUID ownerId,
            String stockStatus, String q,
            String category, String brand,
            ProductStatus status, Integer minStock, Integer maxStock,
            String cursor, int size) {

        assertCompanyOwnership(companyId, ownerId);

        if (minStock != null && maxStock != null && minStock > maxStock) {
            throw new BadRequestException("minStock cannot be greater than maxStock");
        }

        if (size > 50) size = 50;

        Instant cursorUpdatedAt = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(cursor);
                String[] parts = new String(decoded, StandardCharsets.UTF_8).split(":", 2);
                cursorUpdatedAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
                cursorId = UUID.fromString(parts[1]);
            } catch (Exception e) {
                throw new BadRequestException("Invalid cursor");
            }
        }

        // Always sort updatedAt DESC, id DESC for stable keyset pagination.
        // Fetch one extra to detect whether another page exists.
        Pageable pageable = PageRequest.of(0, size + 1,
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));

        List<Product> results = productRepository.findAll(
                InventorySpecification.withFilters(
                        companyId, stockStatus, q, category, brand, status, minStock, maxStock,
                        cursorUpdatedAt, cursorId),
                pageable).getContent();

        boolean hasMore = results.size() > size;
        List<Product> page = hasMore ? results.subList(0, size) : results;

        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;

        List<InventoryItemResponse> items = page.stream()
                .map(this::toInventoryItemResponse)
                .toList();

        return new CursorPagedResponse<>(items, nextCursor, hasMore, items.size());
    }

    @Override
    public InventorySummaryResponse getSummary(UUID companyId, UUID ownerId) {
        assertCompanyOwnership(companyId, ownerId);

        long totalProducts = productRepository.count(
                InventorySpecification.withFilters(companyId, null, null, null, null, null, null, null));
        long trackedProducts = productRepository.countTrackedProducts(companyId);
        long outOfStockCount = productRepository.countOutOfStock(companyId);
        long lowStockCount = productRepository.countLowStock(companyId);
        long inStockCount = trackedProducts - outOfStockCount - lowStockCount;
        BigDecimal totalInventoryValue = productRepository.totalInventoryValue(companyId);

        return new InventorySummaryResponse(
                totalProducts,
                trackedProducts,
                inStockCount,
                lowStockCount,
                outOfStockCount,
                totalInventoryValue
        );
    }

    @Override
    public InventoryItemResponse getInventoryItem(UUID companyId, UUID productId, UUID ownerId) {
        assertCompanyOwnership(companyId, ownerId);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        return toInventoryItemResponse(product);
    }

    @Override
    public PagedResponse<AdjustmentResponse> getAdjustmentHistory(
            UUID companyId, UUID productId, UUID ownerId, int page, int size) {

        assertCompanyOwnership(companyId, ownerId);

        productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return new PagedResponse<>(
                adjustmentRepository
                        .findAllByProductIdAndProductCompanyId(productId, companyId, pageable)
                        .map(this::toAdjustmentResponse)
        );
    }

    @Override
    @Transactional
    public InventoryItemResponse adjustStock(
            UUID companyId, UUID productId, UUID ownerId, AdjustStockRequest request) {

        assertCompanyOwnership(companyId, ownerId);

        String lockToken = UUID.randomUUID().toString();
        String lockKey = LOCK_PREFIX + productId;
        boolean lockAcquired = false;

        try {
            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(lockKey, lockToken, LOCK_TTL_SECONDS)) {
                    lockAcquired = true;
                    break;
                }
                try {
                    Thread.sleep(LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Stock adjustment interrupted, please try again");
                }
            }

            if (!lockAcquired) {
                throw new ConflictException("Product is currently being updated by another operation, please try again shortly");
            }

            Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

            if (product.getStock() == null) {
                throw new BadRequestException("Stock tracking is not enabled for this product. Set an initial stock value first.");
            }

            int previousStock = product.getStock();
            int delta = request.getDelta();

            int rows = productRepository.adjustStock(productId, delta);
            if (rows == 0) {
                throw new BadRequestException(
                        "Adjustment would result in negative stock. Current stock: " + previousStock + ", delta: " + delta);
            }

            InventoryAdjustment adjustment = new InventoryAdjustment();
            adjustment.setProduct(productRepository.getReferenceById(productId));
            adjustment.setAdjustedBy(userRepository.getReferenceById(ownerId));
            adjustment.setDelta(delta);
            adjustment.setPreviousStock(previousStock);
            adjustment.setNewStock(previousStock + delta);
            adjustment.setReason(request.getReason());
            adjustment.setNote(request.getNote());
            adjustmentRepository.save(adjustment);

            if (delta < 0) {
                stockAlertService.checkAndAlert(
                        productId, product.getName(), null, null,
                        previousStock + delta, product.getLowStockThreshold());
            }

            if (previousStock == 0 && delta > 0) {
                eventPublisher.publishEvent(new StockRestoredEvent(product.getId(), null));
            }

            product = productRepository.findByIdAndCompanyId(productId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

            return toInventoryItemResponse(product);

        } finally {
            if (lockAcquired) {
                try {
                    cacheService.unlock(lockKey, lockToken);
                } catch (Exception e) {
                    log.error("Failed to release inventory lock for product {}: {}", productId, e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional
    public List<InventoryItemResponse> bulkAdjust(UUID companyId, UUID ownerId, BulkAdjustRequest request) {
        assertCompanyOwnership(companyId, ownerId);

        List<BulkAdjustItem> items = request.getItems();

        // Validate no duplicate productIds in the request
        Set<UUID> seen = new HashSet<>();
        List<UUID> duplicates = new ArrayList<>();
        for (BulkAdjustItem item : items) {
            if (!seen.add(item.getProductId())) {
                duplicates.add(item.getProductId());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BadRequestException("Duplicate productIds in request: " + duplicates);
        }

        // Sort product IDs ascending — consistent lock ordering prevents deadlocks
        List<UUID> sortedProductIds = items.stream()
                .map(BulkAdjustItem::getProductId)
                .sorted()
                .collect(Collectors.toList());

        String lockToken = UUID.randomUUID().toString();
        List<String> acquiredLocks = new ArrayList<>();

        try {
            acquireLocks(sortedProductIds, lockToken, acquiredLocks);

            // Pass 1 — load all products into a map, validate before any writes
            List<Product> loaded = productRepository.findAllByIdInAndCompanyId(sortedProductIds, companyId);
            Map<UUID, Product> productMap = new HashMap<>();
            for (Product p : loaded) {
                productMap.put(p.getId(), p);
            }

            List<String> errors = new ArrayList<>();
            for (BulkAdjustItem item : items) {
                Product product = productMap.get(item.getProductId());
                if (product == null) {
                    errors.add("Product " + item.getProductId() + " not found in this company");
                } else if (product.getStock() == null) {
                    errors.add("Product " + item.getProductId() + " does not have stock tracking enabled");
                }
            }

            if (!errors.isEmpty()) {
                throw new BadRequestException("Bulk adjustment validation failed: " + String.join("; ", errors));
            }

            // Pass 2 — apply all adjustments; any failure rolls back the entire transaction
            List<InventoryAdjustment> adjustments = new ArrayList<>();
            for (BulkAdjustItem item : items) {
                Product product = productMap.get(item.getProductId());
                int previousStock = product.getStock();
                int delta = item.getDelta();

                int rows = productRepository.adjustStock(item.getProductId(), delta);
                if (rows == 0) {
                    throw new BadRequestException(
                            "Adjustment for product " + item.getProductId() +
                            " would result in negative stock. Current stock: " + previousStock + ", delta: " + delta);
                }

                InventoryAdjustment adjustment = new InventoryAdjustment();
                adjustment.setProduct(productRepository.getReferenceById(item.getProductId()));
                adjustment.setAdjustedBy(userRepository.getReferenceById(ownerId));
                adjustment.setDelta(delta);
                adjustment.setPreviousStock(previousStock);
                adjustment.setNewStock(previousStock + delta);
                adjustment.setReason(item.getReason());
                adjustment.setNote(item.getNote());
                adjustments.add(adjustment);
            }

            adjustmentRepository.saveAll(adjustments);

            // Re-fetch after @Modifying to get fresh stock values
            List<Product> refreshed = productRepository.findAllByIdInAndCompanyId(sortedProductIds, companyId);

            // Low stock alerts using fresh (post-adjustment) stock values
            for (Product p : refreshed) {
                BulkAdjustItem matchingItem = items.stream()
                        .filter(i -> i.getProductId().equals(p.getId()))
                        .findFirst().orElse(null);
                if (matchingItem != null && matchingItem.getDelta() < 0) {
                    stockAlertService.checkAndAlert(
                            p.getId(), p.getName(), null, null,
                            p.getStock() != null ? p.getStock() : 0,
                            p.getLowStockThreshold());
                }
            }

            return refreshed.stream()
                    .map(this::toInventoryItemResponse)
                    .toList();

        } finally {
            releaseLocks(acquiredLocks, lockToken);
        }
    }

    @Override
    @Transactional
    public InventoryItemResponse updateSettings(
            UUID companyId, UUID productId, UUID ownerId, UpdateInventorySettingsRequest request) {

        assertCompanyOwnership(companyId, ownerId);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (request.getLowStockThreshold() != null) product.setLowStockThreshold(request.getLowStockThreshold());
        if (request.getLowStockThresholdPercent() != null) product.setLowStockThresholdPercent(request.getLowStockThresholdPercent());
        if (request.getMaxStock() != null) product.setMaxStock(request.getMaxStock());
        if (request.getAutoRestockEnabled() != null) product.setAutoRestockEnabled(request.getAutoRestockEnabled());
        if (request.getAutoRestockQty() != null) product.setAutoRestockQty(request.getAutoRestockQty());

        boolean willBeEnabled = Boolean.TRUE.equals(request.getAutoRestockEnabled())
                || (request.getAutoRestockEnabled() == null && product.isAutoRestockEnabled());
        Integer effectiveQty = request.getAutoRestockQty() != null ? request.getAutoRestockQty() : product.getAutoRestockQty();
        if (willBeEnabled && (effectiveQty == null || effectiveQty < 1)) {
            throw new BadRequestException("autoRestockQty must be set to a positive value when autoRestockEnabled is true");
        }

        productRepository.save(product);

        return toInventoryItemResponse(product);
    }

    @Override
    public List<ProductSalesMetricResponse> getTopPurchasedProducts(UUID companyId, UUID ownerId, int limit, Instant from, Instant to) {
        assertCompanyOwnership(companyId, ownerId);
        return productRepository.findTopByUnitsSold(companyId, limit, from, to)
                .stream()
                .map(this::toSalesMetricResponse)
                .toList();
    }

    @Override
    public List<ProductSalesMetricResponse> getTopRevenueProducts(UUID companyId, UUID ownerId, int limit, Instant from, Instant to) {
        assertCompanyOwnership(companyId, ownerId);
        return productRepository.findTopByRevenue(companyId, limit, from, to)
                .stream()
                .map(this::toSalesMetricResponse)
                .toList();
    }

    @Override
    public List<ProductSalesMetricResponse> getNeverSoldProducts(UUID companyId, UUID ownerId, int limit) {
        assertCompanyOwnership(companyId, ownerId);
        return productRepository.findNeverSold(companyId, limit)
                .stream()
                .map(this::toSalesMetricResponse)
                .toList();
    }

    // --- Cursor helpers ---

    private static String encodeCursor(Product p) {
        long ts = p.getUpdatedAt() != null
                ? p.getUpdatedAt().toEpochMilli()
                : (p.getCreatedAt() != null ? p.getCreatedAt().toEpochMilli() : 0L);
        String raw = ts + ":" + p.getId();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // --- Lock helpers (mirrors OrderServiceImpl — same lock namespace) ---

    private void acquireLocks(List<UUID> sortedProductIds, String lockToken, List<String> acquiredLocks) {
        for (UUID productId : sortedProductIds) {
            String lockKey = LOCK_PREFIX + productId;
            boolean acquired = false;

            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(lockKey, lockToken, LOCK_TTL_SECONDS)) {
                    acquiredLocks.add(lockKey);
                    acquired = true;
                    break;
                }
                try {
                    Thread.sleep(LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    releaseLocks(acquiredLocks, lockToken);
                    throw new ConflictException("Bulk adjustment interrupted, please try again");
                }
            }

            if (!acquired) {
                releaseLocks(acquiredLocks, lockToken);
                throw new ConflictException(
                        "Product " + productId + " is currently being updated by another operation, please try again shortly");
            }
        }
    }

    private void releaseLocks(List<String> lockKeys, String lockToken) {
        for (String lockKey : lockKeys) {
            try {
                cacheService.unlock(lockKey, lockToken);
            } catch (Exception e) {
                log.error("Failed to release inventory lock {}: {}", lockKey, e.getMessage());
            }
        }
    }

    // --- Private mapping helpers ---

    private Company assertCompanyOwnership(UUID companyId, UUID ownerId) {
        return companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);
    }

    private InventoryItemResponse toInventoryItemResponse(Product product) {
        Integer stock = product.getStock();
        Integer threshold = product.getLowStockThreshold();
        Integer thresholdPercent = product.getLowStockThresholdPercent();
        Integer maxStock = product.getMaxStock();

        boolean untracked = stock == null;
        boolean outOfStock = !untracked && stock == 0;

        boolean quantityLow = false;
        boolean percentLow = false;
        if (!untracked && !outOfStock && stock != null) {
            int s = stock;
            if (threshold != null && s <= threshold) quantityLow = true;
            if (thresholdPercent != null && maxStock != null && maxStock > 0) {
                percentLow = (s * 100.0 / maxStock) <= thresholdPercent;
            }
        }
        boolean lowStock = quantityLow || percentLow;

        String stockStatus;
        if (untracked)       stockStatus = "UNTRACKED";
        else if (outOfStock) stockStatus = "OUT_OF_STOCK";
        else if (lowStock)   stockStatus = "LOW_STOCK";
        else                 stockStatus = "IN_STOCK";

        return new InventoryItemResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                stock,
                threshold,
                thresholdPercent,
                maxStock,
                product.isAutoRestockEnabled(),
                product.getAutoRestockQty(),
                lowStock,
                outOfStock,
                stockStatus,
                product.getPrice(),
                product.getCurrency(),
                product.getUpdatedAt()
        );
    }

    private ProductSalesMetricResponse toSalesMetricResponse(ProductSalesProjection p) {
        BigDecimal stockValue = null;
        if (p.getCurrentStock() != null && p.getPrice() != null) {
            stockValue = p.getPrice().multiply(BigDecimal.valueOf(p.getCurrentStock()));
        }
        return new ProductSalesMetricResponse(
                p.getProductId(),
                p.getProductName(),
                p.getSku(),
                p.getCurrentStock(),
                p.getPrice(),
                p.getCurrency(),
                p.getTotalUnitsSold() != null ? p.getTotalUnitsSold() : 0L,
                p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO,
                stockValue
        );
    }

    private AdjustmentResponse toAdjustmentResponse(InventoryAdjustment adj) {
        return new AdjustmentResponse(
                adj.getId(),
                adj.getProduct().getId(),
                adj.getProduct().getName(),
                adj.getVariant() != null ? adj.getVariant().getId() : null,
                adj.getOrderId(),
                adj.getAdjustedBy() != null ? adj.getAdjustedBy().getId() : null,
                adj.getDelta(),
                adj.getPreviousStock(),
                adj.getNewStock(),
                adj.getReason().name(),
                adj.getNote(),
                adj.getCreatedAt()
        );
    }

    @Override
    public PagedResponse<AdjustmentResponse> getCompanyAdjustmentHistory(
            UUID companyId, UUID ownerId, AdjustmentReason reason,
            Instant from, Instant to, UUID productId, UUID userId, int page, int size) {

        assertCompanyOwnership(companyId, ownerId);

        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return new PagedResponse<>(
                adjustmentRepository.findAll(
                        AdjustmentSpecification.withFilters(companyId, reason, from, to, productId, userId),
                        pageable
                ).map(this::toAdjustmentResponse)
        );
    }

    @Override
    @Transactional
    public InventoryItemResponse adjustVariantStock(
            UUID companyId, UUID productId, UUID variantId, UUID ownerId, AdjustStockRequest request) {

        assertCompanyOwnership(companyId, ownerId);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        String lockToken = UUID.randomUUID().toString();
        String lockKey = VARIANT_LOCK_PREFIX + variantId;
        boolean lockAcquired = false;

        try {
            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(lockKey, lockToken, LOCK_TTL_SECONDS)) {
                    lockAcquired = true;
                    break;
                }
                try {
                    Thread.sleep(LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Stock adjustment interrupted, please try again");
                }
            }

            if (!lockAcquired) {
                throw new ConflictException("Variant is currently being updated by another operation, please try again shortly");
            }

            ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));

            if (variant.getStock() == null) {
                throw new BadRequestException("Stock tracking is not enabled for this variant. Set an initial stock value first.");
            }

            int previousStock = variant.getStock();
            int delta = request.getDelta();

            int rows = variantRepository.adjustStock(variantId, delta);
            if (rows == 0) {
                throw new BadRequestException(
                        "Adjustment would result in negative stock. Current stock: " + previousStock + ", delta: " + delta);
            }

            InventoryAdjustment adjustment = new InventoryAdjustment();
            adjustment.setProduct(productRepository.getReferenceById(productId));
            adjustment.setVariant(variantRepository.getReferenceById(variantId));
            adjustment.setAdjustedBy(userRepository.getReferenceById(ownerId));
            adjustment.setDelta(delta);
            adjustment.setPreviousStock(previousStock);
            adjustment.setNewStock(previousStock + delta);
            adjustment.setReason(request.getReason());
            adjustment.setNote(request.getNote());
            adjustmentRepository.save(adjustment);

            if (delta < 0) {
                stockAlertService.checkAndAlert(
                        productId, product.getName(),
                        variantId, variant.getSku(),
                        previousStock + delta, variant.getLowStockThreshold());
            }

            if (previousStock == 0 && delta > 0) {
                eventPublisher.publishEvent(new StockRestoredEvent(product.getId(), variant.getId()));
            }

            return toInventoryItemResponse(product);

        } finally {
            if (lockAcquired) {
                try {
                    cacheService.unlock(lockKey, lockToken);
                } catch (Exception e) {
                    log.error("Failed to release variant lock for variant {}: {}", variantId, e.getMessage());
                }
            }
        }
    }
}
