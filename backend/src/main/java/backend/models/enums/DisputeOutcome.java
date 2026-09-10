package backend.models.enums;

/**
 * Result of a chargeback case. {@link #PENDING} until Stripe closes the dispute.
 *
 * <p>Stripe has no distinct "accepted" status — conceding a dispute simply closes it as
 * {@code lost}, and so does letting the evidence deadline expire unanswered. Because the provider
 * cannot tell those apart, {@link #ACCEPTED} is <b>never inferred</b>: every provider
 * {@code lost} maps to {@link #LOST}. Inferring acceptance from
 * {@code evidence_details.has_evidence == false} would relabel a missed deadline as a deliberate
 * business decision and hide the failure this feature exists to prevent.
 */
public enum DisputeOutcome {
    /** Still open, or closed with a status we could not classify. */
    PENDING,
    /** Stripe ruled in our favour. */
    WON,
    /** Stripe closed the dispute against us, however that came about. */
    LOST,
    /**
     * Reserved for an explicit, locally recorded decision to concede a dispute. No such action
     * exists in v1, so nothing currently produces this value.
     */
    ACCEPTED
}
