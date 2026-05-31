package backend.dtos.responses.loyalty;

public record LoyaltyReferralResponse(
        String referralCode,
        long totalReferrals,
        long convertedReferrals,
        long pointsEarnedFromReferrals
) {}
