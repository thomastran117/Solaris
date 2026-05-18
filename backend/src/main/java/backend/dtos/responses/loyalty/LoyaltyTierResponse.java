package backend.dtos.responses.loyalty;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class LoyaltyTierResponse {
    private UUID id;
    private UUID companyId;
    private String name;
    private long minPoints;
    private BigDecimal earnMultiplier;
    private String perksJson;
    private String badgeColor;
    private int displayOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
