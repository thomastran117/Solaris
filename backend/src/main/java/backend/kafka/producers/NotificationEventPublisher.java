package backend.kafka.producers;

import backend.events.notification.NotificationEvent;

public interface NotificationEventPublisher {
    void publish(NotificationEvent event);
}
