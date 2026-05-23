package backend.dtos.responses.premium;

import backend.models.enums.UserTier;

import java.time.Instant;

public record PremiumStatusResponse(
        UserTier tier,
        Instant premiumExpiresAt
) {}
