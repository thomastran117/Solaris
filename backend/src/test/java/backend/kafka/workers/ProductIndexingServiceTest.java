package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.repositories.BundleRepository;
import backend.repositories.CollectionProductRepository;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ProductRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.search.BundleSearchRepository;
import backend.repositories.search.ProductSearchRepository;
import backend.services.pricing.config.PromotionConfigValidator;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductIndexingServiceTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID PRODUCT_ID = TestIds.uuid(2);
    private static final UUID BUNDLE_ID  = TestIds.uuid(3);

    private ProductRepository       productRepository;
    private BundleRepository        bundleRepository;
    private ProductSearchRepository productSearchRepository;
    private BundleSearchRepository  bundleSearchRepository;
    private IndexVersionManager     indexVersionManager;

    private ProductIndexingService service;

    @BeforeEach
    void setUp() {
        productRepository       = mock(ProductRepository.class);
        bundleRepository        = mock(BundleRepository.class);
        productSearchRepository = mock(ProductSearchRepository.class);
        bundleSearchRepository  = mock(BundleSearchRepository.class);
        indexVersionManager     = mock(IndexVersionManager.class);

        service = new ProductIndexingService(
                productSearchRepository,
                bundleSearchRepository,
                productRepository,
                bundleRepository,
                mock(PromotionRuleRepository.class),
                mock(CollectionProductRepository.class),
                mock(PromotionConfigValidator.class),
                mock(IndexingFailureRepository.class),
                indexVersionManager,
                new EnvironmentSetting());

        // Inject a real queue — bypasses @PostConstruct threading setup
        ReflectionTestUtils.setField(service, "taskQueue", new LinkedBlockingQueue<>(1000));
    }

    // ─── Public API — queue submissions ───────────────────────────────────────

    @Test
    void indexProduct_doesNotThrow() {
        Product product = product();

        assertDoesNotThrow(() -> service.indexProduct(product, COMPANY_ID));
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

        verify(productRepository, org.mockito.Mockito.never()).findAllWithCompany();
    }

    @Test
    void run_countThrows_doesNotPropagate() throws Exception {
        when(productSearchRepository.count()).thenThrow(new RuntimeException("ES down"));
        when(bundleSearchRepository.count()).thenReturn(0L);
        when(bundleRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.run(null));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Product product() {
        backend.models.core.Company company = new backend.models.core.Company();
        company.setId(COMPANY_ID);
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCompany(company);
        p.setName("Widget");
        return p;
    }
}
