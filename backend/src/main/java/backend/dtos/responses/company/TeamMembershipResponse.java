package backend.dtos.responses.company;

import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;

import java.time.Instant;

public record TeamMembershipResponse(
        long id,
        Long userId,
        String displayName,
        String email,
        CompanyRole role,
        CompanyMembershipStatus status,
        Instant invitedAt,
        Instant acceptedAt
) {}
