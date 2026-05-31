package backend.utilities;

import backend.exceptions.http.ForbiddenException;
import backend.models.core.User;
import backend.models.enums.UserRole;

import java.util.Set;

public final class SecurityUtils {

    private static final Set<UserRole> STAFF_ROLES = Set.of(
            UserRole.SUPPORT, UserRole.MODERATOR, UserRole.ADMIN);

    private SecurityUtils() {}

    public static boolean isStaff(User user) {
        return STAFF_ROLES.contains(user.getRole());
    }

    public static void requireStaff(User user) {
        if (!isStaff(user)) {
            throw new ForbiddenException();
        }
    }

    public static void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException();
        }
    }
}
