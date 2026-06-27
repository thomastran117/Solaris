package backend.dtos.responses.mfa;

import backend.models.enums.MfaType;

import java.time.Instant;
import java.util.UUID;

public record MfaCredentialResponse(
        UUID id,
        MfaType type,
        boolean verified,
        boolean enabled,
        String target,
        Instant enrolledAt
) {}
