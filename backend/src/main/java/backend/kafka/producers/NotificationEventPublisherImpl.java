package backend.kafka.producers;

import backend.events.notification.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventPublisherImpl implements NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisherImpl.class);

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final String topic;

    public NotificationEventPublisherImpl(
            KafkaTemplate<String, NotificationEvent> kafkaTemplate,
            @Value("${app.kafka.topics.notification-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(NotificationEvent event) {
        kafkaTemplate.send(topic, event).whenComplete((res, ex) -> {
            if (ex != null) {
                log.warn("notification-events publish failed type={}", event.getClass().getSimpleName(), ex);
            }
        });
    }
}
