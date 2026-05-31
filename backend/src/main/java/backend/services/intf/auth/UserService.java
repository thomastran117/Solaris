package backend.services.intf.auth;

import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.other.OAuthUser;

import java.util.UUID;

public interface UserService {
    User login(String email, String password);

    User signup(String email, String password);

    void activateUser(UUID userId);

    /**
     * Assign a role to an existing user. Used by admin endpoints to promote/demote.
     * Throws ResourceNotFoundException if no user matches {@code userId}.
     */
    User setRole(UUID userId, UserRole role);

    boolean changePassword(UUID id, String currentPassword, String newPassword);

    boolean delete(UUID id);

    User getUserByID(UUID id);

    /**
     * Same as {@link #getUserByID} but also asserts the account is in an accessible state
     * (ACTIVE or INACTIVE). Throws ForbiddenException for suspended, deleted, or unverified accounts.
     * Use this in any token-issuing flow that loads a user by ID.
     */
    User getAccessibleUserByID(UUID id);

    UUID getID(String email);

    User loginOrSignupGoogle(OAuthUser oauthUser);

    User loginOrSignupMicrosoft(OAuthUser oauthUser);

    User loginOrSignupApple(OAuthUser oauthUser);
}
