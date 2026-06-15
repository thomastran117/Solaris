package backend.models.enums;

/** Lifecycle of a customer's requested delivery slot. */
public enum DeliverySlotStatus {
    /** Customer has requested a slot; vendor has not yet acted on it. */
    REQUESTED,
    /** Vendor confirmed they can meet the requested slot. */
    CONFIRMED,
    /** Vendor cannot meet the requested slot; customer has been notified. */
    UNAVAILABLE
}
