package backend.controllers.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.profile.UpdateProfileRequest;
import backend.dtos.responses.profile.ProfileResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.ProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/profile")
@RequireAuth
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("")
    public ResponseEntity<ProfileResponse> getProfile() {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(profileService.getProfile(userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("")
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        try {
            long userId = resolveUserId();
            ProfileResponse response = profileService.updateProfile(
                    userId,
                    request.getFirstName(),
                    request.getLastName(),
                    request.getPhoneNumber(),
                    request.getAddress()
            );
            return ResponseEntity.ok(response);
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
