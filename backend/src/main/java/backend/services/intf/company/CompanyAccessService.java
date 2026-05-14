package backend.services.intf.company;

import backend.models.core.Company;
import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyRole;

import java.util.List;
import java.util.Optional;

public interface CompanyAccessService {

    /**
     * Loads the company and verifies the user's active role grants the capability.
     * Throws ForbiddenException if not.
     */
    Company require(long companyId, long userId, CompanyCapability capability);

    /**
     * Loads the company and verifies the user has any active membership.
     * Used for plain reads where any role suffices (e.g. fetching company identity).
     */
    Company requireAnyAccess(long companyId, long userId);

    /**
     * Returns the user's active role for this company, or empty if no active membership.
     */
    Optional<CompanyRole> resolveRole(long companyId, long userId);

    /**
     * Returns every company the user has an active membership in.
     */
    List<Company> listAccessibleCompanies(long userId);

    /**
     * True iff the role grants the capability. Pure lookup.
     */
    boolean roleHas(CompanyRole role, CompanyCapability capability);

    /**
     * Invalidates any cached role for this (companyId, userId). Called by the
     * membership service after invite-accept, role change, or revoke.
     */
    void invalidate(long companyId, long userId);
}
