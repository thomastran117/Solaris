package backend.models.enums;

/**
 * Category of a {@code DisputeEvidence} entry. Mirrors the groupings Stripe's evidence form
 * expects, so support staff can copy each block into the matching field.
 */
public enum DisputeEvidenceType {
    /** Order contents, totals, and the shipping address snapshot. */
    ORDER_DETAILS,
    /** Carrier, tracking number, and tracking checkpoints. */
    TRACKING,
    /** Proof of delivery. */
    DELIVERY_CONFIRMATION,
    /** Support ticket thread with the customer. */
    CUSTOMER_COMMUNICATION,
    /** Anything added manually by staff. */
    OTHER
}
