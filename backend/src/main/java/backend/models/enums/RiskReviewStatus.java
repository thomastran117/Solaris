package backend.models.enums;

/** Merchant review queue state for orders held by the fraud engine. */
public enum RiskReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
