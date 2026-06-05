package backend.services.impl.inventory;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.BulkAdjustItem;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.inventory.UpdateInventorySettingsRequest;
import backend.dtos.responses.general.CursorPagedResponse;
import backend.dtos.responses.inventory.InventoryItemResponse;
import backend.events.inventory.StockRestoredEvent;
import backend.exceptions.http.BadRequestException;
import backend.models.core.InventoryAdjustment;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.User;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.ProductStatus;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.repositories.projections.ProductSalesProjection;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID OTHER_PRODUCT_ID = TestIds.uuid(4);
    private static final UUID VARIANT_ID = TestIds.uuid(5);

    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private CompanyAccessService companyAccessService;
    private InventoryAdjustmentRepository adjustmentRepository;
    private UserRepository userRepository;
    private CacheService cacheService;
    private StockAlertService stockAlertService;
    private ApplicationEventPublisher eventPublisher;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        adjustmentRepository = mock(InventoryAdjustmentRepository.class);
        userRepository = mock(UserRepository.class);
        cacheService = mock(CacheService.class);
        stockAlertService = mock(StockAlertService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new InventoryServiceImpl(
                productRepository,
                variantRepository,
                companyAccessService,
                adjustmentRepository,
                userRepository,
                cacheService,
                stockAlertService,
                eventPublisher
        );

        when(adjustmentRepository.save(any(InventoryAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adjustmentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getInventory_rejectsMinStockGreaterThanMaxStock() {
        assertThrows(BadRequestException.class,
                () -> service.getInventory(COMPANY_ID, OWNER_ID, null, null, null, null, null, 10, 5, null, 20));
    }

    @Test
    void getInventory_rejectsInvalidCursor() {
        assertThrows(BadRequestException.class,
                () -> service.getInventory(COMPANY_ID, OWNER_ID, null, null, null, null, null, null, null, "not-base64", 20));
    }

    @Test
    void getInventory_returnsCursorPageAndEncodesNextCursor() {
        Product newest = product(PRODUCT_ID, "Desk", "DESK-1", 8);
        newest.setUpdatedAt(Instant.parse("2026-05-19T12:00:00Z"));
        Product next = product(OTHER_PRODUCT_ID, "Lamp", "LAMP-1", 3);
        next.setUpdatedAt(Instant.parse("2026-05-18T12:00:00Z"));

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newest, next)));

        CursorPagedResponse<InventoryItemResponse> response = service.getInventory(
                COMPANY_ID, OWNER_ID, null, null, null, null, null, null, null, null, 1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(2, pageableCaptor.getValue().getPageSize());
        assertEquals(1, response.items().size());
        assertTrue(response.hasMore());
        assertNotNull(response.nextCursor());

        String decoded = new String(Base64.getDecoder().decode(response.nextCursor()), StandardCharsets.UTF_8);
        assertTrue(decoded.endsWith(":" + newest.getId()));
        assertEquals("IN_STOCK", response.items().get(0).getStockStatus());
    }

    @Test
    void getSummary_calculatesDerivedCounts() {
        when(productRepository.count(any(Specification.class))).thenReturn(12L);
        when(productRepository.countTrackedProducts(COMPANY_ID)).thenReturn(9L);
        when(productRepository.countOutOfStock(COMPANY_ID)).thenReturn(2L);
        when(productRepository.countLowStock(COMPANY_ID)).thenReturn(3L);
        when(productRepository.totalInventoryValue(COMPANY_ID)).thenReturn(new BigDecimal("250.50"));

        var response = service.getSummary(COMPANY_ID, OWNER_ID);

        assertEquals(12L, response.getTotalProducts());
        assertEquals(9L, response.getTrackedProducts());
        assertEquals(4L, response.getInStockCount());
        assertEquals(new BigDecimal("250.50"), response.getTotalInventoryValue());
        verify(companyAccessService).require(eq(COMPANY_ID), eq(OWNER_ID), any());
    }

    @Test
    void adjustStock_successPublishesRestoredEventAndUnlocks() {
        Product before = product(PRODUCT_ID, "Desk", "DESK-1", 0);
        before.setLowStockThreshold(2);
        Product after = product(PRODUCT_ID, "Desk", "DESK-1", 5);
        User user = user(OWNER_ID);

        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(before), Optional.of(after));
        when(productRepository.adjustStock(PRODUCT_ID, 5)).thenReturn(1);
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(before);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user);

        AdjustStockRequest request = new AdjustStockRequest();
        request.setDelta(5);
        request.setReason(AdjustmentReason.RESTOCK);
        request.setNote("supplier delivery");

        InventoryItemResponse response = service.adjustStock(COMPANY_ID, PRODUCT_ID, OWNER_ID, request);

        ArgumentCaptor<InventoryAdjustment> adjustmentCaptor = ArgumentCaptor.forClass(InventoryAdjustment.class);
        verify(adjustmentRepository).save(adjustmentCaptor.capture());
        assertEquals(0, adjustmentCaptor.getValue().getPreviousStock());
        assertEquals(5, adjustmentCaptor.getValue().getNewStock());
        assertEquals(AdjustmentReason.RESTOCK, adjustmentCaptor.getValue().getReason());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        StockRestoredEvent event = assertInstanceOf(StockRestoredEvent.class, eventCaptor.getValue());
        assertEquals(PRODUCT_ID, event.productId());
        assertEquals(null, event.variantId());

        verify(cacheService).unlock(any(), any());
        verify(stockAlertService, never()).checkAndAlert(any(), any(), any(), any(), anyInt(), any());
        assertEquals(5, response.getStock());
    }

    @Test
    void adjustStock_untrackedProductThrowsAndStillUnlocks() {
        Product product = product(PRODUCT_ID, "Desk", "DESK-1", null);

        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        AdjustStockRequest request = new AdjustStockRequest();
        request.setDelta(-1);
        request.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        assertThrows(BadRequestException.class,
                () -> service.adjustStock(COMPANY_ID, PRODUCT_ID, OWNER_ID, request));

        verify(cacheService).unlock(any(), any());
    }

    @Test
    void bulkAdjust_rejectsDuplicateProductIds() {
        BulkAdjustRequest request = new BulkAdjustRequest();
        request.setItems(List.of(
                bulkItem(PRODUCT_ID, 2, AdjustmentReason.RESTOCK, "a"),
                bulkItem(PRODUCT_ID, -1, AdjustmentReason.MANUAL_ADJUSTMENT, "b")
        ));

        assertThrows(BadRequestException.class, () -> service.bulkAdjust(COMPANY_ID, OWNER_ID, request));
    }

    @Test
    void bulkAdjust_successPersistsAdjustmentsAlertsAndUnlocksAllLocks() {
        Product first = product(PRODUCT_ID, "Desk", "DESK-1", 10);
        first.setLowStockThreshold(3);
        Product second = product(OTHER_PRODUCT_ID, "Lamp", "LAMP-1", 2);
        second.setLowStockThreshold(1);

        Product refreshedFirst = product(PRODUCT_ID, "Desk", "DESK-1", 6);
        refreshedFirst.setLowStockThreshold(3);
        Product refreshedSecond = product(OTHER_PRODUCT_ID, "Lamp", "LAMP-1", 7);
        refreshedSecond.setLowStockThreshold(1);

        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true, true);
        when(productRepository.findAllByIdInAndCompanyId(anyCollection(), eq(COMPANY_ID)))
                .thenReturn(List.of(first, second), List.of(refreshedFirst, refreshedSecond));
        when(productRepository.adjustStock(PRODUCT_ID, -4)).thenReturn(1);
        when(productRepository.adjustStock(OTHER_PRODUCT_ID, 5)).thenReturn(1);
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(first);
        when(productRepository.getReferenceById(OTHER_PRODUCT_ID)).thenReturn(second);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user(OWNER_ID));

        BulkAdjustRequest request = new BulkAdjustRequest();
        request.setItems(List.of(
                bulkItem(PRODUCT_ID, -4, AdjustmentReason.MANUAL_ADJUSTMENT, "cycle count"),
                bulkItem(OTHER_PRODUCT_ID, 5, AdjustmentReason.RESTOCK, "received stock")
        ));

        List<InventoryItemResponse> response = service.bulkAdjust(COMPANY_ID, OWNER_ID, request);

        ArgumentCaptor<Iterable<InventoryAdjustment>> adjustmentsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(adjustmentRepository).saveAll(adjustmentsCaptor.capture());
        List<InventoryAdjustment> adjustments = toList(adjustmentsCaptor.getValue());
        assertEquals(2, adjustments.size());
        assertEquals(-4, adjustments.get(0).getDelta());
        assertEquals(5, adjustments.get(1).getDelta());

        verify(stockAlertService).checkAndAlert(
                PRODUCT_ID, "Desk", null, null, 6, 3);
        verify(stockAlertService, never()).checkAndAlert(
                eq(OTHER_PRODUCT_ID), eq("Lamp"), any(), any(), anyInt(), any());
        verify(cacheService, times(2)).unlock(any(), any());
        assertEquals(2, response.size());
        assertEquals("IN_STOCK", response.get(0).getStockStatus());
    }

    @Test
    void updateSettings_requiresPositiveQtyWhenAutoRestockEnabled() {
        Product product = product(PRODUCT_ID, "Desk", "DESK-1", 4);
        product.setAutoRestockEnabled(false);
        product.setAutoRestockQty(null);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        UpdateInventorySettingsRequest request = new UpdateInventorySettingsRequest();
        request.setAutoRestockEnabled(true);

        assertThrows(BadRequestException.class,
                () -> service.updateSettings(COMPANY_ID, PRODUCT_ID, OWNER_ID, request));
    }

    @Test
    void adjustVariantStock_successPublishesVariantRestoredEvent() {
        Product product = product(PRODUCT_ID, "Desk", "DESK-1", 12);
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setProduct(product);
        variant.setSku("DESK-1-BLACK");
        variant.setStock(0);
        variant.setLowStockThreshold(2);
        User user = user(OWNER_ID);

        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(variantRepository.adjustStock(VARIANT_ID, 3)).thenReturn(1);
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(product);
        when(variantRepository.getReferenceById(VARIANT_ID)).thenReturn(variant);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user);

        AdjustStockRequest request = new AdjustStockRequest();
        request.setDelta(3);
        request.setReason(AdjustmentReason.RESTOCK);

        InventoryItemResponse response = service.adjustVariantStock(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, request);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        StockRestoredEvent event = assertInstanceOf(StockRestoredEvent.class, eventCaptor.getValue());
        assertEquals(PRODUCT_ID, event.productId());
        assertEquals(VARIANT_ID, event.variantId());
        verify(cacheService).unlock(any(), any());
        assertEquals(PRODUCT_ID, response.getProductId());
    }

    @Test
    void getTopRevenueProducts_mapsProjectionIncludingStockValue() {
        when(productRepository.findTopByRevenue(COMPANY_ID, 5, null, null))
                .thenReturn(List.of(salesProjection(PRODUCT_ID, "Desk", "DESK-1", 4,
                        new BigDecimal("20.00"), "USD", 9L, new BigDecimal("180.00"))));

        var response = service.getTopRevenueProducts(COMPANY_ID, OWNER_ID, 5, null, null);

        assertEquals(1, response.size());
        assertEquals(new BigDecimal("80.00"), response.get(0).getCurrentStockValue());
        assertEquals(9L, response.get(0).getTotalUnitsSold());
    }

    private Product product(UUID id, String name, String sku, Integer stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSku(sku);
        product.setStock(stock);
        product.setPrice(new BigDecimal("19.99"));
        product.setCurrency("USD");
        product.setStatus(ProductStatus.ACTIVE);
        product.setImages(new ArrayList<>());
        product.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        product.setCreatedAt(Instant.parse("2026-05-18T00:00:00Z"));
        return product;
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@test.com");
        return user;
    }

    private BulkAdjustItem bulkItem(UUID productId, int delta, AdjustmentReason reason, String note) {
        BulkAdjustItem item = new BulkAdjustItem();
        item.setProductId(productId);
        item.setDelta(delta);
        item.setReason(reason);
        item.setNote(note);
        return item;
    }

    private List<InventoryAdjustment> toList(Iterable<InventoryAdjustment> iterable) {
        List<InventoryAdjustment> out = new ArrayList<>();
        for (InventoryAdjustment adj : iterable) {
            out.add(adj);
        }
        return out;
    }

    private ProductSalesProjection salesProjection(
            UUID productId,
            String productName,
            String sku,
            Integer currentStock,
            BigDecimal price,
            String currency,
            Long totalUnitsSold,
            BigDecimal totalRevenue) {
        return new ProductSalesProjection() {
            @Override public UUID getProductId() { return productId; }
            @Override public String getProductName() { return productName; }
            @Override public String getSku() { return sku; }
            @Override public Integer getCurrentStock() { return currentStock; }
            @Override public BigDecimal getPrice() { return price; }
            @Override public String getCurrency() { return currency; }
            @Override public Long getTotalUnitsSold() { return totalUnitsSold; }
            @Override public BigDecimal getTotalRevenue() { return totalRevenue; }
        };
    }

    // ─── Additional tests for uncovered methods ───────────────────────────────

    @Test
    void getInventoryItem_notFound_throwsResourceNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(backend.exceptions.http.ResourceNotFoundException.class, () ->
                service.getInventoryItem(COMPANY_ID, PRODUCT_ID, OWNER_ID));
    }

    @Test
    void getInventoryItem_found_returnsResponse() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(product(PRODUCT_ID, "Desk", "DESK-1", 10)));

        InventoryItemResponse response = service.getInventoryItem(COMPANY_ID, PRODUCT_ID, OWNER_ID);

        assertNotNull(response);
        assertEquals(PRODUCT_ID, response.getProductId());
    }

    @Test
    void getAdjustmentHistory_productNotFound_throwsResourceNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(backend.exceptions.http.ResourceNotFoundException.class, () ->
                service.getAdjustmentHistory(COMPANY_ID, PRODUCT_ID, OWNER_ID, 0, 20));
    }

    @Test
    void getAdjustmentHistory_happyPath_returnsPage() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(product(PRODUCT_ID, "Desk", "DESK-1", 5)));
        when(adjustmentRepository.findAllByProductIdAndProductCompanyId(eq(PRODUCT_ID), eq(COMPANY_ID), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        var result = service.getAdjustmentHistory(COMPANY_ID, PRODUCT_ID, OWNER_ID, 0, 20);

        assertNotNull(result);
    }

    @Test
    void getNeverSoldProducts_returnsResults() {
        var p = salesProjection(PRODUCT_ID, "Desk", "DESK-1", 0, BigDecimal.TEN, "USD", 0L, BigDecimal.ZERO);
        when(productRepository.findNeverSold(eq(COMPANY_ID), eq(10))).thenReturn(List.of(p));

        var result = service.getNeverSoldProducts(COMPANY_ID, OWNER_ID, 10);

        assertEquals(1, result.size());
    }

    @Test
    void getTopPurchasedProducts_returnsResults() {
        var p = salesProjection(PRODUCT_ID, "Desk", "DESK-1", 5, BigDecimal.TEN, "USD", 20L, new BigDecimal("200"));
        when(productRepository.findTopByUnitsSold(eq(COMPANY_ID), eq(5), any(), any())).thenReturn(List.of(p));

        var result = service.getTopPurchasedProducts(COMPANY_ID, OWNER_ID, 5,
                java.time.Instant.now().minusSeconds(86400), java.time.Instant.now());

        assertEquals(1, result.size());
    }

    @Test
    void getTopRevenueProducts_returnsResults() {
        var p = salesProjection(PRODUCT_ID, "Desk", "DESK-1", 5, BigDecimal.TEN, "USD", 10L, new BigDecimal("500"));
        when(productRepository.findTopByRevenue(eq(COMPANY_ID), eq(5), any(), any())).thenReturn(List.of(p));

        var result = service.getTopRevenueProducts(COMPANY_ID, OWNER_ID, 5,
                java.time.Instant.now().minusSeconds(86400), java.time.Instant.now());

        assertEquals(1, result.size());
    }

    @Test
    void adjustStock_lockNotAcquired_throwsConflict() {
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(false);

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(5);
        req.setReason(AdjustmentReason.RESTOCK);

        assertThrows(backend.exceptions.http.ConflictException.class, () ->
                service.adjustStock(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void adjustStock_zeroRowsReturned_throwsBadRequest() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 3);
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));
        when(productRepository.adjustStock(PRODUCT_ID, -10)).thenReturn(0);

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(-10);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        assertThrows(BadRequestException.class, () ->
                service.adjustStock(COMPANY_ID, PRODUCT_ID, OWNER_ID, req));
    }

    @Test
    void adjustStock_negativeDelta_triggersLowStockAlert() {
        Product before = product(PRODUCT_ID, "Desk", "DESK-1", 10);
        before.setLowStockThreshold(3);
        Product after = product(PRODUCT_ID, "Desk", "DESK-1", 2);
        after.setLowStockThreshold(3);

        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(before), Optional.of(after));
        when(productRepository.adjustStock(PRODUCT_ID, -8)).thenReturn(1);
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(before);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user(OWNER_ID));

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(-8);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        service.adjustStock(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        verify(stockAlertService).checkAndAlert(PRODUCT_ID, "Desk", null, null, 2, 3);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void bulkAdjust_lockNotAcquired_throwsConflict() {
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(false);

        BulkAdjustRequest request = new BulkAdjustRequest();
        request.setItems(List.of(bulkItem(PRODUCT_ID, 2, AdjustmentReason.RESTOCK, "note")));

        assertThrows(backend.exceptions.http.ConflictException.class, () ->
                service.bulkAdjust(COMPANY_ID, OWNER_ID, request));
    }

    @Test
    void bulkAdjust_productNotFoundInMap_throwsBadRequest() {
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID))).thenReturn(List.of());

        BulkAdjustRequest request = new BulkAdjustRequest();
        request.setItems(List.of(bulkItem(PRODUCT_ID, 2, AdjustmentReason.RESTOCK, "note")));

        assertThrows(BadRequestException.class, () ->
                service.bulkAdjust(COMPANY_ID, OWNER_ID, request));
    }

    @Test
    void bulkAdjust_negativeResultOnAdjust_throwsBadRequest() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 2);
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findAllByIdInAndCompanyId(any(), eq(COMPANY_ID))).thenReturn(List.of(prod));
        when(productRepository.adjustStock(PRODUCT_ID, -5)).thenReturn(0);
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(prod);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user(OWNER_ID));

        BulkAdjustRequest request = new BulkAdjustRequest();
        request.setItems(List.of(bulkItem(PRODUCT_ID, -5, AdjustmentReason.MANUAL_ADJUSTMENT, "note")));

        assertThrows(BadRequestException.class, () ->
                service.bulkAdjust(COMPANY_ID, OWNER_ID, request));
    }

    @Test
    void updateSettings_happyPath_savesProduct() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 10);
        prod.setAutoRestockEnabled(false);
        prod.setAutoRestockQty(null);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateInventorySettingsRequest req = new UpdateInventorySettingsRequest();
        req.setLowStockThreshold(5);
        req.setMaxStock(100);

        InventoryItemResponse resp = service.updateSettings(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(resp);
        verify(productRepository).save(prod);
    }

    @Test
    void updateSettings_alreadyEnabled_withExistingQty_passes() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 10);
        prod.setAutoRestockEnabled(true);
        prod.setAutoRestockQty(20);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateInventorySettingsRequest req = new UpdateInventorySettingsRequest();
        req.setLowStockThreshold(3);

        InventoryItemResponse resp = service.updateSettings(COMPANY_ID, PRODUCT_ID, OWNER_ID, req);

        assertNotNull(resp);
        verify(productRepository).save(prod);
    }

    @Test
    void adjustVariantStock_lockNotAcquired_throwsConflict() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 12);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(false);

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(3);
        req.setReason(AdjustmentReason.RESTOCK);

        assertThrows(backend.exceptions.http.ConflictException.class, () ->
                service.adjustVariantStock(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req));
    }

    @Test
    void adjustVariantStock_untrackedVariant_throwsBadRequest() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 12);
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setProduct(prod);
        variant.setSku("DESK-1-BLACK");
        variant.setStock(null);

        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(5);
        req.setReason(AdjustmentReason.RESTOCK);

        assertThrows(BadRequestException.class, () ->
                service.adjustVariantStock(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req));
    }

    @Test
    void adjustVariantStock_negativeResult_throwsBadRequest() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 12);
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setProduct(prod);
        variant.setSku("DESK-1-BLACK");
        variant.setStock(2);

        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(variantRepository.adjustStock(VARIANT_ID, -5)).thenReturn(0);

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(-5);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        assertThrows(BadRequestException.class, () ->
                service.adjustVariantStock(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req));
    }

    @Test
    void adjustVariantStock_negativeDelta_triggersAlert() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 12);
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setProduct(prod);
        variant.setSku("DESK-1-BLACK");
        variant.setStock(10);
        variant.setLowStockThreshold(2);

        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(variantRepository.adjustStock(VARIANT_ID, -8)).thenReturn(1);
        when(productRepository.getReferenceById(PRODUCT_ID)).thenReturn(prod);
        when(variantRepository.getReferenceById(VARIANT_ID)).thenReturn(variant);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(user(OWNER_ID));

        AdjustStockRequest req = new AdjustStockRequest();
        req.setDelta(-8);
        req.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);

        service.adjustVariantStock(COMPANY_ID, PRODUCT_ID, VARIANT_ID, OWNER_ID, req);

        verify(stockAlertService).checkAndAlert(PRODUCT_ID, "Desk", VARIANT_ID, "DESK-1-BLACK", 2, 2);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getAdjustmentHistory_returnsPageWithMappedAdjustments() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 5);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));

        InventoryAdjustment adj = new InventoryAdjustment();
        adj.setId(TestIds.uuid(99));
        adj.setProduct(prod);
        adj.setAdjustedBy(user(OWNER_ID));
        adj.setDelta(5);
        adj.setPreviousStock(0);
        adj.setNewStock(5);
        adj.setReason(AdjustmentReason.RESTOCK);
        adj.setNote("restock");
        adj.setCreatedAt(Instant.parse("2026-05-20T00:00:00Z"));

        when(adjustmentRepository.findAllByProductIdAndProductCompanyId(eq(PRODUCT_ID), eq(COMPANY_ID), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(adj)));

        var result = service.getAdjustmentHistory(COMPANY_ID, PRODUCT_ID, OWNER_ID, 0, 20);

        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getDelta());
    }

    @Test
    void getInventoryItem_outOfStock_stockStatusIsOutOfStock() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID))
                .thenReturn(Optional.of(product(PRODUCT_ID, "Desk", "DESK-1", 0)));

        InventoryItemResponse resp = service.getInventoryItem(COMPANY_ID, PRODUCT_ID, OWNER_ID);

        assertEquals("OUT_OF_STOCK", resp.getStockStatus());
    }

    @Test
    void getInventoryItem_percentThresholdLow_stockStatusIsLow() {
        Product prod = product(PRODUCT_ID, "Desk", "DESK-1", 5);
        prod.setMaxStock(100);
        prod.setLowStockThresholdPercent(10);
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(prod));

        InventoryItemResponse resp = service.getInventoryItem(COMPANY_ID, PRODUCT_ID, OWNER_ID);

        assertEquals("LOW_STOCK", resp.getStockStatus());
    }
}
