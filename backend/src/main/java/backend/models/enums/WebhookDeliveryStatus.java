package backend.models.enums;

public enum WebhookDeliveryStatus {
    /**
     * Default state of a freshly-constructed {@code WebhookDeliveryLog} entity. The consumer
     * delivers synchronously and only ever persists the terminal {@link #DELIVERED} or
     * {@link #FAILED} status, so PENDING is never written to the DB today. It is reserved for a
     * future async/scheduled-delivery flow that would persist a row before attempting delivery.
     */
    PENDING,
    DELIVERED,
    FAILED
}
