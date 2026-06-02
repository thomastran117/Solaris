package backend.services.impl.products;

import backend.events.ProductIndexEvent;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.enums.ProductStatus;
import backend.repositories.ProductRepository;
import backend.services.impl.SingleFlightCache;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSchedulingWorkerTest {

    private static final UUID COMPANY_ID      = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID  = TestIds.uuid(2);
    private static final UUID PRODUCT_ID      = TestIds.uuid(3);

    private ProductRepository       productRepository;
    private ApplicationEventPublisher eventPublisher;
    private SingleFlightCache        singleFlightCache;

    private ProductSchedulingWorker worker;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        eventPublisher    = mock(ApplicationEventPublisher.class);
        singleFlightCache = mock(SingleFlightCache.class);

        worker = new ProductSchedulingWorker(productRepository, eventPublisher, singleFlightCache);
    }

    // ─── publishDueProducts ───────────────────────────────────────────────────

    @Test
    void publishDueProducts_noDueProducts_doesNotPublishEvent() {
        when(productRepository.findDueForPublishing(eq(ProductStatus.SCHEDULED), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        worker.publishDueProducts();

        verify(eventPublisher, never()).publishEvent(any(ProductIndexEvent.class));
    }

    @Test
    void publishDueProducts_dueProduct_publishesIndexEventAndEvictsCache() {
        Product product = scheduledProduct(true);

        when(productRepository.findDueForPublishing(eq(ProductStatus.SCHEDULED), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)))
                .thenReturn(new PageImpl<>(List.of())); // second call returns empty to end loop

        worker.publishDueProducts();

        verify(eventPublisher).publishEvent(any(ProductIndexEvent.class));
        verify(singleFlightCache).evict("product:" + COMPANY_ID + ":" + PRODUCT_ID);
        verify(singleFlightCache).evict("marketplace:product:" + MARKETPLACE_ID + ":" + PRODUCT_ID);
    }

    @Test
    void publishDueProducts_dueProduct_setsStatusToActive() {
        Product product = scheduledProduct(true);

        when(productRepository.findDueForPublishing(eq(ProductStatus.SCHEDULED), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)))
                .thenReturn(new PageImpl<>(List.of()));

        worker.publishDueProducts();

        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        assertNull(product.getScheduledPublishAt());
        assertNotNull(product.getPublishedAt());
    }

    @Test
    void publishDueProducts_noMarketplaceId_skipsMarketplaceCacheEviction() {
        Product product = scheduledProduct(false); // no marketplace

        when(productRepository.findDueForPublishing(eq(ProductStatus.SCHEDULED), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)))
                .thenReturn(new PageImpl<>(List.of()));

        worker.publishDueProducts();

        verify(singleFlightCache, never()).evict("marketplace:product:" + MARKETPLACE_ID + ":" + PRODUCT_ID);
    }

    @Test
    void publishDueProducts_cacheEvictionThrows_doesNotPropagate() {
        Product product = scheduledProduct(true);

        when(productRepository.findDueForPublishing(eq(ProductStatus.SCHEDULED), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)))
                .thenReturn(new PageImpl<>(List.of()));
        org.mockito.Mockito.doThrow(new RuntimeException("Redis down"))
                .when(singleFlightCache).evict(any());

        worker.publishDueProducts(); // must not throw

        verify(eventPublisher).publishEvent(any(ProductIndexEvent.class));
    }

    // ─── flipNextBatch ────────────────────────────────────────────────────────

    @Test
    void flipNextBatch_returnsDueProducts() {
        Product product = scheduledProduct(true);

        when(productRepository.findDueForPublishing(eq(ProductStatus.SCHEDULED), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        List<Product> result = worker.flipNextBatch(Instant.now());

        assertEquals(1, result.size());
        assertEquals(ProductStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void flipNextBatch_emptyPage_returnsEmptyList() {
        when(productRepository.findDueForPublishing(eq(ProductStatus.SCHEDULED), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<Product> result = worker.flipNextBatch(Instant.now());

        assertEquals(0, result.size());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Product scheduledProduct(boolean withMarketplace) {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCompany(company);
        p.setStatus(ProductStatus.SCHEDULED);
        p.setScheduledPublishAt(Instant.now().minusSeconds(60));
        if (withMarketplace) {
            p.setMarketplaceId(MARKETPLACE_ID);
        }
        return p;
    }
}
