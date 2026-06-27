package backend.dtos.responses.mfa;

public record TotpEnrollmentResponse(
        String secret,
        String otpAuthUri
) {}
