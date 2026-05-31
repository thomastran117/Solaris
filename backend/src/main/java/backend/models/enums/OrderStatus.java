package backend.models.enums;

public enum OrderStatus {
    /** Stock reserved in DB; Stripe payment intent created but webhook not yet received. */
    RESERVED,
    /** Payment confirmed via webhook; items queued for warehouse fulfillment. */
    PAID,
    /** All items physically assembled by the warehouse; ready for carrier handoff. */
    PACKED,
    /** Some items shipped to carrier; others still being processed (multi-location / backorder). */
    PARTIALLY_FULFILLED,
    /** All items handed to carrier; tracking number recorded. */
    SHIPPED,
    /** Delivery confirmed by merchant or carrier. */
    DELIVERED,
    /** Items returned by customer after delivery; stock restored. */
    RETURNED,
    /** Payment failed via Stripe webhook; stock restored. */
    FAILED,
    /** Order cancelled by customer (allowed from RESERVED, PAID, or PACKED). */
    CANCELLED,
    /** Full refund issued post-delivery. */
    REFUNDED,
    /** Held by fraud engine; merchant decision pending. Stock is reserved but Stripe was NOT charged. */
    UNDER_REVIEW
}
