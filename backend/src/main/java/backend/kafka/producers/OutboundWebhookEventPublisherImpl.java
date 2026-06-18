package backend.kafka.producers;

import backend.events.webhook.OutboundWebhookEvent;
import backend.services.intf.OutboundWebhookEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OutboundWebhookEventPublisherImpl implements OutboundWebhookEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboundWebhookEventPublisherImpl.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public OutboundWebhookEventPublisherImpl(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.outbound-webhook-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(OutboundWebhookEvent event) {
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

    private void doSend(OutboundWebhookEvent event) {
        try {
            kafkaTemplate.send(topic, event.companyId().toString(), event).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("outbound webhook event publish failed eventType={} companyId={}",
                            event.eventType(), event.companyId(), ex);
                }
            });
        } catch (Throwable t) {
            log.warn("outbound webhook event publish error eventType={} companyId={}",
                    event.eventType(), event.companyId(), t);
        }
    }
}
