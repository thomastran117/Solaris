package backend.services.impl.products;

import backend.dtos.requests.product.BatchDeleteBundlesRequest;
import backend.dtos.requests.product.BatchUpdateBundlesRequest;
import backend.dtos.requests.product.BundleItemRequest;
import backend.dtos.requests.product.CreateBundleRequest;
import backend.dtos.requests.product.UpdateBundleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.BundleResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.ProductVariant;
import backend.models.enums.CompanyCapability;
import backend.models.enums.ProductStatus;
import backend.repositories.BundleRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BundleServiceImplTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID OWNER_ID    = TestIds.uuid(2);
    private static final UUID BUNDLE_ID   = TestIds.uuid(3);
    private static final UUID PRODUCT_ID  = TestIds.uuid(4);
    private static final UUID VARIANT_ID  = TestIds.uuid(5);

    private BundleRepository bundleRepository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private OrderRepository orderRepository;
    private CompanyAccessService companyAccessService;
    private ApplicationEventPublisher eventPublisher;
    private SingleFlightCache singleFlightCache;

    private BundleServiceImpl service;

    @BeforeEach
    void setUp() {
        bundleRepository     = mock(BundleRepository.class);
        productRepository    = mock(ProductRepository.class);
        variantRepository    = mock(ProductVariantRepository.class);
        orderRepository      = mock(OrderRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        eventPublisher       = mock(ApplicationEventPublisher.class);
        singleFlightCache    = mock(SingleFlightCache.class);

        service = new BundleServiceImpl(
                bundleRepository, productRepository, variantRepository, orderRepository,
                companyAccessService, eventPublisher, singleFlightCache, 300L);

        // Common stubs
        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any(CompanyCapability.class)))
                .thenReturn(makeCompany());
        when(bundleRepository.save(any(ProductBundle.class))).thenAnswer(inv -> {
            ProductBundle b = inv.getArgument(0);
            if (b.getId() == null) b.setId(BUNDLE_ID);
            return b;
        });

        // Bypass cache
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(Class.class));
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── createBundle ─────────────────────────────────────────────────────────

    @Test
    void createBundle_autoPriceFromComponents_setsComponentTotal() {
        Product product = makeProduct(PRODUCT_ID, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        CreateBundleRequest req = makeCreateRequest(null, List.of(item(PRODUCT_ID, null, 2)));

        BundleResponse result = service.createBundle(COMPANY_ID, OWNER_ID, req);

        // 2 × $10.00 = $20.00
        assertEquals(new BigDecimal("20.00"), result.getPrice());
    }

    @Test
    void createBundle_explicitPriceUnderTotal_succeeds() {
        Product product = makeProduct(PRODUCT_ID, new BigDecimal("20.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        CreateBundleRequest req = makeCreateRequest(new BigDecimal("15.00"), List.of(item(PRODUCT_ID, null, 1)));

        BundleResponse result = service.createBundle(COMPANY_ID, OWNER_ID, req);

        assertEquals(new BigDecimal("15.00"), result.getPrice());
    }

    @Test
    void createBundle_explicitPriceExceedsComponentTotal_throwsBadRequest() {
        Product product = makeProduct(PRODUCT_ID, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        CreateBundleRequest req = makeCreateRequest(new BigDecimal("99.00"), List.of(item(PRODUCT_ID, null, 1)));

        assertThrows(BadRequestException.class,
                () -> service.createBundle(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createBundle_negativePriceExplicit_throwsBadRequest() {
        Product product = makeProduct(PRODUCT_ID, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        CreateBundleRequest req = makeCreateRequest(new BigDecimal("-1.00"), List.of(item(PRODUCT_ID, null, 1)));

        assertThrows(BadRequestException.class,
                () -> service.createBundle(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createBundle_duplicateProductVariantCombo_throwsBadRequest() {
        CreateBundleRequest req = makeCreateRequest(null,
                List.of(item(PRODUCT_ID, null, 1), item(PRODUCT_ID, null, 1)));

        assertThrows(BadRequestException.class,
                () -> service.createBundle(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createBundle_moreThan10Items_throwsBadRequest() {
        // 11 distinct products to pass duplicate check but still exceed limit
        List<BundleItemRequest> items = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Product p = makeProduct(TestIds.uuid(100 + i), new BigDecimal("5.00"));
            UUID pid = TestIds.uuid(100 + i);
            when(productRepository.findByIdAndCompanyId(pid, COMPANY_ID)).thenReturn(Optional.of(p));
            items.add(item(pid, null, 1));
        }

        CreateBundleRequest req = makeCreateRequest(null, items);

        assertThrows(BadRequestException.class,
                () -> service.createBundle(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createBundle_productNotFound_throwsResourceNotFound() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        CreateBundleRequest req = makeCreateRequest(null, List.of(item(PRODUCT_ID, null, 1)));

        assertThrows(ResourceNotFoundException.class,
                () -> service.createBundle(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createBundle_variantNotFound_throwsResourceNotFound() {
        Product product = makeProduct(PRODUCT_ID, new BigDecimal("10.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        CreateBundleRequest req = makeCreateRequest(null, List.of(item(PRODUCT_ID, VARIANT_ID, 1)));

        assertThrows(ResourceNotFoundException.class,
                () -> service.createBundle(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void createBundle_nullCurrency_defaultsToUsd() {
        Product product = makeProduct(PRODUCT_ID, new BigDecimal("5.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        CreateBundleRequest req = makeCreateRequest(null, List.of(item(PRODUCT_ID, null, 1)));
        req.setCurrency(null);

        BundleResponse result = service.createBundle(COMPANY_ID, OWNER_ID, req);

        assertEquals("USD", result.getCurrency());
    }

    @Test
    void createBundle_withVariant_usesVariantPrice() {
        Product product = makeProduct(PRODUCT_ID, new BigDecimal("20.00"));
        ProductVariant variant = makeVariant(VARIANT_ID, new BigDecimal("12.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));

        CreateBundleRequest req = makeCreateRequest(null, List.of(item(PRODUCT_ID, VARIANT_ID, 1)));

        BundleResponse result = service.createBundle(COMPANY_ID, OWNER_ID, req);

        // should use variant price ($12), not product price ($20)
        assertEquals(new BigDecimal("12.00"), result.getPrice());
    }

    @Test
    void createBundle_withoutVariant_usesProductPrice() {
        Product product = makeProduct(PRODUCT_ID, new BigDecimal("30.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(product));

        CreateBundleRequest req = makeCreateRequest(null, List.of(item(PRODUCT_ID, null, 1)));

        BundleResponse result = service.createBundle(COMPANY_ID, OWNER_ID, req);

        assertEquals(new BigDecimal("30.00"), result.getPrice());
    }

    @Test
    void createBundle_multipleItemsWithQuantity_sumsCorrectly() {
        Product p1 = makeProduct(PRODUCT_ID, new BigDecimal("10.00"));
        UUID pid2 = TestIds.uuid(20);
        Product p2 = makeProduct(pid2, new BigDecimal("5.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(p1));
        when(productRepository.findByIdAndCompanyId(pid2, COMPANY_ID)).thenReturn(Optional.of(p2));

        CreateBundleRequest req = makeCreateRequest(null,
                List.of(item(PRODUCT_ID, null, 2), item(pid2, null, 3)));

        BundleResponse result = service.createBundle(COMPANY_ID, OWNER_ID, req);

        // 2×$10 + 3×$5 = $35
        assertEquals(new BigDecimal("35.00"), result.getPrice());
    }

    // ─── updateBundle ─────────────────────────────────────────────────────────

    @Test
    void updateBundle_nameOnly_updatesName() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        UpdateBundleRequest req = new UpdateBundleRequest();
        req.setName("New Name");

        BundleResponse result = service.updateBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID, req);

        assertEquals("New Name", result.getName());
    }

    @Test
    void updateBundle_priceOnly_valid_updates() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        Product bundleProduct = makeProduct(PRODUCT_ID, new BigDecimal("20.00"));
        bundle.getItems().get(0).setProduct(bundleProduct);
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        UpdateBundleRequest req = new UpdateBundleRequest();
        req.setPrice(new BigDecimal("15.00")); // under component total of $20

        BundleResponse result = service.updateBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID, req);

        assertEquals(new BigDecimal("15.00"), result.getPrice());
    }

    @Test
    void updateBundle_priceOnly_exceedsComponents_throwsBadRequest() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        Product bundleProduct = makeProduct(PRODUCT_ID, new BigDecimal("20.00"));
        bundle.getItems().get(0).setProduct(bundleProduct);
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        UpdateBundleRequest req = new UpdateBundleRequest();
        req.setPrice(new BigDecimal("999.00"));

        assertThrows(BadRequestException.class,
                () -> service.updateBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID, req));
    }

    @Test
    void updateBundle_items_emptyList_throwsBadRequest() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        UpdateBundleRequest req = new UpdateBundleRequest();
        req.setItems(List.of()); // empty

        assertThrows(BadRequestException.class,
                () -> service.updateBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID, req));
    }

    @Test
    void updateBundle_items_duplicateCombo_throwsBadRequest() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        UpdateBundleRequest req = new UpdateBundleRequest();
        req.setItems(List.of(item(PRODUCT_ID, null, 1), item(PRODUCT_ID, null, 1)));

        assertThrows(BadRequestException.class,
                () -> service.updateBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID, req));
    }

    @Test
    void updateBundle_notFound_throwsResourceNotFound() {
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID, new UpdateBundleRequest()));
    }

    @Test
    void updateBundle_noChangesButComponentPriceDrifted_capsStoredPriceToCurrentTotal() {
        // M14: if a component product's price was lowered after the bundle was created,
        // the stored bundle price may now exceed the new component total. An update with
        // neither items nor price should silently cap it to the new total.
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("25.00"));
        // Component is now only worth $20 (price drifted down from original $30)
        bundle.getItems().get(0).getProduct().setPrice(new BigDecimal("10.00")); // component total = $20
        bundle.getItems().get(0).setQuantity(2); // 2 × $10 = $20, but bundle.price = $25 > $20

        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        service.updateBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID, new UpdateBundleRequest());

        // Price should be capped down to the new component total
        assertTrue(bundle.getPrice().compareTo(new BigDecimal("20.00")) <= 0);
        verify(bundleRepository).save(bundle);
    }

    // ─── deleteBundle ─────────────────────────────────────────────────────────

    @Test
    void deleteBundle_happyPath_deletesAndPublishesRemoveEvent() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        service.deleteBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID);

        verify(bundleRepository).delete(bundle);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void deleteBundle_notFound_throwsResourceNotFound() {
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID));
    }

    @Test
    void deleteBundle_activeOrderExists_throwsConflict() {
        // H15: deletion must be blocked while active orders reference the bundle.
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));
        when(orderRepository.existsActiveOrderWithBundle(BUNDLE_ID)).thenReturn(true);

        assertThrows(backend.exceptions.http.ConflictException.class,
                () -> service.deleteBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID));
        verify(bundleRepository, never()).delete(any());
    }

    @Test
    void deleteBundle_noActiveOrders_proceeds() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));
        when(orderRepository.existsActiveOrderWithBundle(BUNDLE_ID)).thenReturn(false);

        service.deleteBundle(COMPANY_ID, BUNDLE_ID, OWNER_ID);

        verify(bundleRepository).delete(bundle);
    }

    // ─── getBundle (public) ───────────────────────────────────────────────────

    @Test
    void getBundle_activeBundle_returnsResponse() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        bundle.setStatus(ProductStatus.ACTIVE);
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        BundleResponse result = service.getBundle(COMPANY_ID, BUNDLE_ID);

        assertNotNull(result);
        assertEquals("Test Bundle", result.getName());
    }

    @Test
    void getBundle_draftBundle_throwsResourceNotFound() {
        ProductBundle bundle = makeBundle(BUNDLE_ID, new BigDecimal("20.00"));
        bundle.setStatus(ProductStatus.DRAFT);
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.of(bundle));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getBundle(COMPANY_ID, BUNDLE_ID));
    }

    @Test
    void getBundle_notFound_throwsResourceNotFound() {
        when(bundleRepository.findByIdAndCompanyId(BUNDLE_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getBundle(COMPANY_ID, BUNDLE_ID));
    }

    // ─── listBundles (public) ─────────────────────────────────────────────────

    @Test
    void listBundles_alwaysRestrictsToActive() {
        when(bundleRepository.findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // caller requests DRAFT — public endpoint should still only return ACTIVE
        service.listBundles(COMPANY_ID, ProductStatus.DRAFT, 0, 20);

        verify(bundleRepository).findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class));
    }

    @Test
    void listBundles_clampsPageSizeTo50() {
        when(bundleRepository.findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listBundles(COMPANY_ID, null, 0, 200);

        verify(bundleRepository).findAllByCompanyIdAndStatus(eq(COMPANY_ID), eq(ProductStatus.ACTIVE),
                argThat(p -> p.getPageSize() == 50));
    }

    // ─── compareBundles ───────────────────────────────────────────────────────

    @Test
    void compareBundles_twoBundles_returnsResponses() {
        UUID bid1 = TestIds.uuid(10);
        UUID bid2 = TestIds.uuid(11);
        ProductBundle b1 = makeBundle(bid1, new BigDecimal("10.00"));
        b1.setStatus(ProductStatus.ACTIVE);
        ProductBundle b2 = makeBundle(bid2, new BigDecimal("20.00"));
        b2.setStatus(ProductStatus.ACTIVE);
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(b1, b2));

        List<BundleResponse> result = service.compareBundles(COMPANY_ID, List.of(bid1, bid2));

        assertEquals(2, result.size());
    }

    @Test
    void compareBundles_oneId_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.compareBundles(COMPANY_ID, List.of(TestIds.uuid(10))));
    }

    @Test
    void compareBundles_fiveIds_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.compareBundles(COMPANY_ID, List.of(
                        TestIds.uuid(10), TestIds.uuid(11), TestIds.uuid(12),
                        TestIds.uuid(13), TestIds.uuid(14))));
    }

    @Test
    void compareBundles_noBundlesFound_throwsResourceNotFound() {
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.compareBundles(COMPANY_ID, List.of(TestIds.uuid(10), TestIds.uuid(11))));
    }

    @Test
    void compareBundles_fourBundles_succeeds() {
        List<UUID> ids = List.of(TestIds.uuid(10), TestIds.uuid(11), TestIds.uuid(12), TestIds.uuid(13));
        List<ProductBundle> bundles = ids.stream().map(id -> {
            ProductBundle b = makeBundle(id, new BigDecimal("10.00"));
            b.setStatus(ProductStatus.ACTIVE);
            return b;
        }).toList();
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID))).thenReturn(bundles);

        List<BundleResponse> result = service.compareBundles(COMPANY_ID, ids);

        assertEquals(4, result.size());
    }

    // ─── batchUpdateBundles ───────────────────────────────────────────────────

    @Test
    void batchUpdateBundles_validRequest_updatesAll() {
        UUID bid1 = TestIds.uuid(10);
        UUID bid2 = TestIds.uuid(11);
        ProductBundle b1 = makeBundle(bid1, new BigDecimal("20.00"));
        ProductBundle b2 = makeBundle(bid2, new BigDecimal("30.00"));
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(b1, b2));

        BatchUpdateBundlesRequest req = new BatchUpdateBundlesRequest();
        req.setIds(List.of(bid1, bid2));
        req.setStatus(ProductStatus.INACTIVE);

        List<BundleResponse> results = service.batchUpdateBundles(COMPANY_ID, OWNER_ID, req);

        assertEquals(2, results.size());
        verify(bundleRepository, times(2)).save(any(ProductBundle.class));
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void batchUpdateBundles_scheduledStatus_throwsBadRequest() {
        BatchUpdateBundlesRequest req = new BatchUpdateBundlesRequest();
        req.setIds(List.of(BUNDLE_ID));
        req.setStatus(ProductStatus.SCHEDULED);

        assertThrows(BadRequestException.class,
                () -> service.batchUpdateBundles(COMPANY_ID, OWNER_ID, req));

        verify(bundleRepository, never()).save(any(ProductBundle.class));
    }

    @Test
    void batchUpdateBundles_countMismatch_throwsNotFound() {
        UUID bid1 = TestIds.uuid(10);
        UUID bid2 = TestIds.uuid(11);
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(makeBundle(bid1, new BigDecimal("20.00"))));

        BatchUpdateBundlesRequest req = new BatchUpdateBundlesRequest();
        req.setIds(List.of(bid1, bid2));
        req.setListed(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.batchUpdateBundles(COMPANY_ID, OWNER_ID, req));
    }

    // ─── batchDeleteBundles ───────────────────────────────────────────────────

    @Test
    void batchDeleteBundles_validIds_deletesAllAndPublishesEvents() {
        UUID bid1 = TestIds.uuid(10);
        UUID bid2 = TestIds.uuid(11);
        ProductBundle b1 = makeBundle(bid1, new BigDecimal("20.00"));
        ProductBundle b2 = makeBundle(bid2, new BigDecimal("30.00"));
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(b1, b2));

        BatchDeleteBundlesRequest req = new BatchDeleteBundlesRequest();
        req.setIds(List.of(bid1, bid2));

        service.batchDeleteBundles(COMPANY_ID, OWNER_ID, req);

        verify(bundleRepository).deleteAll(List.of(b1, b2));
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void batchDeleteBundles_countMismatch_throwsNotFound() {
        UUID bid1 = TestIds.uuid(10);
        UUID bid2 = TestIds.uuid(11);
        when(bundleRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(makeBundle(bid1, new BigDecimal("20.00"))));

        BatchDeleteBundlesRequest req = new BatchDeleteBundlesRequest();
        req.setIds(List.of(bid1, bid2));

        assertThrows(ResourceNotFoundException.class,
                () -> service.batchDeleteBundles(COMPANY_ID, OWNER_ID, req));

        verify(bundleRepository, never()).deleteAll(anyList());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Company makeCompany() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("Test Company");
        return c;
    }

    private Product makeProduct(UUID id, BigDecimal price) {
        Product p = new Product();
        p.setId(id);
        p.setCompany(makeCompany());
        p.setName("Test Product");
        p.setPrice(price);
        p.setCurrency("USD");
        p.setStatus(ProductStatus.ACTIVE);
        p.setImages(new ArrayList<>());
        p.setOptions(new ArrayList<>());
        p.setVariants(new ArrayList<>());
        p.setAttributes(new ArrayList<>());
        return p;
    }

    private ProductVariant makeVariant(UUID id, BigDecimal price) {
        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setPrice(price);
        return v;
    }

    private ProductBundle makeBundle(UUID id, BigDecimal price) {
        ProductBundle b = new ProductBundle();
        b.setId(id);
        b.setCompany(makeCompany());
        b.setName("Test Bundle");
        b.setPrice(price);
        b.setCurrency("USD");
        b.setStatus(ProductStatus.ACTIVE);

        // Add one placeholder item so price-recompute paths have something to sum
        backend.models.core.BundleItem item = new backend.models.core.BundleItem();
        item.setProduct(makeProduct(PRODUCT_ID, price));
        item.setQuantity(1);
        b.setItems(new ArrayList<>(List.of(item)));
        return b;
    }

    private BundleItemRequest item(UUID productId, UUID variantId, int quantity) {
        BundleItemRequest r = new BundleItemRequest();
        r.setProductId(productId);
        r.setVariantId(variantId);
        r.setQuantity(quantity);
        return r;
    }

    private CreateBundleRequest makeCreateRequest(BigDecimal price, List<BundleItemRequest> items) {
        CreateBundleRequest req = new CreateBundleRequest();
        req.setName("Test Bundle");
        req.setPrice(price);
        req.setCurrency("USD");
        req.setItems(items);
        return req;
    }
}
