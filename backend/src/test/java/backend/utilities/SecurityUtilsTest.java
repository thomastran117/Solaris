package backend.utilities;

import backend.exceptions.http.ForbiddenException;
import backend.models.core.User;
import backend.models.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    private User userWithRole(UserRole role) {
        User u = new User();
        u.setRole(role);
        return u;
    }

    // ── isStaff ───────────────────────────────────────────────────────────────

    @Test
    void isStaff_support_returnsTrue() {
        assertTrue(SecurityUtils.isStaff(userWithRole(UserRole.SUPPORT)));
    }

    @Test
    void isStaff_moderator_returnsTrue() {
        assertTrue(SecurityUtils.isStaff(userWithRole(UserRole.MODERATOR)));
    }

    @Test
    void isStaff_admin_returnsTrue() {
        assertTrue(SecurityUtils.isStaff(userWithRole(UserRole.ADMIN)));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"USER", "MERCHANT", "VENDOR_OWNER", "VENDOR_STAFF", "MARKETPLACE_OPERATOR"})
    void isStaff_nonStaffRoles_returnsFalse(UserRole role) {
        assertFalse(SecurityUtils.isStaff(userWithRole(role)));
    }

    // ── requireStaff ──────────────────────────────────────────────────────────

    @Test
    void requireStaff_admin_doesNotThrow() {
        assertDoesNotThrow(() -> SecurityUtils.requireStaff(userWithRole(UserRole.ADMIN)));
    }

    @Test
    void requireStaff_support_doesNotThrow() {
        assertDoesNotThrow(() -> SecurityUtils.requireStaff(userWithRole(UserRole.SUPPORT)));
    }

    @Test
    void requireStaff_moderator_doesNotThrow() {
        assertDoesNotThrow(() -> SecurityUtils.requireStaff(userWithRole(UserRole.MODERATOR)));
    }

    @Test
    void requireStaff_user_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> SecurityUtils.requireStaff(userWithRole(UserRole.USER)));
    }

    @Test
    void requireStaff_merchant_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> SecurityUtils.requireStaff(userWithRole(UserRole.MERCHANT)));
    }

    // ── requireAdmin ──────────────────────────────────────────────────────────

    @Test
    void requireAdmin_admin_doesNotThrow() {
        assertDoesNotThrow(() -> SecurityUtils.requireAdmin(userWithRole(UserRole.ADMIN)));
    }

    @Test
    void requireAdmin_support_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> SecurityUtils.requireAdmin(userWithRole(UserRole.SUPPORT)));
    }

    @Test
    void requireAdmin_moderator_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> SecurityUtils.requireAdmin(userWithRole(UserRole.MODERATOR)));
    }

    @Test
    void requireAdmin_user_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> SecurityUtils.requireAdmin(userWithRole(UserRole.USER)));
    }

    @Test
    void requireAdmin_merchant_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> SecurityUtils.requireAdmin(userWithRole(UserRole.MERCHANT)));
    }
}
