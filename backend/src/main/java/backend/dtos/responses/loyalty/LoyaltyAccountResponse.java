package backend.dtos.responses.loyalty;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class LoyaltyAccountResponse {
    private UUID id;
    private UUID userId;
    private UUID companyId;
    private long pointsBalance;
    private long lifetimePoints;
    private UUID currentTierId;
    private String currentTierName;
    private Instant tierUpdatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
