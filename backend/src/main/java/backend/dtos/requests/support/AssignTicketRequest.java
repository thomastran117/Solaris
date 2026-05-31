package backend.dtos.requests.support;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record AssignTicketRequest(
        @NotNull UUID staffUserId
) {}
