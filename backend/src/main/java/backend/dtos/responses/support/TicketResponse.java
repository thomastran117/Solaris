package backend.dtos.responses.support;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TicketResponse {
    private UUID id;
    private UUID customerId;
    private String customerName;
    private UUID openedById;
    private UUID assignedToId;
    private String assignedToName;
    private UUID orderId;
    private String subject;
    private String description;
    private String status;
    private String priority;
    private String category;
    private List<TicketMessageResponse> messages;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
    private Instant closedAt;
}
