package backend.models.enums;

/**
 * Lifecycle of a B2B quote (Feature 12).
 *
 * <pre>
 *   PENDING_VENDOR  buyer submitted; awaiting vendor review
 *   PENDING_BUYER   vendor approved/counter-proposed; awaiting buyer decision
 *   CONVERTED       an Order has been created from this quote
 *   REJECTED        buyer rejected the vendor's terms
 *   EXPIRED         passed expiresAt before the buyer acted
 * </pre>
 *
 * <p>v1 accepts directly from PENDING_BUYER to CONVERTED, so there is no intermediate ACCEPTED
 * state; DRAFT (saved-but-unsubmitted quotes) is likewise not part of v1. Re-add either when those
 * flows are built.
 */
public enum QuoteStatus {
    PENDING_VENDOR,
    PENDING_BUYER,
    REJECTED,
    EXPIRED,
    CONVERTED
}
