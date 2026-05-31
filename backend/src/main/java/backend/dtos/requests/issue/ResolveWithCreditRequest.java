package backend.dtos.requests.issue;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveWithCreditRequest(
        /** Credit amount in cents. Must be > 0. */
        @NotNull @Min(1) Long amountCents,
        @SafeText @Size(max = 500) String reason
) {}
