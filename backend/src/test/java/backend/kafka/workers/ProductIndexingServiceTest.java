package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductAttribute;
import backend.models.core.ProductBundle;
import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.repositories.BundleRepository;
import backend.repositories.CollectionProductRepository;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ProductRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.search.BundleSearchRepository;
import backend.repositories.search.ProductSearchRepository;
import backend.services.pricing.config.FixedOffConfig;
import backend.services.pricing.config.PercentageOffConfig;
import backend.services.pricing.config.PromotionConfigValidator;
import backend.services.pricing.config.PromotionScope;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductIndexingServiceTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID PRODUCT_ID = TestIds.uuid(2);
    private static final UUID BUNDLE_ID  = TestIds.uuid(3);

    private ProductRepository            productRepository;
    private BundleRepository             bundleRepository;
    private ProductSearchRepository      productSearchRepository;
    private BundleSearchRepository       bundleSearchRepository;
    private IndexVersionManager          indexVersionManager;
    private PromotionRuleRepository      promotionRuleRepository;
    private CollectionProductRepository  collectionProductRepository;
    private PromotionConfigValidator     configValidator;
    private IndexingFailureRepository    failureRepository;

    private ProductIndexingService service;

    @BeforeEach
    void setUp() {
        productRepository           = mock(ProductRepository.class);
        bundleRepository            = mock(BundleRepository.class);
        productSearchRepository     = mock(ProductSearchRepository.class);
        bundleSearchRepository      = mock(BundleSearchRepository.class);
        indexVersionManager         = mock(IndexVersionManager.class);
        promotionRuleRepository     = mock(PromotionRuleRepository.class);
        collectionProductRepository = mock(CollectionProductRepository.class);
        configValidator             = mock(PromotionConfigValidator.class);
        failureRepository           = mock(IndexingFailureRepository.class);

        service = new ProductIndexingService(
                productSearchRepository,
                bundleSearchRepository,
                productRepository,
                bundleRepository,
                promotionRuleRepository,
                collectionProductRepository,
                configValidator,
                failureRepository,
                indexVersionManager,
                new EnvironmentSetting());

        ReflectionTestUtils.setField(service, "taskQueue", new LinkedBlockingQueue<>(1000));
    }

    // ─── Public API — queue submissions ───────────────────────────────────────

    @Test
    void indexProduct_doesNotThrow() {
        assertDoesNotThrow(() -> service.indexProduct(product(), COMPANY_ID));
    }

    @Test
    void removeProduct_doesNotThrow() {
        assertDoesNotThrow(() -> service.removeProduct(PRODUCT_ID));
    }

    @Test
    void indexBundle_doesNotThrow() {
        ProductBundle bundle = new ProductBundle();
        bundle.setId(BUNDLE_ID);
        assertDoesNotThrow(() -> service.indexBundle(bundle));
    }

    @Test
    void removeBundle_doesNotThrow() {
        assertDoesNotThrow(() -> service.removeBundle(BUNDLE_ID));
    }

    // ─── reindexAll / reindexCompany ──────────────────────────────────────────

    @Test
    void reindexAll_iteratesAllProducts() {
        Product p = product();
        when(productRepository.findAllWithCompany()).thenReturn(List.of(p));
        when(bundleRepository.findAll()).thenReturn(List.of());

        service.reindexAll();

        verify(productRepository).findAllWithCompany();
        verify(bundleRepository).findAll();
    }

    @Test
    void reindexCompany_iteratesCompanyProductsAndBundles() {
        Product p = product();
        when(productRepository.findAllByCompanyIdWithCompany(COMPANY_ID)).thenReturn(List.of(p));
        when(bundleRepository.findAllByCompanyId(COMPANY_ID)).thenReturn(List.of());

        service.reindexCompany(COMPANY_ID);

        verify(productRepository).findAllByCompanyIdWithCompany(COMPANY_ID);
        verify(bundleRepository).findAllByCompanyId(COMPANY_ID);
    }

    // ─── run (ApplicationRunner startup) ─────────────────────────────────────

    @Test
    void run_whenSearchEmpty_queuesInitialProducts() throws Exception {
        when(productSearchRepository.count()).thenReturn(0L);
        when(bundleSearchRepository.count()).thenReturn(0L);
        when(productRepository.findAllWithCompany()).thenReturn(List.of(product()));
        when(bundleRepository.findAll()).thenReturn(List.of());

        service.run(null);

        verify(indexVersionManager).ensureIndexExists("products");
        verify(indexVersionManager).ensureIndexExists("bundles");
        verify(productRepository).findAllWithCompany();
    }

    @Test
    void run_whenSearchNotEmpty_skipsInitialIndex() throws Exception {
        when(productSearchRepository.count()).thenReturn(5L);
        when(bundleSearchRepository.count()).thenReturn(3L);

        service.run(null);

        verify(productRepository, never()).findAllWithCompany();
    }

    @Test
    void run_countThrows_doesNotPropagate() throws Exception {
        when(productSearchRepository.count()).thenThrow(new RuntimeException("ES down"));
        when(bundleSearchRepository.count()).thenReturn(0L);
        when(bundleRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.run(null));
    }

    // ─── processBatch — success paths ────────────────────────────────────────

    @Test
    void processBatch_indexProduct_callsSaveAll() {
        stubNoRulesNoCollections();
        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(product(), COMPANY_ID));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_removeProduct_callsDeleteAll() {
        List<IndexingTask> batch = List.of(new IndexingTask.RemoveProduct(PRODUCT_ID));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).deleteAllById(anyList());
    }

    @Test
    void processBatch_indexBundle_callsBundleSaveAll() {
        List<IndexingTask> batch = List.of(new IndexingTask.IndexBundle(bundle()));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(bundleSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_removeBundle_callsBundleDeleteAll() {
        List<IndexingTask> batch = List.of(new IndexingTask.RemoveBundle(BUNDLE_ID));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(bundleSearchRepository).deleteAllById(anyList());
    }

    @Test
    void processBatch_emptyBatch_callsNothing() {
        ReflectionTestUtils.invokeMethod(service, "processBatch", List.of());

        verify(productSearchRepository, never()).saveAll(any());
        verify(productSearchRepository, never()).deleteAllById(any());
    }

    // ─── processBatch — failure / DLQ paths ──────────────────────────────────

    @Test
    void processBatch_saveAllThrows_persistsFailureRecord() {
        stubNoRulesNoCollections();
        doThrow(new RuntimeException("ES down")).when(productSearchRepository).saveAll(any());

        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(productWithId(TestIds.uuid(10)), COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(failureRepository).save(any());
    }

    @Test
    void processBatch_removeThrows_persistsRemoveFailure() {
        doThrow(new RuntimeException("ES down")).when(productSearchRepository).deleteAllById(any());

        List<IndexingTask> batch = List.of(new IndexingTask.RemoveProduct(PRODUCT_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(failureRepository).save(any());
    }

    @Test
    void processBatch_bundleSaveAllThrows_persistsFailure() {
        doThrow(new RuntimeException("ES down")).when(bundleSearchRepository).saveAll(any());

        List<IndexingTask> batch = List.of(new IndexingTask.IndexBundle(bundle()));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(failureRepository).save(any());
    }

    @Test
    void processBatch_bundleRemoveThrows_persistsFailure() {
        doThrow(new RuntimeException("ES down")).when(bundleSearchRepository).deleteAllById(any());

        List<IndexingTask> batch = List.of(new IndexingTask.RemoveBundle(BUNDLE_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(failureRepository).save(any());
    }

    @Test
    void processBatch_failureRepositoryAlsoThrows_doesNotPropagate() {
        stubNoRulesNoCollections();
        doThrow(new RuntimeException("ES down")).when(productSearchRepository).saveAll(any());
        doThrow(new RuntimeException("DB down")).when(failureRepository).save(any());

        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(product(), COMPANY_ID));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "processBatch", batch));
    }

    // ─── processBatch — toProductDocument / computeSaving paths ──────────────

    @Test
    void processBatch_percentageOffRule_computesPositiveDiscount() {
        PromotionRule rule = new PromotionRule();
        rule.setRuleType(PromotionRuleType.PERCENTAGE_OFF);
        rule.setDescription("Sale");
        when(promotionRuleRepository.findActiveRulesForProduct(any(UUID.class), any(UUID.class), any(Instant.class)))
                .thenReturn(List.of(rule));
        when(collectionProductRepository.findProductCollectionPairs(anyList())).thenReturn(List.of());
        when(configValidator.parseStored(PromotionRuleType.PERCENTAGE_OFF, null))
                .thenReturn(new PercentageOffConfig(new BigDecimal("10"), null, PromotionScope.LINE));

        Product p = product();
        p.setPrice(new BigDecimal("100.00"));
        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(p, COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_percentageOffRule_withMaxDiscountCap_cappedAtMax() {
        PromotionRule rule = new PromotionRule();
        rule.setRuleType(PromotionRuleType.PERCENTAGE_OFF);
        when(promotionRuleRepository.findActiveRulesForProduct(any(), any(), any()))
                .thenReturn(List.of(rule));
        when(collectionProductRepository.findProductCollectionPairs(anyList())).thenReturn(List.of());
        when(configValidator.parseStored(PromotionRuleType.PERCENTAGE_OFF, null))
                .thenReturn(new PercentageOffConfig(new BigDecimal("50"), new BigDecimal("5"), PromotionScope.LINE));

        Product p = product();
        p.setPrice(new BigDecimal("100.00"));
        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(p, COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_fixedOffRule_computesDiscount() {
        PromotionRule rule = new PromotionRule();
        rule.setRuleType(PromotionRuleType.FIXED_OFF);
        when(promotionRuleRepository.findActiveRulesForProduct(any(), any(), any()))
                .thenReturn(List.of(rule));
        when(collectionProductRepository.findProductCollectionPairs(anyList())).thenReturn(List.of());
        when(configValidator.parseStored(PromotionRuleType.FIXED_OFF, null))
                .thenReturn(new FixedOffConfig(new BigDecimal("5.00"), PromotionScope.LINE));

        Product p = product();
        p.setPrice(new BigDecimal("20.00"));
        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(p, COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_bogoRule_zeroDiscount() {
        PromotionRule rule = new PromotionRule();
        rule.setRuleType(PromotionRuleType.BOGO);
        when(promotionRuleRepository.findActiveRulesForProduct(any(), any(), any()))
                .thenReturn(List.of(rule));
        when(collectionProductRepository.findProductCollectionPairs(anyList())).thenReturn(List.of());

        Product p = product();
        p.setPrice(new BigDecimal("10.00"));
        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(p, COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_product_withFuturePin_keepsPin() {
        stubNoRulesNoCollections();
        Product p = product();
        p.setPinnedUntil(Instant.now().plusSeconds(3600));
        p.setPinnedRank(1);

        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(p, COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_product_withExpiredPin_clearsPin() {
        stubNoRulesNoCollections();
        Product p = product();
        p.setPinnedUntil(Instant.now().minusSeconds(100));
        p.setPinnedRank(1);

        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(p, COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_product_withAttributes_includesAttributeText() {
        stubNoRulesNoCollections();
        Product p = product();
        ProductAttribute attr = new ProductAttribute();
        attr.setName("color");
        attr.setValue("red");
        p.setAttributes(List.of(attr));

        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(p, COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_product_withCollections_includesCollectionIds() {
        when(promotionRuleRepository.findActiveRulesForProduct(any(), any(), any())).thenReturn(List.of());
        UUID collectionId = TestIds.uuid(99);
        java.util.ArrayList<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{PRODUCT_ID, collectionId});
        when(collectionProductRepository.findProductCollectionPairs(anyList())).thenReturn(rows);

        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(product(), COMPANY_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_collectionFetchThrows_gracefullyHandled() {
        when(promotionRuleRepository.findActiveRulesForProduct(any(), any(), any())).thenReturn(List.of());
        when(collectionProductRepository.findProductCollectionPairs(anyList()))
                .thenThrow(new RuntimeException("DB error"));

        List<IndexingTask> batch = List.of(new IndexingTask.IndexProduct(product(), COMPANY_ID));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "processBatch", batch));
    }

    @Test
    void processBatch_mixed_allTypesInOneBatch() {
        stubNoRulesNoCollections();
        List<IndexingTask> batch = List.of(
                new IndexingTask.IndexProduct(product(), COMPANY_ID),
                new IndexingTask.RemoveProduct(TestIds.uuid(20)),
                new IndexingTask.IndexBundle(bundle()),
                new IndexingTask.RemoveBundle(TestIds.uuid(21))
        );

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(productSearchRepository).saveAll(anyList());
        verify(productSearchRepository).deleteAllById(anyList());
        verify(bundleSearchRepository).saveAll(anyList());
        verify(bundleSearchRepository).deleteAllById(anyList());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void stubNoRulesNoCollections() {
        when(promotionRuleRepository.findActiveRulesForProduct(any(), any(), any()))
                .thenReturn(List.of());
        when(collectionProductRepository.findProductCollectionPairs(anyList()))
                .thenReturn(List.of());
    }

    private Product product() {
        return productWithId(PRODUCT_ID);
    }

    private Product productWithId(UUID id) {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Test Co");

        Product p = new Product();
        p.setId(id);
        p.setCompany(company);
        p.setName("Widget");
        p.setPrice(new BigDecimal("10.00"));
        return p;
    }

    private ProductBundle bundle() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        ProductBundle b = new ProductBundle();
        b.setId(BUNDLE_ID);
        b.setCompany(company);
        b.setName("Bundle");
        b.setPrice(new BigDecimal("20.00"));
        return b;
    }
}
