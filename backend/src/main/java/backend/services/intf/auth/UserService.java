package backend.services.intf.auth;

import backend.models.core.User;
import backend.models.enums.UserRole;

public interface UserService {
    User login(String email, String password);

    User signup(String email, String password);

    void activateUser(long userId);

    /**
     * Assign a role to an existing user. Used by admin endpoints to promote/demote.
     * Throws ResourceNotFoundException if no user matches {@code userId}.
     */
    User setRole(long userId, UserRole role);

    boolean changePassword(long id, String currentPassword, String newPassword);

    boolean delete(long id);

    User getUserByID(long id);

    long getID(String email);

    User loginOrSignupGoogle(String email);

    User loginOrSignupMicrosoft(String email);

    User loginOrSignupApple(String email);
}
