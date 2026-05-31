package backend.kafka.producers;

import backend.events.notification.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class NotificationEventPublisherImplTest {

    private static final String TOPIC = "notification-events";

    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private NotificationEventPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new NotificationEventPublisherImpl(kafkaTemplate, TOPIC);
        when(kafkaTemplate.send(any(String.class), any(NotificationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    // ─── publish ─────────────────────────────────────────────────────────────

    @Test
    void publish_sendsToCorrectTopic() {
        NotificationEvent event = new NotificationEvent.OrderShipped(
                UUID.randomUUID(), UUID.randomUUID(), "Alice", "TRK123", "UPS");

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), any(NotificationEvent.OrderShipped.class));
    }

    @Test
    void publish_kafkaFailure_doesNotThrow() {
        CompletableFuture<SendResult<String, NotificationEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(any(String.class), any(NotificationEvent.class))).thenReturn(failed);

        assertDoesNotThrow(() -> publisher.publish(
                new NotificationEvent.OrderDelivered(UUID.randomUUID(), UUID.randomUUID(), "Bob")));
    }
}
