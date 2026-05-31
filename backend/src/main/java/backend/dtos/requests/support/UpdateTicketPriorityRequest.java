package backend.dtos.requests.support;

import backend.models.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketPriorityRequest(
        @NotNull TicketPriority priority
) {}
