package backend.dtos.responses.loyalty;

import java.time.Instant;

public record LoyaltyExpiryWarningResponse(long pointsExpiringSoon, Instant nextExpiryAt) {}
