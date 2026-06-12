package backend.kafka.consumers;

import backend.events.activity.BundleChangedEvent;
import backend.events.activity.ChangeType;
import backend.events.activity.ProductChangedEvent;
import backend.kafka.workers.ProductIndexingService;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.repositories.BundleRepository;
import backend.repositories.ProductRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductIndexingKafkaConsumerTest {

    private static final UUID PRODUCT_ID    = TestIds.uuid(1);
    private static final UUID BUNDLE_ID     = TestIds.uuid(2);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(3);
    private static final UUID COMPANY_ID    = TestIds.uuid(4);

    private ProductIndexingService indexingService;
    private ProductRepository      productRepository;
    private BundleRepository       bundleRepository;

    private ProductIndexingKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        indexingService   = mock(ProductIndexingService.class);
        productRepository = mock(ProductRepository.class);
        bundleRepository  = mock(BundleRepository.class);
        consumer = new ProductIndexingKafkaConsumer(indexingService, productRepository, bundleRepository);
    }

    // ─── onProductEvent ───────────────────────────────────────────────────────

    @Test
    void onProductEvent_created_productFound_indexesProduct() {
        Product product = product();
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.of(product));

        consumer.onProductEvent(productEvent(PRODUCT_ID, ChangeType.CREATED));

        verify(indexingService).indexProduct(product, COMPANY_ID);
    }

    @Test
    void onProductEvent_updated_productFound_indexesProduct() {
        Product product = product();
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.of(product));

        consumer.onProductEvent(productEvent(PRODUCT_ID, ChangeType.UPDATED));

        verify(indexingService).indexProduct(product, COMPANY_ID);
    }

    @Test
    void onProductEvent_deleted_removesProduct() {
        consumer.onProductEvent(productEvent(PRODUCT_ID, ChangeType.DELETED));

        verify(indexingService).removeProduct(PRODUCT_ID);
        verify(productRepository, never()).findByIdWithCompanyOwner(any());
    }

    @Test
    void onProductEvent_created_productNotFound_doesNotIndex() {
        when(productRepository.findByIdWithCompanyOwner(PRODUCT_ID)).thenReturn(Optional.empty());

        consumer.onProductEvent(productEvent(PRODUCT_ID, ChangeType.CREATED));

        verify(indexingService, never()).indexProduct(any(), any());
    }

    // ─── onBundleEvent ────────────────────────────────────────────────────────

    @Test
    void onBundleEvent_created_bundleFound_indexesBundle() {
        ProductBundle bundle = bundle();
        when(bundleRepository.findById(BUNDLE_ID)).thenReturn(Optional.of(bundle));

        consumer.onBundleEvent(bundleEvent(BUNDLE_ID, ChangeType.CREATED));

        verify(indexingService).indexBundle(bundle);
    }

    @Test
    void onBundleEvent_deleted_removesBundle() {
        consumer.onBundleEvent(bundleEvent(BUNDLE_ID, ChangeType.DELETED));

        verify(indexingService).removeBundle(BUNDLE_ID);
        verify(bundleRepository, never()).findById(any());
    }

    @Test
    void onBundleEvent_created_bundleNotFound_doesNotIndex() {
        when(bundleRepository.findById(BUNDLE_ID)).thenReturn(Optional.empty());

        consumer.onBundleEvent(bundleEvent(BUNDLE_ID, ChangeType.CREATED));

        verify(indexingService, never()).indexBundle(any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Product product() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCompany(company);
        return p;
    }

    private ProductBundle bundle() {
        ProductBundle b = new ProductBundle();
        b.setId(BUNDLE_ID);
        return b;
    }

    private ProductChangedEvent productEvent(UUID productId, ChangeType type) {
        return new ProductChangedEvent(productId, MARKETPLACE_ID, type, Instant.now());
    }

    private BundleChangedEvent bundleEvent(UUID bundleId, ChangeType type) {
        return new BundleChangedEvent(bundleId, type, Instant.now());
    }
}
