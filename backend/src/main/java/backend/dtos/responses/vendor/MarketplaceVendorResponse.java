package backend.dtos.responses.vendor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class MarketplaceVendorResponse {
    private UUID id;
    private UUID marketplaceId;
    private String marketplaceName;
    private UUID vendorCompanyId;
    private String vendorCompanyName;
    private String status;
    private String tier;
    private String onboardingStep;
    private UUID commissionPolicyId;
    private String stripeConnectStatus;
    private boolean chargesEnabled;
    private boolean payoutsEnabled;
    private Instant appliedAt;
    private Instant approvedAt;
    private Instant suspendedAt;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;
}
