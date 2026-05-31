package backend.dtos.responses.order;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderStatusHistoryResponse(
        UUID id,
        String eventType,
        String status,
        Instant occurredAt,
        UUID actorId,
        String note
) {}
