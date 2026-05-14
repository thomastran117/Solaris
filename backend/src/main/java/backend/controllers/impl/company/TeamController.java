package backend.controllers.impl.company;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.company.InviteTeamMemberRequest;
import backend.dtos.requests.company.UpdateTeamMemberRoleRequest;
import backend.dtos.responses.company.CompanyRoleResponse;
import backend.dtos.responses.company.TeamInvitePreviewResponse;
import backend.dtos.responses.company.TeamMembershipResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.company.CompanyMembershipService;

import jakarta.validation.Valid;

import java.util.List;

@RestController
public class TeamController {

    private final CompanyMembershipService membershipService;

    public TeamController(CompanyMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping("/companies/{companyId}/team/invites")
    @RequireAuth
    public ResponseEntity<TeamMembershipResponse> invite(
            @PathVariable long companyId,
            @Valid @RequestBody InviteTeamMemberRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(membershipService.inviteMember(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/companies/{companyId}/team")
    @RequireAuth
    public ResponseEntity<List<TeamMembershipResponse>> list(@PathVariable long companyId) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(membershipService.listMembers(companyId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/companies/{companyId}/team/{membershipId}")
    @RequireAuth
    public ResponseEntity<TeamMembershipResponse> updateRole(
            @PathVariable long companyId,
            @PathVariable long membershipId,
            @Valid @RequestBody UpdateTeamMemberRoleRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(
                    membershipService.updateMemberRole(companyId, membershipId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/companies/{companyId}/team/{membershipId}")
    @RequireAuth
    public ResponseEntity<Void> revoke(
            @PathVariable long companyId,
            @PathVariable long membershipId) {
        try {
            long userId = resolveUserId();
            membershipService.revokeMember(companyId, membershipId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/companies/{companyId}/me")
    @RequireAuth
    public ResponseEntity<CompanyRoleResponse> describeMyAccess(@PathVariable long companyId) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(membershipService.describeMyAccess(companyId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/team-invites/{token}")
    public ResponseEntity<TeamInvitePreviewResponse> preview(@PathVariable String token) {
        try {
            return ResponseEntity.ok(membershipService.previewInvite(token));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/team-invites/{token}/accept")
    @RequireAuth
    public ResponseEntity<TeamMembershipResponse> accept(@PathVariable String token) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(membershipService.acceptInvite(token, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
