package backend.controllers.impl.profile;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.notification.UpdateNotificationPreferencesRequest;
import backend.dtos.requests.preference.SetTrackingOptOutRequest;
import backend.dtos.responses.notification.NotificationPreferencesResponse;
import backend.dtos.responses.preference.UserPreferenceResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.profile.UserPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/preferences")
@RequireAuth
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    public UserPreferenceController(UserPreferenceService userPreferenceService) {
        this.userPreferenceService = userPreferenceService;
    }

    @GetMapping
    public ResponseEntity<UserPreferenceResponse> getPreferences() {
        try {
            UUID userId = resolveUserId();
            boolean optedOut = userPreferenceService.isTrackingOptedOut(userId);
            return ResponseEntity.ok(new UserPreferenceResponse(optedOut));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PutMapping("/tracking")
    public ResponseEntity<Void> setTracking(@Valid @RequestBody SetTrackingOptOutRequest request) {
        try {
            userPreferenceService.setTrackingOptOut(resolveUserId(), request.getOptOut());
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/notifications")
    public ResponseEntity<NotificationPreferencesResponse> getNotificationPreferences() {
        try {
            return ResponseEntity.ok(userPreferenceService.getNotificationPreferences(resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/notifications")
    public ResponseEntity<NotificationPreferencesResponse> updateNotificationPreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        try {
            return ResponseEntity.ok(userPreferenceService.updateNotificationPreferences(resolveUserId(), request));
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
}
