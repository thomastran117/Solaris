package backend.services.impl.auth;

import backend.http.ClientInfo;
import backend.http.ClientRequestContext;
import backend.http.DeviceType;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.models.other.OAuthUser;
import backend.services.intf.auth.AuthService;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.auth.EmailVerificationService;
import backend.services.intf.auth.OAuthService;
import backend.services.intf.auth.TokenService;
import backend.services.intf.auth.UserService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.UnauthorizedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    private UserService userService;
    private OAuthService oauthService;
    private TokenService tokenService;
    private EmailVerificationService emailVerificationService;
    private DeviceService deviceService;
    private AuthServiceImpl service;

    private static final ClientInfo CLIENT_INFO = new ClientInfo(
            "1.2.3.4", DeviceType.DESKTOP, "Chrome", "Windows", "Mozilla/5.0"
    );
    private static final String FINGERPRINT = "fp-abc123";

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        oauthService = mock(OAuthService.class);
        tokenService = mock(TokenService.class);
        emailVerificationService = mock(EmailVerificationService.class);
        deviceService = mock(DeviceService.class);
        service = new AuthServiceImpl(userService, oauthService, tokenService,
                emailVerificationService, deviceService, true);

        // Inject ClientInfo into Spring's request context so ClientRequestContext.get() works
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(ClientRequestContext.ATTRIBUTE_KEY, CLIENT_INFO);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        when(deviceService.computeFingerprint(CLIENT_INFO.userAgent())).thenReturn(FINGERPRINT);
        when(tokenService.generateTokenPair(any(), any(), any())).thenReturn(Map.of(
                "accessToken", "access-tok",
                "refreshToken", "refresh-tok"
        ));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ─── localAuthenicate ────────────────────────────────────────────────────

    @Test
    void localAuthenicate_knownDevice_returnsSuccessLoginResult() {
        User user = makeUser(TestIds.uuid(1));
        when(userService.login("u@test.com", "pw")).thenReturn(user);
        when(deviceService.isKnownDevice(TestIds.uuid(1), FINGERPRINT)).thenReturn(true);

        AuthService.LoginAttemptResult result = service.localAuthenicate("u@test.com", "pw");

        assertFalse(result.deviceVerificationRequired());
        assertNotNull(result.loginResult());
        assertEquals("access-tok", result.loginResult().accessToken());
        verify(deviceService).recordDeviceSeen(TestIds.uuid(1), CLIENT_INFO);
    }

    @Test
    void localAuthenicate_unknownDevice_returnsPendingVerification() {
        User user = makeUser(TestIds.uuid(1));
        when(userService.login("u@test.com", "pw")).thenReturn(user);
        when(deviceService.isKnownDevice(TestIds.uuid(1), FINGERPRINT)).thenReturn(false);

        AuthService.LoginAttemptResult result = service.localAuthenicate("u@test.com", "pw");

        assertTrue(result.deviceVerificationRequired());
        assertNull(result.loginResult());
        verify(deviceService).initiateDeviceVerification(TestIds.uuid(1), "u@test.com", CLIENT_INFO);
    }

    // ─── googleAuthenicate ───────────────────────────────────────────────────

    @Test
    void googleAuthenicate_knownDevice_returnsSuccessResult() {
        User user = makeUser(TestIds.uuid(2));
        when(oauthService.verifyGoogleToken("g-id-token"))
                .thenReturn(new OAuthUser("sub", "g@test.com", "Google User", "google"));
        when(userService.loginOrSignupGoogle(any(OAuthUser.class))).thenReturn(user);
        when(deviceService.isKnownDevice(TestIds.uuid(2), FINGERPRINT)).thenReturn(true);

        AuthService.LoginAttemptResult result = service.googleAuthenicate("g-id-token");

        assertFalse(result.deviceVerificationRequired());
        verify(deviceService).recordDeviceSeen(TestIds.uuid(2), CLIENT_INFO);
    }

    @Test
    void googleAuthenicate_unknownDevice_returnsPendingVerification() {
        User user = makeUser(TestIds.uuid(2), "g@test.com");
        when(oauthService.verifyGoogleToken("g-id-token"))
                .thenReturn(new OAuthUser("sub", "g@test.com", "Google User", "google"));
        when(userService.loginOrSignupGoogle(any(OAuthUser.class))).thenReturn(user);
        when(deviceService.isKnownDevice(TestIds.uuid(2), FINGERPRINT)).thenReturn(false);

        AuthService.LoginAttemptResult result = service.googleAuthenicate("g-id-token");

        assertTrue(result.deviceVerificationRequired());
        verify(deviceService).initiateDeviceVerification(TestIds.uuid(2), "g@test.com", CLIENT_INFO);
    }

    // ─── microsoftAuthenticate ────────────────────────────────────────────────

    @Test
    void microsoftAuthenticate_knownDevice_returnsSuccessResult() {
        User user = makeUser(TestIds.uuid(3));
        when(oauthService.verifyMicrosoftToken("ms-tok"))
                .thenReturn(new OAuthUser("sub", "m@test.com", "MS User", "microsoft"));
        when(userService.loginOrSignupMicrosoft(any(OAuthUser.class))).thenReturn(user);
        when(deviceService.isKnownDevice(TestIds.uuid(3), FINGERPRINT)).thenReturn(true);

        AuthService.LoginAttemptResult result = service.microsoftAuthenticate("ms-tok");

        assertFalse(result.deviceVerificationRequired());
    }

    // ─── appleAuthenticate ────────────────────────────────────────────────────

    @Test
    void appleAuthenticate_knownDevice_returnsSuccessResult() {
        User user = makeUser(TestIds.uuid(4));
        when(oauthService.verifyAppleToken("apple-tok"))
                .thenReturn(new OAuthUser("sub", "a@icloud.com", "Apple User", "apple"));
        when(userService.loginOrSignupApple(any(OAuthUser.class))).thenReturn(user);
        when(deviceService.isKnownDevice(TestIds.uuid(4), FINGERPRINT)).thenReturn(true);

        AuthService.LoginAttemptResult result = service.appleAuthenticate("apple-tok");

        assertFalse(result.deviceVerificationRequired());
    }

    // ─── refresh ─────────────────────────────────────────────────────────────

    @Test
    void refresh_nullToken_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> service.refresh(null));
    }

    @Test
    void refresh_consumeReturnsNull_throwsUnauthorized() {
        when(tokenService.consumeRefreshToken("bad")).thenReturn(null);
        assertThrows(UnauthorizedException.class, () -> service.refresh("bad"));
    }

    @Test
    void refresh_validToken_atomicallyConsumesAndReturnsNewTokens() {
        UUID userId = TestIds.uuid(5);
        User user = makeUser(userId);
        TokenService.RefreshTokenPayload payload =
                new TokenService.RefreshTokenPayload(userId, "USER", "u@test.com");

        when(tokenService.consumeRefreshToken("old-tok")).thenReturn(payload);
        when(userService.getAccessibleUserByID(userId)).thenReturn(user);
        when(tokenService.generateRefreshToken(userId, "USER", "u@test.com")).thenReturn("new-refresh");
        when(tokenService.generateAccessToken(userId, "USER", "u@test.com")).thenReturn("new-access");
        when(tokenService.getAccessTokenExpiresInSeconds()).thenReturn(900L);

        AuthService.RefreshResult result = service.refresh("old-tok");

        // consumeRefreshToken is the single atomic gating call — no separate revoke
        verify(tokenService).consumeRefreshToken("old-tok");
        verify(tokenService, never()).revokeRefreshToken(any());
        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
        assertEquals(900L, result.expiresInSeconds());
        assertEquals("u@test.com", result.email());
        assertEquals("USER", result.role());
        assertEquals("FREE", result.tier());
    }

    @Test
    void refresh_blockedUser_throwsForbiddenAfterConsumingToken() {
        UUID userId = TestIds.uuid(5);
        TokenService.RefreshTokenPayload payload =
                new TokenService.RefreshTokenPayload(userId, "USER", "u@test.com");

        when(tokenService.consumeRefreshToken("tok")).thenReturn(payload);
        when(userService.getAccessibleUserByID(userId))
                .thenThrow(new ForbiddenException("Account suspended"));

        assertThrows(ForbiddenException.class, () -> service.refresh("tok"));
        verify(tokenService).consumeRefreshToken("tok");
    }

    // ─── signup ──────────────────────────────────────────────────────────────

    @Test
    void signup_createsUserAndInitiatesEmailVerification() {
        User user = makeUser(TestIds.uuid(6));
        when(userService.signup("u@test.com", "pw")).thenReturn(user);

        service.signup("u@test.com", "pw");

        verify(emailVerificationService).initiateVerification(TestIds.uuid(6), "u@test.com");
    }

    @Test
    void signup_returnsEmailAndMessage() {
        User user = makeUser(TestIds.uuid(6));
        when(userService.signup("u@test.com", "pw")).thenReturn(user);

        AuthService.SignupResult result = service.signup("u@test.com", "pw");

        assertEquals("u@test.com", result.email());
        assertNotNull(result.message());
    }

    // ─── verifyEmail ─────────────────────────────────────────────────────────

    @Test
    void verifyEmail_consumesTokenAndActivatesUser() {
        UUID userId = TestIds.uuid(7);
        when(emailVerificationService.consumeVerificationToken("verify-tok")).thenReturn(userId);

        service.verifyEmail("verify-tok");

        verify(userService).activateUser(userId);
    }

    // ─── verifyDevice ────────────────────────────────────────────────────────

    @Test
    void verifyDevice_consumesTokenRecordsDeviceAndReturnsLoginResult() {
        UUID userId = TestIds.uuid(8);
        User user = makeUser(userId);
        DeviceService.DeviceVerificationPayload dvp = new DeviceService.DeviceVerificationPayload(
                userId, FINGERPRINT, "Chrome", "Windows", DeviceType.DESKTOP, "1.2.3.4", "Mozilla/5.0"
        );
        when(deviceService.consumeDeviceVerificationToken("dv-tok")).thenReturn(dvp);
        when(userService.getAccessibleUserByID(userId)).thenReturn(user);

        AuthService.LoginResult result = service.verifyDevice("dv-tok");

        verify(deviceService).recordDeviceSeen(eq(userId), any(ClientInfo.class));
        assertEquals("access-tok", result.accessToken());
    }

    // ─── revokeRefreshToken / revokeAllRefreshTokensForUser ──────────────────

    @Test
    void revokeRefreshToken_delegatesToTokenService() {
        service.revokeRefreshToken("tok");
        verify(tokenService).revokeRefreshToken("tok");
    }

    @Test
    void revokeAllRefreshTokensForUser_delegatesToTokenService() {
        service.revokeAllRefreshTokensForUser(TestIds.uuid(1));
        verify(tokenService).revokeAllRefreshTokensForUser(TestIds.uuid(1));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id) {
        return makeUser(id, "u@test.com");
    }

    private User makeUser(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }
}
