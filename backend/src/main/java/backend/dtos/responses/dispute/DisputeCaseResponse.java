package backend.dtos.responses.dispute;

import backend.models.core.DisputeCase;
import backend.models.enums.DisputeOutcome;
import backend.models.enums.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisputeCaseResponse {
    private UUID id;
    /** Null when the disputed charge could not be mapped to an order. */
    private UUID orderId;
    private String stripeDisputeId;
    private String stripeChargeId;
    private long amountCents;
    private String currency;
    private String reason;
    private DisputeStatus status;
    private DisputeOutcome outcome;
    private String stripeStatus;
    private Instant evidenceDeadline;
    private Instant closedAt;
    private long evidenceCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static DisputeCaseResponse from(DisputeCase c, long evidenceCount) {
        return new DisputeCaseResponse(
                c.getId(),
                // Read-only FK column, not the lazy association: Order maps @Id on the field, so
                // getOrder().getId() would initialise the proxy and fire a SELECT per row here.
                c.resolveOrderId(),
                c.getStripeDisputeId(),
                c.getStripeChargeId(),
                c.getAmountCents(),
                c.getCurrency(),
                c.getReason(),
                c.getStatus(),
                c.getOutcome(),
                c.getStripeStatus(),
                c.getEvidenceDeadline(),
                c.getClosedAt(),
                evidenceCount,
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
