package backend.services.intf;

import backend.events.activity.UserActivityEvent;

public interface ActivityEventPublisher {
    void publish(UserActivityEvent event);
}
