package backend.dtos.requests.support;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketMessageRequest(
        @NotBlank @SafeText @Size(max = 4000) String body
) {}
