package backend.services.intf.payments;

import backend.dtos.requests.dispute.AddEvidenceRequest;
import backend.dtos.responses.dispute.DisputeCaseDetailResponse;
import backend.dtos.responses.dispute.DisputeCaseResponse;
import backend.dtos.responses.dispute.DisputeEvidenceResponse;
import backend.dtos.responses.general.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chargeback handling. Stripe opens a dispute against a charge and starts a 7–21 day clock to
 * submit evidence; an unanswered dispute is lost automatically. This service records the case,
 * alerts support, and pre-populates evidence from data the platform already holds.
 *
 * <p>Evidence is <b>not</b> submitted back to Stripe in v1 — support reviews and submits manually.
 *
 * <p>Provider-agnostic by design: callers flatten the webhook payload into {@link StripeDispute},
 * so no implementation here depends on the Stripe SDK.
 */
public interface DisputeService {

    /**
     * A dispute as reported by the payment provider.
     *
     * @param disputeId        provider dispute id ({@code dp_...}); the idempotency key
     * @param chargeId         the disputed charge
     * @param paymentIntentId  used to resolve the order; may be null
     * @param amountCents      disputed amount in the smallest currency unit
     * @param currency         ISO 4217 lowercase currency code
     * @param reason           raw provider reason code (e.g. {@code fraudulent})
     * @param stripeStatus     raw provider status (e.g. {@code needs_response}, {@code won})
     * @param evidenceDeadline when evidence must be submitted by; null when not supplied
     * @param hasEvidence      whether evidence was submitted — distinguishes a loss from a concession
     */
    record StripeDispute(
            String disputeId,
            String chargeId,
            String paymentIntentId,
            long amountCents,
            String currency,
            String reason,
            String stripeStatus,
            Instant evidenceDeadline,
            boolean hasEvidence
    ) {}

    /**
     * Opens a case for a new chargeback, pre-populates evidence, and alerts the support team.
     *
     * <p>Idempotent on {@code disputeId}: a repeat delivery returns the existing case and creates
     * neither a second case nor a second alert.
     */
    DisputeCaseResponse handleDisputeCreated(StripeDispute dispute);

    /** Syncs a status change from the provider. No-op for an unknown dispute id. */
    void handleDisputeUpdated(StripeDispute dispute);

    /** Records the final outcome and closes the case. No-op for an unknown dispute id. */
    void handleDisputeClosed(StripeDispute dispute);

    /** Open (non-closed) disputes, newest first. */
    PagedResponse<DisputeCaseResponse> getOpenDisputes(Pageable pageable);

    /** A single case with its full evidence pack. */
    DisputeCaseDetailResponse getDispute(UUID disputeCaseId);

    /** Every dispute raised against an order, newest first. */
    List<DisputeCaseResponse> getDisputesByOrder(UUID orderId);

    /** Appends a staff-authored evidence entry to an open or closed case. */
    DisputeEvidenceResponse addManualEvidence(UUID disputeCaseId, AddEvidenceRequest request, UUID staffUserId);
}
