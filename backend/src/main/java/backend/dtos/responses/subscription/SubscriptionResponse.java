package backend.dtos.responses.subscription;

import backend.models.enums.BillingInterval;
import backend.models.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private UUID id;
    private SubscriptionStatus status;
    private BillingInterval billingInterval;
    private int intervalCount;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private Instant nextBillingAt;
    private Instant pausedAt;
    private Instant cancelledAt;
    private boolean skipNextCycle;
    private boolean cancelAtPeriodEnd;
    private String currency;
    private long unitAmountCents;
    private ShippingAddressResponse shippingAddress;
    private List<SubscriptionItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}
