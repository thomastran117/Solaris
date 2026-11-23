package backend.events.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka payload for the product-events topic, published for every product change.
 * Consumed by {@code ProductIndexingKafkaConsumer} (Elasticsearch projection) and
 * {@code ProductSimilarityConsumer}. {@code marketplaceId} is null for products that are not
 * listed on a marketplace.
 */
public record ProductChangedEvent(
    UUID productId,
    UUID marketplaceId,
    ChangeType changeType,
    Instant occurredAt
) {}
