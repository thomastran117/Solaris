package backend.kafka.consumers;

import backend.events.activity.ChangeType;
import backend.events.activity.ProductChangedEvent;
import backend.repositories.ProductRepository;
import backend.services.impl.products.ProductSimilarityService;
import backend.testutil.TestIds;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ProductSimilarityConsumerTest {

    private static final UUID PRODUCT_ID = TestIds.uuid(1);

    private ProductSimilarityService productSimilarityService;
    private ProductRepository productRepository;
    private MeterRegistry meterRegistry;
    private ProductSimilarityConsumer consumer;

    @BeforeEach
    void setUp() {
        productSimilarityService = mock(ProductSimilarityService.class);
        productRepository        = mock(ProductRepository.class);
        meterRegistry            = new SimpleMeterRegistry();

        consumer = new ProductSimilarityConsumer(productSimilarityService, productRepository, meterRegistry);

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
    }

    @Test
    void consumer_callsRecomputeOnCreated() {
        consumer.onProductEvent(event(ChangeType.CREATED));
        verify(productSimilarityService).recompute(PRODUCT_ID);
    }

    @Test
    void consumer_callsRecomputeOnUpdated() {
        consumer.onProductEvent(event(ChangeType.UPDATED));
        verify(productSimilarityService).recompute(PRODUCT_ID);
    }

    @Test
    void consumer_skipsDeleted() {
        consumer.onProductEvent(event(ChangeType.DELETED));
        verify(productSimilarityService, never()).recompute(any());
    }

    @Test
    void consumer_exceptionSuppressed_failureCounterIncremented() {
        doThrow(new RuntimeException("ES down")).when(productSimilarityService).recompute(PRODUCT_ID);

        assertDoesNotThrow(() -> consumer.onProductEvent(event(ChangeType.UPDATED)));

        double failureCount = meterRegistry.counter("similarity_recompute_failed_total").count();
        assertEquals(1.0, failureCount);
    }

    private ProductChangedEvent event(ChangeType type) {
        return new ProductChangedEvent(PRODUCT_ID, null, type, Instant.now());
    }
}
