package backend.kafka.producers;

import backend.events.order.OrderFulfillmentEvent;
import backend.events.webhook.OutboundWebhookEvent;
import backend.models.enums.WebhookEventType;
import backend.services.intf.OutboundWebhookEventPublisher;
import backend.services.intf.orders.OrderFulfillmentEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Service
public class OrderFulfillmentEventPublisherImpl implements OrderFulfillmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentEventPublisherImpl.class);

    private final KafkaTemplate<String, OrderFulfillmentEvent> kafkaTemplate;
    private final String topic;
    private final OutboundWebhookEventPublisher outboundWebhookEventPublisher;
    private final ObjectMapper objectMapper;

    public OrderFulfillmentEventPublisherImpl(
            KafkaTemplate<String, OrderFulfillmentEvent> kafkaTemplate,
            @Value("${app.kafka.topics.order-fulfillment-events}") String topic,
            OutboundWebhookEventPublisher outboundWebhookEventPublisher,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.outboundWebhookEventPublisher = outboundWebhookEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(OrderFulfillmentEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(event);
                    doSendOutbound(event);
                }
            });
        } else {
            doSend(event);
            doSendOutbound(event);
        }
    }

    private void doSend(OrderFulfillmentEvent event) {
        try {
            String key = resolveKey(event);
            kafkaTemplate.send(topic, key, event).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("order fulfillment event publish failed type={}", event.getClass().getSimpleName(), ex);
                }
            });
        } catch (Throwable t) {
            log.warn("order fulfillment event publish error type={}", event.getClass().getSimpleName(), t);
        }
    }

    private void doSendOutbound(OrderFulfillmentEvent event) {
        // ORDER_CANCELLED is handled by OrderServiceImpl (which has companyId context).
        // PickupReady has no webhook event type defined.
        try {
            OutboundWebhookEvent outbound = switch (event) {
                case OrderFulfillmentEvent.Shipped e -> new OutboundWebhookEvent(
                        WebhookEventType.ORDER_SHIPPED, e.companyId(), e.orderId(), null,
                        objectMapper.writeValueAsString(e), Instant.now());
                case OrderFulfillmentEvent.Delivered e -> new OutboundWebhookEvent(
                        WebhookEventType.ORDER_DELIVERED, e.companyId(), e.orderId(), null,
                        objectMapper.writeValueAsString(e), Instant.now());
                case OrderFulfillmentEvent.Cancelled e -> null;
                case OrderFulfillmentEvent.PickupReady e -> null;
            };
            if (outbound != null) {
                outboundWebhookEventPublisher.publish(outbound);
            }
        } catch (Exception e) {
            log.warn("outbound webhook event build failed for fulfillment event type={}", event.getClass().getSimpleName(), e);
        }
    }

    private String resolveKey(OrderFulfillmentEvent event) {
        return switch (event) {
            case OrderFulfillmentEvent.Shipped e     -> e.orderId().toString();
            case OrderFulfillmentEvent.PickupReady e -> e.orderId().toString();
            case OrderFulfillmentEvent.Delivered e   -> e.orderId().toString();
            case OrderFulfillmentEvent.Cancelled e   -> e.orderId().toString();
        };
    }
}
