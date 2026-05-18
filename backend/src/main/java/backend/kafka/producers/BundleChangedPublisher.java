package backend.kafka.producers;

import backend.events.BundleIndexEvent;
import backend.events.BundleRemoveEvent;
import backend.events.activity.BundleChangedEvent;
import backend.events.activity.ChangeType;
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
public class BundleChangedPublisher {

    private static final Logger log = LoggerFactory.getLogger(BundleChangedPublisher.class);

    private final KafkaTemplate<String, BundleChangedEvent> kafkaTemplate;
    private final String topic;

    public BundleChangedPublisher(
            KafkaTemplate<String, BundleChangedEvent> kafkaTemplate,
            @Value("${app.kafka.topics.bundle-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBundleIndex(BundleIndexEvent event) {
        send(event.bundle().getId(), ChangeType.UPDATED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBundleRemove(BundleRemoveEvent event) {
        send(event.bundleId(), ChangeType.DELETED);
    }

    private void send(UUID bundleId, ChangeType changeType) {
        try {
            var payload = new BundleChangedEvent(bundleId, changeType, Instant.now());
            kafkaTemplate.send(topic, String.valueOf(bundleId), payload).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("bundle-events publish failed bundleId={} type={}", bundleId, changeType, ex);
                }
            });
        } catch (Throwable t) {
            log.warn("bundle-events publish error bundleId={} type={}", bundleId, changeType, t);
        }
    }
}
