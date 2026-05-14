package backend.services.intf.company;

import backend.dtos.requests.company.InviteTeamMemberRequest;
import backend.dtos.requests.company.UpdateTeamMemberRoleRequest;
import backend.dtos.responses.company.CompanyRoleResponse;
import backend.dtos.responses.company.TeamInvitePreviewResponse;
import backend.dtos.responses.company.TeamMembershipResponse;

import java.util.List;

public interface CompanyMembershipService {

    /** Owner sends an invite. Returns the new pending membership. */
    TeamMembershipResponse inviteMember(long companyId, long inviterUserId, InviteTeamMemberRequest request);

    /** List active + pending members of a company (owner-only). */
    List<TeamMembershipResponse> listMembers(long companyId, long requestingUserId);

    /** Promote/demote between MANAGER and EMPLOYEE. */
    TeamMembershipResponse updateMemberRole(
            long companyId, long membershipId, long requestingUserId, UpdateTeamMemberRoleRequest request);

    /** Revoke a member (sets status to REVOKED). */
    void revokeMember(long companyId, long membershipId, long requestingUserId);

    /** Look up an invite by token (public). */
    TeamInvitePreviewResponse previewInvite(String token);

    /** Accept an invite as the current user. */
    TeamMembershipResponse acceptInvite(String token, long acceptingUserId);

    /** Return the current user's role + capabilities in a company, or empty if no access. */
    CompanyRoleResponse describeMyAccess(long companyId, long userId);
}
