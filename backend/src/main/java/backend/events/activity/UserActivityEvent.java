package backend.events.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka payload for the user-activity topic.
 * userId and sessionId are mutually exclusive: one is always null.
 * marketplaceId is guaranteed non-null before publish (publisher drops events where it is absent).
 */
public record UserActivityEvent(
    UUID userId,
    String sessionId,
    UUID productId,
    Long marketplaceId,
    ActivityType activityType,
    Instant occurredAt
) {}
