package backend.services.impl.auth;

import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.UnauthorizedException;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.UserRepository;
import backend.services.intf.AuthAuditLogger;
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
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditLogger = mock(AuthAuditLogger.class);
        service = new UserServiceImpl(userRepository, passwordEncoder, auditLogger);
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

    @Test
    void loginOrSignupGoogle_newUser_createsWithNullPasswordAndUserRole() {
        when(userRepository.findByEmail("g@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.loginOrSignupGoogle("g@gmail.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNull(captor.getValue().getPassword());
        assertEquals(UserRole.USER, captor.getValue().getRole());
        assertEquals("g@gmail.com", result.getEmail());
    }

    @Test
    void loginOrSignupGoogle_existingUserWithPassword_logsAccountLinked() {
        User user = makeUser(TestIds.uuid(2), UserRole.USER, UserStatus.ACTIVE);
        user.setPassword("hashed");
        when(userRepository.findByEmail("g@gmail.com")).thenReturn(Optional.of(user));

        service.loginOrSignupGoogle("g@gmail.com");

        verify(auditLogger).log(eq(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED),
                eq(TestIds.uuid(2).toString()), eq("provider=google"));
    }

    @Test
    void loginOrSignupGoogle_existingUserWithoutPassword_doesNotLogAccountLinked() {
        User user = makeUser(TestIds.uuid(2), UserRole.USER, UserStatus.ACTIVE);
        user.setPassword(null);
        when(userRepository.findByEmail("g@gmail.com")).thenReturn(Optional.of(user));

        service.loginOrSignupGoogle("g@gmail.com");

        verify(auditLogger, never()).log(any(), any(), any());
    }

    @Test
    void loginOrSignupGoogle_suspendedExistingUser_throwsForbidden() {
        User user = makeUser(TestIds.uuid(2), UserRole.USER, UserStatus.SUSPENDED);
        when(userRepository.findByEmail("g@gmail.com")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class,
                () -> service.loginOrSignupGoogle("g@gmail.com"));
    }

    // ─── loginOrSignupMicrosoft ───────────────────────────────────────────────

    @Test
    void loginOrSignupMicrosoft_newUser_createsWithNullPassword() {
        when(userRepository.findByEmail("m@microsoft.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loginOrSignupMicrosoft("m@microsoft.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNull(captor.getValue().getPassword());
    }

    @Test
    void loginOrSignupMicrosoft_existingUserWithPassword_logsAccountLinked() {
        User user = makeUser(TestIds.uuid(3), UserRole.USER, UserStatus.ACTIVE);
        user.setPassword("hashed");
        when(userRepository.findByEmail("m@microsoft.com")).thenReturn(Optional.of(user));

        service.loginOrSignupMicrosoft("m@microsoft.com");

        verify(auditLogger).log(eq(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED),
                eq(TestIds.uuid(3).toString()), eq("provider=microsoft"));
    }

    // ─── loginOrSignupApple ──────────────────────────────────────────────────

    @Test
    void loginOrSignupApple_newUser_createsWithNullPassword() {
        when(userRepository.findByEmail("a@apple.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loginOrSignupApple("a@apple.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNull(captor.getValue().getPassword());
    }

    @Test
    void loginOrSignupApple_existingUserWithPassword_logsAccountLinked() {
        User user = makeUser(TestIds.uuid(4), UserRole.USER, UserStatus.ACTIVE);
        user.setPassword("hashed");
        when(userRepository.findByEmail("a@apple.com")).thenReturn(Optional.of(user));

        service.loginOrSignupApple("a@apple.com");

        verify(auditLogger).log(eq(AuthAuditLogger.Event.OAUTH_ACCOUNT_LINKED),
                eq(TestIds.uuid(4).toString()), eq("provider=apple"));
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
