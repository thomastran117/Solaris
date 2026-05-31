package backend.models.enums;

public enum ReturnStatus {
    REQUESTED,   // Buyer submitted or merchant initiated; awaiting processing
    APPROVED,    // Merchant approved; transient state recorded in audit trail
    REJECTED,    // Merchant rejected buyer request; terminal, no stock/refund changes
    COMPLETED,   // Stock restored (if elected) and refund issued (or waived); terminal success
    FAILED       // Refund or stock step failed; compensation pending for retry
}
