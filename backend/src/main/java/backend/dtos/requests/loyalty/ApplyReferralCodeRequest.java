package backend.dtos.requests.loyalty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyReferralCodeRequest(
        @NotBlank @Size(max = 12) String code
) {}
