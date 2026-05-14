package backend.dtos.responses.company;

import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyRole;

import java.util.Set;

public record CompanyRoleResponse(
        CompanyRole role,
        Set<CompanyCapability> capabilities
) {}
