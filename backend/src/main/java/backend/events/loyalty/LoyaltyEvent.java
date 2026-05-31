package backend.events.loyalty;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = LoyaltyEvent.PointsEarned.class,       name = "POINTS_EARNED"),
        @JsonSubTypes.Type(value = LoyaltyEvent.TierUpgraded.class,       name = "TIER_UPGRADED"),
        @JsonSubTypes.Type(value = LoyaltyEvent.PointsExpiringSoon.class, name = "POINTS_EXPIRING_SOON"),
})
public sealed interface LoyaltyEvent {

    record PointsEarned(
            UUID userId,
            UUID companyId,
            long pointsDelta,
            String transactionType,
            long newBalance
    ) implements LoyaltyEvent {}

    record TierUpgraded(
            UUID userId,
            UUID companyId,
            String newTierName,
            String previousTierName
    ) implements LoyaltyEvent {}

    record PointsExpiringSoon(
            UUID userId,
            UUID companyId,
            long pointsExpiring,
            Instant expiresAt
    ) implements LoyaltyEvent {}
}
