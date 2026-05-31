package backend.models.enums;

/**
 * High-level shape of a {@code ProductChangeLog} entry.
 * Mirrors {@link backend.events.activity.ChangeType} but lives on the JPA layer so the
 * audit table is decoupled from the Kafka event type.
 */
public enum ProductChangeType {
    CREATED,
    UPDATED,
    DELETED
}
