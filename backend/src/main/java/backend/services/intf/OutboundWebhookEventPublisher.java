package backend.services.intf;

import backend.events.webhook.OutboundWebhookEvent;

public interface OutboundWebhookEventPublisher {
    void publish(OutboundWebhookEvent event);
}
