package backend.models.enums;

/**
 * Lifecycle of a B2B quote (Feature 12).
 *
 * <pre>
 *   PENDING_VENDOR  buyer submitted; awaiting vendor review
 *   PENDING_BUYER   vendor approved/counter-proposed; awaiting buyer decision
 *   ACCEPTED        buyer accepted (transient; immediately CONVERTED once the order is created)
 *   CONVERTED       an Order has been created from this quote
 *   REJECTED        buyer rejected the vendor's terms
 *   EXPIRED         passed expiresAt before the buyer acted
 * </pre>
 */
public enum QuoteStatus {
    DRAFT,
    PENDING_VENDOR,
    PENDING_BUYER,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CONVERTED
}
