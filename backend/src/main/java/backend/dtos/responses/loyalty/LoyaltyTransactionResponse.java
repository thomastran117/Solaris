package backend.dtos.responses.loyalty;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class LoyaltyTransactionResponse {
    private UUID id;
    private UUID accountId;
    private UUID userId;
    private UUID companyId;
    private String type;
    private long pointsDelta;
    private long valueCents;
    private UUID sourceOrderId;
    private Instant expiresAt;
    private String reason;
    private Instant createdAt;
}
