package backend.dtos.responses.company;

import java.util.UUID;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;

import java.time.Instant;

public record TeamMembershipResponse(
        UUID id,
        UUID userId,
        String displayName,
        String email,
        CompanyRole role,
        CompanyMembershipStatus status,
        Instant invitedAt,
        Instant acceptedAt
) {}
