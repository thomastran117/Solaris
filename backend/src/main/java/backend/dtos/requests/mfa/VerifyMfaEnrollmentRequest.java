package backend.dtos.requests.mfa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyMfaEnrollmentRequest(
        // Every enrollment code path (email OTP, SMS OTP, TOTP) produces exactly 6 digits.
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
        String code
) {}
