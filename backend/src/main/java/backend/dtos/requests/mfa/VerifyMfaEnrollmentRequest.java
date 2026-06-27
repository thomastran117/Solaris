package backend.dtos.requests.mfa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyMfaEnrollmentRequest(
        @NotBlank
        @Size(min = 6, max = 8)
        @Pattern(regexp = "^\\d{6,8}$", message = "Code must be 6 to 8 digits")
        String code
) {}
