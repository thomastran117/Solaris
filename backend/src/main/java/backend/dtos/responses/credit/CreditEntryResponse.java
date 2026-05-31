package backend.dtos.responses.credit;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class CreditEntryResponse {
    private UUID id;
    private long amountCents;
    private String currency;
    private String type;
    private String reason;
    private UUID issuedById;
    private UUID sourceTicketId;
    private UUID sourceOrderIssueId;
    private java.util.UUID redeemedOnOrderId;
    private Instant expiresAt;
    private Instant createdAt;
}
