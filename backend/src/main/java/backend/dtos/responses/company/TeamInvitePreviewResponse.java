package backend.dtos.responses.company;

import backend.models.enums.CompanyRole;

import java.time.Instant;

public record TeamInvitePreviewResponse(
        String companyName,
        String invitedEmail,
        CompanyRole role,
        String inviterDisplayName,
        Instant expiresAt
) {}
