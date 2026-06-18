package backend.events.webhook;

import backend.models.enums.WebhookEventType;

import java.time.Instant;
import java.util.UUID;

public record OutboundWebhookEvent(
        UUID eventId,
        WebhookEventType eventType,
        UUID companyId,
        UUID orderId,
        UUID productId,
        String payloadJson,
        Instant occurredAt
) {}
