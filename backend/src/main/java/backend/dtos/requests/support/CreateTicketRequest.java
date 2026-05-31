package backend.dtos.requests.support;

import backend.annotations.safeText.SafeText;
import backend.models.enums.TicketCategory;
import backend.models.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTicketRequest(
        @NotBlank @SafeText @Size(max = 200) String subject,
        @NotBlank @SafeText @Size(max = 2000) String description,
        @NotNull TicketCategory category,
        TicketPriority priority,
        /** Optional order to associate the ticket with. */
        UUID orderId,
        /** Staff-only: open a ticket on behalf of this customer. */
        UUID customerId
) {}
