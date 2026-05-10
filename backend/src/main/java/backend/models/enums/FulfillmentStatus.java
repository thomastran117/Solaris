package backend.models.enums;

public enum FulfillmentStatus {
    /** Stock reserved at order time; item is waiting to be packed by the warehouse. */
    PENDING,
    /** Zero stock at order time and backorderEnabled=true; waiting for restock. */
    BACKORDERED,
    /** Physically assembled by the warehouse; ready for carrier handoff. */
    PACKED,
    /** Pickup orders only: assembled at the store and waiting for the customer to collect. */
    PICKUP_READY,
    /** Handed to carrier. */
    SHIPPED,
    /** Delivery confirmed. */
    DELIVERED,
    /** Returned by the customer after delivery. */
    RETURNED,
    /** Item cancelled as part of an order cancellation. */
    CANCELLED
}
