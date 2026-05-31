package backend.dtos.requests.subscription;

import backend.models.enums.BillingInterval;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Partial-update DTO for a subscription. Any non-null field is applied.
 * When {@code productId} or {@code billingInterval} / {@code intervalCount} changes,
 * a new Stripe Price is created and the subscription item is swapped.
 */
@Getter
@Setter
public class UpdateSubscriptionRequest {

    private UUID productId;

    private UUID variantId;

    @Min(1)
    @Max(999)
    private Integer quantity;

    private BillingInterval billingInterval;

    @Min(1)
    @Max(12)
    private Integer intervalCount;
}
