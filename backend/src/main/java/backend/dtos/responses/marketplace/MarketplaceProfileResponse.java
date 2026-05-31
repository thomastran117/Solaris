package backend.dtos.responses.marketplace;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class MarketplaceProfileResponse {
    private UUID id;
    private UUID companyId;
    private String companyName;
    private String slug;
    private UUID defaultCommissionPolicyId;
    private String payoutSchedule;
    private int holdPeriodDays;
    private String defaultCurrency;
    private boolean acceptingApplications;
    private Instant createdAt;
    private Instant updatedAt;
}
