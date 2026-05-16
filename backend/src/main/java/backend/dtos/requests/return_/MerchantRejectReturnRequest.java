package backend.dtos.requests.return_;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantRejectReturnRequest(
        @SafeText @NotBlank @Size(max = 1000) String merchantNote
) {}
