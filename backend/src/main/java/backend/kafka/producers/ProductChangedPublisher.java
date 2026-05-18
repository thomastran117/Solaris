package backend.kafka.producers;

import backend.events.ProductIndexEvent;
import backend.events.ProductRemoveEvent;
import backend.events.activity.ChangeType;
import backend.events.activity.ProductChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Component
public class ProductChangedPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductChangedPublisher.class);

    private final KafkaTemplate<String, ProductChangedEvent> kafkaTemplate;
    private final String topic;
    private final boolean trackingEnabled;

    public ProductChangedPublisher(
            KafkaTemplate<String, ProductChangedEvent> kafkaTemplate,
            @Value("${app.kafka.topics.product-events}") String topic,
            @Value("${app.activity-tracking.enabled:true}") boolean trackingEnabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.trackingEnabled = trackingEnabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductIndex(ProductIndexEvent event) {
        if (!trackingEnabled) return;
        UUID marketplaceId = event.product().getMarketplaceId();
        if (marketplaceId == null) return;
        send(event.product().getId(), marketplaceId, ChangeType.UPDATED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductRemove(ProductRemoveEvent event) {
        if (!trackingEnabled) return;
        if (event.marketplaceId() == null) return;
        send(event.productId(), event.marketplaceId(), ChangeType.DELETED);
    }

    private void send(UUID productId, UUID marketplaceId, ChangeType changeType) {
        try {
            var payload = new ProductChangedEvent(productId, marketplaceId, changeType, Instant.now());
            kafkaTemplate.send(topic, String.valueOf(productId), payload).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("product-events publish failed productId={} type={}", productId, changeType, ex);
                }
            });
        } catch (Throwable t) {
            log.warn("product-events publish error productId={} type={}", productId, changeType, t);
        }
    }
}
