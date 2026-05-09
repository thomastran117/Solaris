package backend.dtos.responses.auth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
