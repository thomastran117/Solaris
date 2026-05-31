package backend.controllers.impl.admin;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.admin.UpdateUserRoleRequest;
import backend.dtos.responses.general.MessageResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.core.User;
import backend.services.intf.AuthAuditLogger;
import backend.services.intf.auth.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for managing user accounts. Role assignment lives here rather
 * than on the public signup flow so it cannot be reached without an authenticated ADMIN.
 */
@RestController
@RequestMapping("/admin/users")
@RequireAuth(roles = {"ADMIN"})
public class UserAdminController {

    private final UserService userService;
    private final AuthAuditLogger audit;

    public UserAdminController(UserService userService, AuthAuditLogger audit) {
        this.userService = userService;
        this.audit = audit;
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<MessageResponse> updateRole(@PathVariable UUID userId,
                                                       @Valid @RequestBody UpdateUserRoleRequest request) {
        try {
            User updated = userService.setRole(userId, request.getRole());
            audit.log(AuthAuditLogger.Event.ROLE_CHANGED, updated.getId().toString(),
                    "new=" + updated.getRole());
            return ResponseEntity.ok(new MessageResponse(
                    "Role for user " + updated.getId() + " set to " + updated.getRole()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
