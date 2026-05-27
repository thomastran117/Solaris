package backend.services.impl.auth;

import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.UnauthorizedException;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.models.other.OAuthUser;
import backend.repositories.UserRepository;
import backend.services.intf.AuthAuditLogger;
import backend.services.intf.auth.TokenService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthAuditLogger auditLogger;
    private TokenService tokenService;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditLogger = mock(AuthAuditLogger.class);
        tokenService = mock(TokenService.class);
        service = new UserServiceImpl(userRepository, passwordEncoder, auditLogger, tokenService);
    }

    // ─── login ───────────────────────────────────────────────────────────────

    @Test
    void login_validActiveUser_returnsUser() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", user.getPassword())).thenReturn(true);

        User result = service.login("a@b.com", "pw");

        assertSame(user, result);
    }

    @Test
    void login_validInactiveUser_returnsUser() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.INACTIVE);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", user.getPassword())).thenReturn(true);

        User result = service.login("a@b.com", "pw");

        assertSame(user, result);
    }

    @Test
    void login_userNotFound_throwsUnauthorized() {
        when(userRepository.findByEmail("missing@b.com")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> service.login("missing@b.com", "pw"));
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> service.login("a@b.com", "wrong"));
    }

    @Test
    void login_pendingVerification_throwsForbidden() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.PENDING_VERIFICATION);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", user.getPassword())).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> service.login("a@b.com", "pw"));
    }

    @Test
    void login_suspended_throwsForbidden() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.SUSPENDED);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", user.getPassword())).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> service.login("a@b.com", "pw"));
    }

    @Test
    void login_deleted_throwsForbidden() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.DELETED);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", user.getPassword())).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> service.login("a@b.com", "pw"));
    }

    // ─── signup ──────────────────────────────────────────────────────────────

    @Test
    void signup_newEmail_savesWithPendingVerificationAndEncodedPassword() {
        when(userRepository.findByEmail("new@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pw")).thenReturn("encoded-pw");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.signup("new@b.com", "pw");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new@b.com", captor.getValue().getEmail());
        assertEquals("encoded-pw", captor.getValue().getPassword());
        assertEquals(UserStatus.PENDING_VERIFICATION, captor.getValue().getStatus());
    }

    @Test
    void signup_setsRoleToUser() {
        when(userRepository.findByEmail("new@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.signup("new@b.com", "pw");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(UserRole.USER, captor.getValue().getRole());
    }

    @Test
    void signup_duplicateEmail_throwsConflict() {
        when(userRepository.findByEmail("dup@b.com"))
                .thenReturn(Optional.of(makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE)));

        assertThrows(ConflictException.class,
                () -> service.signup("dup@b.com", "pw"));
        verify(userRepository, never()).save(any());
    }

    // ─── activateUser ────────────────────────────────────────────────────────

    @Test
    void activateUser_setsStatusToActive() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.PENDING_VERIFICATION);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));

        service.activateUser(TestIds.uuid(1));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(UserStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void activateUser_notFound_throwsResourceNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.activateUser(TestIds.uuid(99)));
    }

    // ─── setRole ─────────────────────────────────────────────────────────────

    @Test
    void setRole_updatesUserRole() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setRole(TestIds.uuid(1), UserRole.ADMIN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(UserRole.ADMIN, captor.getValue().getRole());
    }

    @Test
    void setRole_nullRole_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> service.setRole(TestIds.uuid(1), null));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void setRole_notFound_throwsResourceNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.setRole(TestIds.uuid(99), UserRole.ADMIN));
    }

    // ─── getUserByID / getID ─────────────────────────────────────────────────

    @Test
    void getUserByID_returnsUser() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));

        assertSame(user, service.getUserByID(TestIds.uuid(1)));
    }

    @Test
    void getUserByID_notFound_throwsResourceNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getUserByID(TestIds.uuid(99)));
    }

    @Test
    void getID_returnsUuid() {
        User user = makeUser(TestIds.uuid(5), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertEquals(TestIds.uuid(5), service.getID("a@b.com"));
    }

    @Test
    void getID_notFound_throwsResourceNotFound() {
        when(userRepository.findByEmail("missing@b.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getID("missing@b.com"));
    }

    // ─── changePassword ──────────────────────────────────────────────────────

    @Test
    void changePassword_correctCurrentPassword_encodesAndSavesNewPassword() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        user.setPassword("hashed-old");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hashed-old")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("hashed-new");

        boolean result = service.changePassword(TestIds.uuid(1), "old", "new");

        assertTrue(result);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed-new", captor.getValue().getPassword());
    }

    @Test
    void changePassword_success_revokesAllRefreshTokens() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        user.setPassword("hashed-old");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hashed-old")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("hashed-new");

        service.changePassword(TestIds.uuid(1), "old", "new");

        verify(tokenService).revokeAllRefreshTokensForUser(TestIds.uuid(1));
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsForbidden() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        user.setPassword("hashed-old");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-old")).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> service.changePassword(TestIds.uuid(1), "wrong", "new"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_notFound_throwsResourceNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.changePassword(TestIds.uuid(99), "old", "new"));
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_deletesUser() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));

        boolean result = service.delete(TestIds.uuid(1));

        assertTrue(result);
        verify(userRepository).delete(user);
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.delete(TestIds.uuid(99)));
        verify(userRepository, never()).delete(any());
    }

    // ─── loginOrSignupGoogle ─────────────────────────────────────────────────

    private static final OAuthUser GOOGLE_OAUTH = new OAuthUser("g-sub-123", "g@gmail.com", "Google User", "google");

    @Test
    void loginOrSignupGoogle_newUser_createsWithSubAndNullPassword() {
        when(userRepository.findByGoogleId("g-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("g@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.loginOrSignupGoogle(GOOGLE_OAUTH);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNull(captor.getValue().getPassword());
        assertEquals(UserRole.USER, captor.getValue().getRole());
        assertEquals("g@gmail.com", captor.getValue().getEmail());
        assertEquals("g-sub-123", captor.getValue().getGoogleId());
        assertEquals("g@gmail.com", result.getEmail());
    }

    @Test
    void loginOrSignupGoogle_knownSub_returnsUserWithoutAudit() {
        User user = makeUser(TestIds.uuid(2), UserRole.USER, UserStatus.ACTIVE);
        user.setGoogleId("g-sub-123");
        when(userRepository.findByGoogleId("g-sub-123")).thenReturn(Optional.of(user));

        User result = service.loginOrSignupGoogle(GOOGLE_OAUTH);

        assertSame(user, result);
        verify(userRepository, never()).findByEmail(any());
        verify(auditLogger, never()).log(any(), any(), any());
    }

    @Test
    void loginOrSignupGoogle_emailMatchNoSub_linksSubAndLogsAudit() {
        User user = makeUser(TestIds.uuid(2), UserRole.USER, UserStatus.ACTIVE);
        user.setGoogleId(null);
        when(userRepository.findByGoogleId("g-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("g@gmail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loginOrSignupGoogle(GOOGLE_OAUTH);

        assertEquals("g-sub-123", user.getGoogleId());
        verify(auditLogger).log(eq(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED),
                eq(TestIds.uuid(2).toString()), eq("provider=google"));
    }

    @Test
    void loginOrSignupGoogle_emailMatchDifferentSub_throwsUnauthorized() {
        User user = makeUser(TestIds.uuid(2), UserRole.USER, UserStatus.ACTIVE);
        user.setGoogleId("other-sub");
        when(userRepository.findByGoogleId("g-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("g@gmail.com")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> service.loginOrSignupGoogle(GOOGLE_OAUTH));
    }

    @Test
    void loginOrSignupGoogle_suspendedUser_throwsForbidden() {
        User user = makeUser(TestIds.uuid(2), UserRole.USER, UserStatus.SUSPENDED);
        user.setGoogleId("g-sub-123");
        when(userRepository.findByGoogleId("g-sub-123")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> service.loginOrSignupGoogle(GOOGLE_OAUTH));
    }

    // ─── loginOrSignupMicrosoft ───────────────────────────────────────────────

    private static final OAuthUser MS_OAUTH = new OAuthUser("ms-sub-456", "m@outlook.com", "MS User", "microsoft");

    @Test
    void loginOrSignupMicrosoft_newUser_createsWithSubAndNullPassword() {
        when(userRepository.findByMicrosoftId("ms-sub-456")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("m@outlook.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loginOrSignupMicrosoft(MS_OAUTH);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNull(captor.getValue().getPassword());
        assertEquals("ms-sub-456", captor.getValue().getMicrosoftId());
    }

    @Test
    void loginOrSignupMicrosoft_knownSub_returnsUserWithoutAudit() {
        User user = makeUser(TestIds.uuid(3), UserRole.USER, UserStatus.ACTIVE);
        user.setMicrosoftId("ms-sub-456");
        when(userRepository.findByMicrosoftId("ms-sub-456")).thenReturn(Optional.of(user));

        User result = service.loginOrSignupMicrosoft(MS_OAUTH);

        assertSame(user, result);
        verify(auditLogger, never()).log(any(), any(), any());
    }

    @Test
    void loginOrSignupMicrosoft_emailMatchNoSub_linksSubAndLogsAudit() {
        User user = makeUser(TestIds.uuid(3), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findByMicrosoftId("ms-sub-456")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("m@outlook.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loginOrSignupMicrosoft(MS_OAUTH);

        assertEquals("ms-sub-456", user.getMicrosoftId());
        verify(auditLogger).log(eq(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED),
                eq(TestIds.uuid(3).toString()), eq("provider=microsoft"));
    }

    @Test
    void loginOrSignupMicrosoft_emailMatchDifferentSub_throwsUnauthorized() {
        User user = makeUser(TestIds.uuid(3), UserRole.USER, UserStatus.ACTIVE);
        user.setMicrosoftId("other-sub");
        when(userRepository.findByMicrosoftId("ms-sub-456")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("m@outlook.com")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> service.loginOrSignupMicrosoft(MS_OAUTH));
    }

    // ─── loginOrSignupApple ──────────────────────────────────────────────────

    private static final OAuthUser APPLE_OAUTH = new OAuthUser("apple-sub-789", "a@icloud.com", "Apple User", "apple");

    @Test
    void loginOrSignupApple_newUser_createsWithSubAndNullPassword() {
        when(userRepository.findByAppleId("apple-sub-789")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("a@icloud.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loginOrSignupApple(APPLE_OAUTH);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNull(captor.getValue().getPassword());
        assertEquals("apple-sub-789", captor.getValue().getAppleId());
    }

    @Test
    void loginOrSignupApple_knownSub_returnsUserWithoutAudit() {
        User user = makeUser(TestIds.uuid(4), UserRole.USER, UserStatus.ACTIVE);
        user.setAppleId("apple-sub-789");
        when(userRepository.findByAppleId("apple-sub-789")).thenReturn(Optional.of(user));

        User result = service.loginOrSignupApple(APPLE_OAUTH);

        assertSame(user, result);
        verify(auditLogger, never()).log(any(), any(), any());
    }

    @Test
    void loginOrSignupApple_emailMatchNoSub_linksSubAndLogsAudit() {
        User user = makeUser(TestIds.uuid(4), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findByAppleId("apple-sub-789")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("a@icloud.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loginOrSignupApple(APPLE_OAUTH);

        assertEquals("apple-sub-789", user.getAppleId());
        verify(auditLogger).log(eq(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED),
                eq(TestIds.uuid(4).toString()), eq("provider=apple"));
    }

    @Test
    void loginOrSignupApple_emailMatchDifferentSub_throwsUnauthorized() {
        User user = makeUser(TestIds.uuid(4), UserRole.USER, UserStatus.ACTIVE);
        user.setAppleId("other-sub");
        when(userRepository.findByAppleId("apple-sub-789")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("a@icloud.com")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> service.loginOrSignupApple(APPLE_OAUTH));
    }

    // ─── getAccessibleUserByID ───────────────────────────────────────────────

    @Test
    void getAccessibleUserByID_activeUser_returnsUser() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));

        assertSame(user, service.getAccessibleUserByID(TestIds.uuid(1)));
    }

    @Test
    void getAccessibleUserByID_inactiveUser_returnsUser() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.INACTIVE);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));

        assertSame(user, service.getAccessibleUserByID(TestIds.uuid(1)));
    }

    @Test
    void getAccessibleUserByID_suspendedUser_throwsForbidden() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.SUSPENDED);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class,
                () -> service.getAccessibleUserByID(TestIds.uuid(1)));
    }

    @Test
    void getAccessibleUserByID_pendingVerification_throwsForbidden() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.PENDING_VERIFICATION);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class,
                () -> service.getAccessibleUserByID(TestIds.uuid(1)));
    }

    @Test
    void getAccessibleUserByID_deletedUser_throwsForbidden() {
        User user = makeUser(TestIds.uuid(1), UserRole.USER, UserStatus.DELETED);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class,
                () -> service.getAccessibleUserByID(TestIds.uuid(1)));
    }

    @Test
    void getAccessibleUserByID_notFound_throwsResourceNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getAccessibleUserByID(TestIds.uuid(99)));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id, UserRole role, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setEmail("user-" + id + "@test.com");
        u.setPassword("hashed-password");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }
}
