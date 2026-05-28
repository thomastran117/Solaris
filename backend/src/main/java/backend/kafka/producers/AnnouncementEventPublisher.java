package backend.kafka.producers;

import backend.events.announcement.AnnouncementPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AnnouncementEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementEventPublisher.class);

    private final KafkaTemplate<String, AnnouncementPublishedEvent> kafkaTemplate;
    private final String topic;

    public AnnouncementEventPublisher(
            KafkaTemplate<String, AnnouncementPublishedEvent> kafkaTemplate,
            @Value("${app.kafka.topics.announcement-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(AnnouncementPublishedEvent event) {
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

    private void doSend(AnnouncementPublishedEvent event) {
        try {
            kafkaTemplate.send(topic, event.companyId().toString(), event).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("announcement event publish failed id={}", event.announcementId(), ex);
                }
            });
        } catch (Throwable t) {
            log.warn("announcement event publish error id={}", event.announcementId(), t);
        }
    }
}
