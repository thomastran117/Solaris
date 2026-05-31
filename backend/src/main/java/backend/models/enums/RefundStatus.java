package backend.models.enums;

public enum RefundStatus {
    NONE,       // No refund requested or intentionally waived
    PENDING,    // Stripe refund created; awaiting async confirmation via webhook
    SUCCEEDED,  // Stripe confirmed the refund
    FAILED      // Stripe reported failure; compensation recorded for retry
}
