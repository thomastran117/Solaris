package backend.models.enums;

public enum SubscriptionStatus {
    /** Subscription is created in Stripe but awaiting initial payment confirmation via webhook. */
    INCOMPLETE,
    /** Active subscription; Stripe is billing on cycle. */
    ACTIVE,
    /** Customer or merchant paused billing; no invoices are generated until resumed. */
    PAUSED,
    /** Most recent invoice failed payment; Stripe is retrying via smart retries. */
    PAST_DUE,
    /** Subscription was cancelled (immediately or scheduled for period end after final cycle). */
    CANCELLED,
    /** Subscription's initial setup failed or expired without becoming active. */
    EXPIRED
}
