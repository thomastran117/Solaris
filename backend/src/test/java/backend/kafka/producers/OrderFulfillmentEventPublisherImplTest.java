package backend.kafka.producers;

import backend.events.order.OrderFulfillmentEvent;
import backend.models.enums.CancellationReason;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderFulfillmentEventPublisherImplTest {

    private static final UUID ORDER_ID   = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final UUID COMPANY_ID = TestIds.uuid(3);
    private static final String TOPIC    = "order-fulfillment-events";

    private KafkaTemplate<String, OrderFulfillmentEvent> kafkaTemplate;
    private OrderFulfillmentEventPublisherImpl publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new OrderFulfillmentEventPublisherImpl(kafkaTemplate, TOPIC);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    void publish_shipped_sendsWithOrderIdAsKey() {
        OrderFulfillmentEvent event = new OrderFulfillmentEvent.Shipped(
                ORDER_ID, USER_ID, COMPANY_ID, "TRACK-1", "UPS", Instant.now());

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(ORDER_ID.toString()), eq(event));
    }

    @Test
    void publish_pickupReady_sendsWithOrderIdAsKey() {
        OrderFulfillmentEvent event = new OrderFulfillmentEvent.PickupReady(
                ORDER_ID, USER_ID, COMPANY_ID, "Main Store", Instant.now());

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(ORDER_ID.toString()), eq(event));
    }

    @Test
    void publish_delivered_sendsWithOrderIdAsKey() {
        OrderFulfillmentEvent event = new OrderFulfillmentEvent.Delivered(
                ORDER_ID, USER_ID, COMPANY_ID, Instant.now());

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(ORDER_ID.toString()), eq(event));
    }

    @Test
    void publish_cancelled_sendsWithOrderIdAsKey() {
        OrderFulfillmentEvent event = new OrderFulfillmentEvent.Cancelled(
                ORDER_ID, USER_ID, CancellationReason.OUT_OF_STOCK, Instant.now());

        publisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(ORDER_ID.toString()), eq(event));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_sendFails_exceptionSwallowed() {
        OrderFulfillmentEvent event = new OrderFulfillmentEvent.Delivered(
                ORDER_ID, USER_ID, COMPANY_ID, Instant.now());
        CompletableFuture<SendResult<String, OrderFulfillmentEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        publisher.publish(event); // must not throw
    }

    @Test
    void publish_kafkaThrowsSynchronously_exceptionSwallowed() {
        OrderFulfillmentEvent event = new OrderFulfillmentEvent.Shipped(
                ORDER_ID, USER_ID, COMPANY_ID, "TRK", "FedEx", Instant.now());
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("broker unavailable"));

        publisher.publish(event); // must not throw
    }
}
