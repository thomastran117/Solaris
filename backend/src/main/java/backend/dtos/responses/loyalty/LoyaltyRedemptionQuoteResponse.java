package backend.dtos.responses.loyalty;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoyaltyRedemptionQuoteResponse {
    private UUID userId;
    private UUID companyId;
    private int pointsToRedeem;
    private long discountCents;
    private long currentBalance;
    private long balanceAfterRedemption;
    private boolean valid;
    private String invalidReason;
}
