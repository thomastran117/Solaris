package backend.dtos.responses.issue;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class OrderIssueResponse {
    private UUID id;
    private UUID orderId;
    private UUID ticketId;
    private UUID reportedById;
    private String type;
    private String state;
    private String resolution;
    private String description;
    private String rejectionReason;
    private UUID returnId;
    private UUID replacementOrderId;
    private UUID customerCreditId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
}
