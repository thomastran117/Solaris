package backend.controllers.impl.company;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.company.PatchFollowRequest;
import backend.dtos.responses.company.CompanyFollowResponse;
import backend.dtos.responses.company.FollowStatusResponse;
import backend.dtos.responses.company.FollowedCompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.company.CompanyFollowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
public class CompanyFollowController {

    private final CompanyFollowService followService;

    public CompanyFollowController(CompanyFollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/companies/{id}/follow")
    @RequireAuth
    public ResponseEntity<CompanyFollowResponse> follow(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(followService.follow(resolveUserId(), id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/companies/{id}/follow")
    @RequireAuth
    public ResponseEntity<Void> unfollow(@PathVariable UUID id) {
        try {
            followService.unfollow(resolveUserId(), id);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/companies/{id}/follow/status")
    public ResponseEntity<FollowStatusResponse> getStatus(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(followService.getStatus(resolveOptionalUserId(), id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/companies/{id}/follow")
    @RequireAuth
    public ResponseEntity<CompanyFollowResponse> patchFollow(
            @PathVariable UUID id,
            @Valid @RequestBody PatchFollowRequest request) {
        try {
            return ResponseEntity.ok(
                    followService.setNotifications(resolveUserId(), id, request.getNotificationsEnabled()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/me/following")
    @RequireAuth
    public ResponseEntity<PagedResponse<FollowedCompanyResponse>> listFollowing(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(followService.listFollowing(resolveUserId(), page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }

    private UUID resolveOptionalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID)) {
            return null;
        }
        return (UUID) auth.getPrincipal();
    }
}
