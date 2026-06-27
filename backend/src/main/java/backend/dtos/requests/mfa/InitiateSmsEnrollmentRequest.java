package backend.dtos.requests.mfa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InitiateSmsEnrollmentRequest(
        @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be in E.164 format (e.g. +15551234567)")
        String phoneNumber
) {}
