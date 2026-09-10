package backend.models.enums;

/**
 * Lifecycle of a chargeback case, collapsed from Stripe's finer-grained dispute status.
 *
 * <p>Stripe distinguishes "warning" (inquiry, no funds withdrawn yet) from real disputes; both
 * map onto the same three states here because the support workflow is identical.
 */
public enum DisputeStatus {
    /** Evidence is needed. Maps from {@code needs_response} / {@code warning_needs_response}. */
    OPEN,
    /** Evidence submitted, awaiting the issuer. Maps from {@code under_review} / {@code warning_under_review}. */
    UNDER_REVIEW,
    /** Terminal. Maps from {@code won} / {@code lost} / {@code warning_closed}. */
    CLOSED
}
