package backend.dtos.requests.credit;

import backend.annotations.safeText.SafeText;
import backend.models.enums.CreditEntryType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record IssueCreditRequest(
        @NotNull @Min(1) Long amountCents,
        @NotNull CreditEntryType type,
        @SafeText @Size(max = 500) String reason,
        Instant expiresAt
) {}
