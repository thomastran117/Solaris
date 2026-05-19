package backend.annotations.requireAuth;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequireAuthAspectTest {

    private RequireAuthAspect aspect;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() throws Throwable {
        aspect = new RequireAuthAspect();
        joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── authentication checks ────────────────────────────────────────────────

    @Test
    void enforceRequireAuth_noAuthentication_throwsAccessDenied() {
        SecurityContextHolder.clearContext(); // no auth set

        assertThrows(AccessDeniedException.class,
                () -> aspect.enforceRequireAuth(joinPoint, noRolesAnnotation()));
    }

    @Test
    void enforceRequireAuth_notAuthenticated_throwsAccessDenied() {
        // isAuthenticated() returns false
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(AccessDeniedException.class,
                () -> aspect.enforceRequireAuth(joinPoint, noRolesAnnotation()));
    }

    @Test
    void enforceRequireAuth_authenticated_noRoleConstraint_proceeds() throws Throwable {
        authenticateAs("ROLE_USER");

        Object result = aspect.enforceRequireAuth(joinPoint, noRolesAnnotation());

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    // ─── role checks ─────────────────────────────────────────────────────────

    @Test
    void enforceRequireAuth_hasRequiredRole_proceeds() throws Throwable {
        authenticateAs("ROLE_ADMIN");

        Object result = aspect.enforceRequireAuth(joinPoint, rolesAnnotation("ADMIN"));

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    void enforceRequireAuth_missingRequiredRole_throwsAccessDenied() {
        authenticateAs("ROLE_USER");

        assertThrows(AccessDeniedException.class,
                () -> aspect.enforceRequireAuth(joinPoint, rolesAnnotation("ADMIN")));
        verifyNoInteractions(joinPoint);
    }

    @Test
    void enforceRequireAuth_roleWithoutPrefix_isAutoExpanded() throws Throwable {
        // "ADMIN" in annotation → checked as "ROLE_ADMIN"
        authenticateAs("ROLE_ADMIN");

        Object result = aspect.enforceRequireAuth(joinPoint, rolesAnnotation("ADMIN"));

        assertEquals("ok", result);
    }

    @Test
    void enforceRequireAuth_roleAlreadyHasPrefix_isNotDoubled() throws Throwable {
        // "ROLE_MANAGER" in annotation → stays "ROLE_MANAGER", not "ROLE_ROLE_MANAGER"
        authenticateAs("ROLE_MANAGER");

        Object result = aspect.enforceRequireAuth(joinPoint, rolesAnnotation("ROLE_MANAGER"));

        assertEquals("ok", result);
    }

    @Test
    void enforceRequireAuth_anyOfMultipleRoles_proceeds() throws Throwable {
        authenticateAs("ROLE_SUPPORT");

        Object result = aspect.enforceRequireAuth(joinPoint, rolesAnnotation("ADMIN", "SUPPORT"));

        assertEquals("ok", result);
    }

    @Test
    void enforceRequireAuth_blankRoleInList_isIgnoredNotMatched() throws Throwable {
        // A blank role entry must not accidentally grant access
        authenticateAs("ROLE_USER");
        // roles = ["", "ADMIN"] — blank is filtered, user lacks ADMIN → denied
        assertThrows(AccessDeniedException.class,
                () -> aspect.enforceRequireAuth(joinPoint, rolesAnnotation("", "ADMIN")));
    }

    // ─── annotation resolution (class-level annotation) ───────────────────────

    @Test
    void enforceRequireAuth_nullRequireAuthParam_resolvesFromMethod() throws Throwable {
        authenticateAs("ROLE_USER");

        // Set up joinPoint to return a method annotated with @RequireAuth (no roles)
        Method method = AnnotatedHelper.class.getDeclaredMethod("noRoles");
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(sig);

        // passing null for requireAuth forces resolution from the joinPoint
        Object result = aspect.enforceRequireAuth(joinPoint, null);

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    void enforceRequireAuth_nullRequireAuthParam_resolvesFromClass() throws Throwable {
        authenticateAs("ROLE_USER");

        // Method has no @RequireAuth; class does (via AnnotatedClass)
        Method method = AnnotatedClass.class.getDeclaredMethod("noAnnotation");
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(sig);

        Object result = aspect.enforceRequireAuth(joinPoint, null);

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, authorities));
    }

    private RequireAuth noRolesAnnotation() {
        RequireAuth a = mock(RequireAuth.class);
        when(a.roles()).thenReturn(new String[]{});
        return a;
    }

    private RequireAuth rolesAnnotation(String... roles) {
        RequireAuth a = mock(RequireAuth.class);
        when(a.roles()).thenReturn(roles);
        return a;
    }

    // Target classes used for annotation resolution tests

    private static class AnnotatedHelper {
        @RequireAuth
        void noRoles() {}
    }

    @RequireAuth
    private static class AnnotatedClass {
        void noAnnotation() {}
    }
}
