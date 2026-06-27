package backend.dtos.responses.mfa;

import backend.models.enums.MfaType;

import java.time.Instant;
import java.util.UUID;

public record MfaCredentialResponse(
        UUID id,
        MfaType type,
        boolean verified,
        String target,
        Instant enrolledAt
) {}
