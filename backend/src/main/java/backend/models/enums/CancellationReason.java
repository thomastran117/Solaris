package backend.models.enums;

/**
 * Why an order ended up in CANCELLED / FAILED state. Set at the cancel call site
 * so the operations dashboard can group cancellations by root cause without
 * heuristically reverse-engineering them from {@code failureReason} strings.
 */
public enum CancellationReason {
    /** Customer explicitly cancelled via the customer-facing endpoint. */
    CUSTOMER_REQUEST,
    /** Stripe payment_intent.payment_failed webhook received. */
    PAYMENT_FAILED,
    /** Risk engine review rejected by the merchant. */
    RISK_REJECTED,
    /** Reservation expired before payment confirmation; compensated by scheduler. */
    STALE_TIMEOUT,
    /** Reserved for an order that was cancelled because stock could not be sourced. */
    OUT_OF_STOCK,
    /** Reserved for a future merchant-initiated cancel endpoint. */
    MERCHANT_CANCELLED,
    /** Catch-all when no other reason fits. */
    OTHER
}
