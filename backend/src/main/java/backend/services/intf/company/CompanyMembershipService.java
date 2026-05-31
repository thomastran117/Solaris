package backend.services.intf.company;

import java.util.UUID;
import backend.dtos.requests.company.InviteTeamMemberRequest;
import backend.dtos.requests.company.UpdateTeamMemberRoleRequest;
import backend.dtos.responses.company.CompanyRoleResponse;
import backend.dtos.responses.company.TeamInvitePreviewResponse;
import backend.dtos.responses.company.TeamMembershipResponse;

import java.util.List;

public interface CompanyMembershipService {

    /** Owner sends an invite. Returns the new pending membership. */
    TeamMembershipResponse inviteMember(UUID companyId, UUID inviterUserId, InviteTeamMemberRequest request);

    /** List active + pending members of a company (owner-only). */
    List<TeamMembershipResponse> listMembers(UUID companyId, UUID requestingUserId);

    /** Promote/demote between MANAGER and EMPLOYEE. */
    TeamMembershipResponse updateMemberRole(
            UUID companyId, UUID membershipId, UUID requestingUserId, UpdateTeamMemberRoleRequest request);

    /** Revoke a member (sets status to REVOKED). */
    void revokeMember(UUID companyId, UUID membershipId, UUID requestingUserId);

    /** Look up an invite by token (public). */
    TeamInvitePreviewResponse previewInvite(String token);

    /** Accept an invite as the current user. */
    TeamMembershipResponse acceptInvite(String token, UUID acceptingUserId);

    /** Return the current user's role + capabilities in a company, or empty if no access. */
    CompanyRoleResponse describeMyAccess(UUID companyId, UUID userId);
}
