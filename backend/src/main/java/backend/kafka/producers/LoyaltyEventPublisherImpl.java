package backend.kafka.producers;

import backend.events.loyalty.LoyaltyEvent;
import backend.services.intf.promotions.LoyaltyEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class LoyaltyEventPublisherImpl implements LoyaltyEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyEventPublisherImpl.class);

    private final KafkaTemplate<String, LoyaltyEvent> kafkaTemplate;
    private final String topic;

    public LoyaltyEventPublisherImpl(
            KafkaTemplate<String, LoyaltyEvent> kafkaTemplate,
            @Value("${app.kafka.topics.loyalty-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(LoyaltyEvent event) {
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

    private void doSend(LoyaltyEvent event) {
        try {
            String key = resolveKey(event);
            kafkaTemplate.send(topic, key, event).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("loyalty event publish failed type={}", event.getClass().getSimpleName(), ex);
                }
            });
        } catch (Throwable t) {
            log.warn("loyalty event publish error type={}", event.getClass().getSimpleName(), t);
        }
    }

    private String resolveKey(LoyaltyEvent event) {
        return switch (event) {
            case LoyaltyEvent.PointsEarned e       -> e.userId().toString();
            case LoyaltyEvent.TierUpgraded e       -> e.userId().toString();
            case LoyaltyEvent.PointsExpiringSoon e -> e.userId().toString();
            default                                -> event.getClass().getSimpleName();
        };
    }
}
