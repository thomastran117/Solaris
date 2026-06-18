package backend.dtos.responses;

import backend.models.enums.WebhookDeliveryStatus;
import backend.models.enums.WebhookEventType;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryLogResponse(
        UUID id,
        UUID subscriptionId,
        WebhookEventType eventType,
        Integer responseStatus,
        int attemptCount,
        Instant deliveredAt,
        WebhookDeliveryStatus status,
        Instant createdAt
) {}
