package backend.events.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka payload for the product-events topic.
 * Consumed by the Python recsys service to keep embeddings and FAISS in sync.
 */
public record ProductChangedEvent(
    UUID productId,
    Long marketplaceId,
    ChangeType changeType,
    Instant occurredAt
) {}
