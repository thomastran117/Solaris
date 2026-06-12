package backend.kafka.producers;

import backend.events.notification.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class NotificationEventPublisherImplTest {

    private static final String TOPIC = "notification-events";

    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private NotificationEventPublisherImpl publisher;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

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

    @Test
    void publish_orderCancelledEvent_sendsToTopic() {
        NotificationEvent event = new NotificationEvent.OrderCancelled(
                UUID.randomUUID(), UUID.randomUUID(), "Eve", "insufficient stock");

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), any(NotificationEvent.OrderCancelled.class));
    }

    @Test
    void publish_kafkaThrowsOnSend_doesNotPropagate() {
        when(kafkaTemplate.send(any(String.class), any(NotificationEvent.class)))
                .thenThrow(new RuntimeException("broker error"));

        assertDoesNotThrow(() -> publisher.publish(
                new NotificationEvent.OrderShipped(UUID.randomUUID(), UUID.randomUUID(), "Carol", "TRK1", "FedEx")));
    }

    @Test
    void publish_withActiveTransaction_sendsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        NotificationEvent event = new NotificationEvent.OrderShipped(
                UUID.randomUUID(), UUID.randomUUID(), "Dave", "TRK2", "DHL");

        publisher.publish(event);
        verify(kafkaTemplate, never()).send(any(String.class), any(NotificationEvent.class));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCommit());

        verify(kafkaTemplate).send(eq(TOPIC), eq(event));
    }
}
