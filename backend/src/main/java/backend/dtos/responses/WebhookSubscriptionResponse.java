package backend.dtos.responses;

import backend.models.enums.WebhookEventType;
import backend.models.enums.WebhookSubscriptionStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WebhookSubscriptionResponse(
        UUID id,
        String url,
        Set<WebhookEventType> events,
        WebhookSubscriptionStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
