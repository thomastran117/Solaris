package backend.models.enums;

/**
 * Result of a chargeback case. {@link #PENDING} until Stripe closes the dispute.
 *
 * <p>Stripe has no distinct "accepted" status — accepting a dispute simply closes it as
 * {@code lost}. The two are told apart by {@code evidence_details.has_evidence}: a loss with no
 * evidence submitted is a concession, not a ruling against us.
 */
public enum DisputeOutcome {
    /** Still open, or closed with a status we could not classify. */
    PENDING,
    /** Stripe ruled in our favour. */
    WON,
    /** Stripe ruled against us after we submitted evidence. */
    LOST,
    /** We conceded — closed as lost with no evidence submitted. */
    ACCEPTED
}
