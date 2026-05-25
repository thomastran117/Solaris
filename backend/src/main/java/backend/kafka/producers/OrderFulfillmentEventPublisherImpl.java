package backend.kafka.producers;

import backend.events.order.OrderFulfillmentEvent;
import backend.services.intf.orders.OrderFulfillmentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderFulfillmentEventPublisherImpl implements OrderFulfillmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentEventPublisherImpl.class);

    private final KafkaTemplate<String, OrderFulfillmentEvent> kafkaTemplate;
    private final String topic;

    public OrderFulfillmentEventPublisherImpl(
            KafkaTemplate<String, OrderFulfillmentEvent> kafkaTemplate,
            @Value("${app.kafka.topics.order-fulfillment-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(OrderFulfillmentEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(event);
                }
            });
        } else {
            doSend(event);
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

    private String resolveKey(OrderFulfillmentEvent event) {
        return switch (event) {
            case OrderFulfillmentEvent.Shipped e     -> e.orderId().toString();
            case OrderFulfillmentEvent.PickupReady e -> e.orderId().toString();
            case OrderFulfillmentEvent.Delivered e   -> e.orderId().toString();
        };
    }
}
