package backend.kafka.producers;

import backend.events.BundleIndexEvent;
import backend.events.BundleRemoveEvent;
import backend.events.activity.BundleChangedEvent;
import backend.events.activity.ChangeType;
import backend.models.core.ProductBundle;
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

class BundleChangedPublisherTest {

    private static final UUID BUNDLE_ID = TestIds.uuid(1);
    private static final String TOPIC = "bundle-events";

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, BundleChangedEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private BundleChangedPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        publisher = new BundleChangedPublisher(kafkaTemplate, TOPIC);
    }

    @Test
    void onBundleIndex_sendsUpdatedEvent() {
        ProductBundle bundle = mock(ProductBundle.class);
        when(bundle.getId()).thenReturn(BUNDLE_ID);

        publisher.onBundleIndex(new BundleIndexEvent(bundle));

        ArgumentCaptor<BundleChangedEvent> captor = ArgumentCaptor.forClass(BundleChangedEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(BUNDLE_ID.toString()), captor.capture());
        assertEquals(ChangeType.UPDATED, captor.getValue().changeType());
        assertEquals(BUNDLE_ID, captor.getValue().bundleId());
    }

    @Test
    void onBundleRemove_sendsDeletedEvent() {
        publisher.onBundleRemove(new BundleRemoveEvent(BUNDLE_ID));

        ArgumentCaptor<BundleChangedEvent> captor = ArgumentCaptor.forClass(BundleChangedEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(BUNDLE_ID.toString()), captor.capture());
        assertEquals(ChangeType.DELETED, captor.getValue().changeType());
        assertEquals(BUNDLE_ID, captor.getValue().bundleId());
    }

    @Test
    void onBundleIndex_kafkaThrows_doesNotPropagate() {
        ProductBundle bundle = mock(ProductBundle.class);
        when(bundle.getId()).thenReturn(BUNDLE_ID);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        assertDoesNotThrow(() -> publisher.onBundleIndex(new BundleIndexEvent(bundle)));
    }

    @Test
    void onBundleIndex_kafkaCallbackFails_doesNotPropagate() {
        ProductBundle bundle = mock(ProductBundle.class);
        when(bundle.getId()).thenReturn(BUNDLE_ID);
        CompletableFuture<SendResult<String, BundleChangedEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("async send failure"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        assertDoesNotThrow(() -> publisher.onBundleIndex(new BundleIndexEvent(bundle)));
    }

    @Test
    void onBundleRemove_kafkaThrows_doesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka down"));

        assertDoesNotThrow(() -> publisher.onBundleRemove(new BundleRemoveEvent(BUNDLE_ID)));
    }
}
