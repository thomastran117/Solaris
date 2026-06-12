package backend.kafka.producers;

import backend.events.ProductIndexEvent;
import backend.events.ProductRemoveEvent;
import backend.events.activity.ChangeType;
import backend.events.activity.ProductChangedEvent;
import backend.models.core.Product;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductChangedPublisherTest {

    private static final UUID PRODUCT_ID = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(2);
    private static final UUID COMPANY_ID = TestIds.uuid(3);
    private static final String TOPIC = "product-events";

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, ProductChangedEvent> kafkaTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    private ProductChangedPublisher publisher(boolean trackingEnabled) {
        return new ProductChangedPublisher(kafkaTemplate, TOPIC, trackingEnabled);
    }

    private Product product(UUID marketplaceId) {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(PRODUCT_ID);
        when(p.getMarketplaceId()).thenReturn(marketplaceId);
        return p;
    }

    // ── onProductIndex ────────────────────────────────────────────────────────

    @Test
    void onProductIndex_trackingEnabled_sendsUpdated() {
        publisher(true).onProductIndex(new ProductIndexEvent(product(MARKETPLACE_ID), COMPANY_ID));

        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(PRODUCT_ID.toString()), captor.capture());
        assertEquals(ChangeType.UPDATED, captor.getValue().changeType());
        assertEquals(PRODUCT_ID, captor.getValue().productId());
    }

    @Test
    void onProductIndex_trackingDisabled_doesNotSend() {
        publisher(false).onProductIndex(new ProductIndexEvent(product(MARKETPLACE_ID), COMPANY_ID));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void onProductIndex_nullMarketplaceId_doesNotSend() {
        publisher(true).onProductIndex(new ProductIndexEvent(product(null), COMPANY_ID));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void onProductIndex_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));
        assertDoesNotThrow(() ->
                publisher(true).onProductIndex(new ProductIndexEvent(product(MARKETPLACE_ID), COMPANY_ID)));
    }

    // ── onProductRemove ───────────────────────────────────────────────────────

    @Test
    void onProductRemove_trackingEnabled_sendsDeleted() {
        publisher(true).onProductRemove(new ProductRemoveEvent(PRODUCT_ID, MARKETPLACE_ID));

        ArgumentCaptor<ProductChangedEvent> captor = ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(PRODUCT_ID.toString()), captor.capture());
        assertEquals(ChangeType.DELETED, captor.getValue().changeType());
    }

    @Test
    void onProductRemove_trackingDisabled_doesNotSend() {
        publisher(false).onProductRemove(new ProductRemoveEvent(PRODUCT_ID, MARKETPLACE_ID));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void onProductRemove_nullMarketplaceId_doesNotSend() {
        publisher(true).onProductRemove(new ProductRemoveEvent(PRODUCT_ID, null));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
