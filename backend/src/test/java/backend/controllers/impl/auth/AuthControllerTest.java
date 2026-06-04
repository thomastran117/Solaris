package backend.controllers.impl.auth;

import backend.configurations.application.GlobalExceptionHandler;
import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.UnauthorizedException;
import backend.http.ClientInfo;
import backend.http.ClientRequestContext;
import backend.http.DeviceType;
import backend.models.core.User;
import backend.models.core.UserDevice;
import backend.security.oauth.InvalidOAuthTokenException;
import backend.security.oauth.OAuthProviderNotConfiguredException;
import backend.services.intf.AuthAuditLogger;
import backend.services.intf.CaptchaService;
import backend.services.intf.RateLimitService;
import backend.services.intf.auth.AuthService;
import backend.services.intf.auth.DeviceService;
import backend.testutil.TestIds;
import backend.utilities.intf.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private AuthService authService;
    private DeviceService deviceService;
    private Logger logger;
    private EnvironmentSetting env;
    private EnvironmentSetting.Security security;
    private EnvironmentSetting.Security.Cookie cookieCfg;
    private EnvironmentSetting.Security.RateLimits rateLimits;
    private RateLimitService rateLimitService;
    private AuthAuditLogger audit;
    private CaptchaService captchaService;
    private backend.services.intf.auth.TokenService tokenService;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID = TestIds.uuid(1);

    // Password satisfies @StrongPassword: uppercase, lowercase, digit, special char
    private static final String TEST_PASSWORD = "P@ssword1!";
    private static final String TEST_CAPTCHA  = "test-captcha";

    private static final AuthService.LoginResult LOGIN_RESULT = new AuthService.LoginResult(
            "access-tok", "refresh-tok", "user@test.com", "USER", USER_ID, "FREE");

    @BeforeEach
    void setUp() {
        authService    = mock(AuthService.class);
        deviceService  = mock(DeviceService.class);
        logger         = mock(Logger.class);
        env            = mock(EnvironmentSetting.class);
        security       = mock(EnvironmentSetting.Security.class);
        cookieCfg      = mock(EnvironmentSetting.Security.Cookie.class);
        rateLimits     = mock(EnvironmentSetting.Security.RateLimits.class);
        rateLimitService = mock(RateLimitService.class);
        audit          = mock(AuthAuditLogger.class);
        captchaService = mock(CaptchaService.class);
        tokenService   = mock(backend.services.intf.auth.TokenService.class);

        when(env.getSecurity()).thenReturn(security);
        when(security.getCookie()).thenReturn(cookieCfg);
        when(security.getRateLimits()).thenReturn(rateLimits);
        when(cookieCfg.isSecure()).thenReturn(false);
        when(cookieCfg.getSameSite()).thenReturn("Strict");
        // Default: captcha passes. Override in individual failure tests.
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);

        AuthController controller = new AuthController(authService, deviceService,
                logger, env, rateLimitService, audit, captchaService, tokenService);

        // GlobalExceptionHandler converts AppHttpExceptions to proper HTTP status codes
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();

        // Inject ClientInfo so ClientRequestContext.get() resolves a real IP
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(ClientRequestContext.ATTRIBUTE_KEY,
                new ClientInfo("1.2.3.4", DeviceType.DESKTOP, "Chrome", "Windows", "Mozilla/5.0"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    // ─── POST /auth/login ────────────────────────────────────────────────────

    @Test
    void login_knownDevice_returns200WithAuthResponse() throws Exception {
        when(authService.localAuthenicate(eq("user@test.com"), anyString()))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("user@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-tok"))
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    void login_unknownDevice_returns200WithDeviceVerificationRequired() throws Exception {
        when(authService.localAuthenicate(anyString(), anyString()))
                .thenReturn(AuthService.LoginAttemptResult.pendingVerification());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("user@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEVICE_VERIFICATION_REQUIRED"));
    }

    @Test
    void login_setsRefreshTokenCookieOnSuccess() throws Exception {
        when(authService.localAuthenicate(anyString(), anyString()))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("user@test.com")))
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-tok")));
    }

    @Test
    void login_enforces_rateLimit_twicePerRequest() throws Exception {
        when(authService.localAuthenicate(anyString(), anyString()))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("user@test.com")));

        // IP bucket + email bucket
        verify(rateLimitService, times(2)).enforce(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void login_auditsLoginSuccess_onKnownDevice() throws Exception {
        when(authService.localAuthenicate(anyString(), anyString()))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("user@test.com")));

        verify(audit).log(AuthAuditLogger.Event.LOGIN_SUCCESS, "user@test.com", null);
    }

    @Test
    void login_auditsDeviceVerificationRequired_onUnknownDevice() throws Exception {
        when(authService.localAuthenicate(anyString(), anyString()))
                .thenReturn(AuthService.LoginAttemptResult.pendingVerification());

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("user@test.com")));

        verify(audit).log(AuthAuditLogger.Event.DEVICE_VERIFICATION_REQUIRED, "user@test.com", null);
    }

    @Test
    void login_appHttpException_returns401AndAuditsFailure() throws Exception {
        when(authService.localAuthenicate(anyString(), anyString()))
                .thenThrow(new UnauthorizedException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("user@test.com")))
                .andExpect(status().isUnauthorized());

        verify(audit).log(eq(AuthAuditLogger.Event.LOGIN_FAILURE), anyString(), anyString());
    }

    @Test
    void login_unexpectedException_returns500AndAuditsFailure() throws Exception {
        when(authService.localAuthenicate(anyString(), anyString()))
                .thenThrow(new RuntimeException("DB down"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("user@test.com")))
                .andExpect(status().isInternalServerError());

        verify(audit).log(eq(AuthAuditLogger.Event.LOGIN_FAILURE), anyString(), anyString());
    }

    // ─── POST /auth/signup ───────────────────────────────────────────────────

    @Test
    void signup_returns201WithMessage() throws Exception {
        when(authService.signup(eq("new@test.com"), anyString()))
                .thenReturn(new AuthService.SignupResult("new@test.com",
                        "Account created. Please check your email to verify your account."));

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("new@test.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void signup_auditsSignup() throws Exception {
        when(authService.signup(anyString(), anyString()))
                .thenReturn(new AuthService.SignupResult("new@test.com", "ok"));

        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("new@test.com")));

        verify(audit).log(AuthAuditLogger.Event.SIGNUP, "new@test.com", null);
    }

    @Test
    void signup_conflict_returns409() throws Exception {
        when(authService.signup(anyString(), anyString()))
                .thenThrow(new ConflictException("An account with these credentials already exists"));

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("dup@test.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void signup_enforces_rateLimit() throws Exception {
        when(authService.signup(anyString(), anyString()))
                .thenReturn(new AuthService.SignupResult("new@test.com", "ok"));

        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("new@test.com")));

        verify(rateLimitService).enforce(eq("auth:signup:ip"), anyString(), anyInt(), anyInt());
    }

    // ─── POST /auth/verify ───────────────────────────────────────────────────

    @Test
    void verifyEmail_returns200WithMessage() throws Exception {
        mockMvc.perform(post("/auth/verify").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully."));
    }

    @Test
    void verifyEmail_callsService() throws Exception {
        mockMvc.perform(post("/auth/verify").param("token", "tok-123"));

        verify(authService).verifyEmail("tok-123");
    }

    @Test
    void verifyEmail_enforces_rateLimit() throws Exception {
        mockMvc.perform(post("/auth/verify").param("token", "tok"));

        verify(rateLimitService).enforce(eq("auth:verify:ip"), anyString(), anyInt(), anyInt());
    }

    // ─── POST /auth/verify-device ─────────────────────────────────────────────

    @Test
    void verifyDevice_returns200WithAuthResponse() throws Exception {
        when(authService.verifyDevice("dv-token")).thenReturn(LOGIN_RESULT);

        mockMvc.perform(post("/auth/verify-device").param("token", "dv-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-tok"));
    }

    @Test
    void verifyDevice_setsRefreshTokenCookie() throws Exception {
        when(authService.verifyDevice(anyString())).thenReturn(LOGIN_RESULT);

        mockMvc.perform(post("/auth/verify-device").param("token", "dv-token"))
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-tok")));
    }

    @Test
    void verifyDevice_enforces_rateLimit() throws Exception {
        when(authService.verifyDevice(anyString())).thenReturn(LOGIN_RESULT);

        mockMvc.perform(post("/auth/verify-device").param("token", "dv-token"));

        verify(rateLimitService).enforce(eq("auth:verify-device:ip"), anyString(), anyInt(), anyInt());
    }

    // ─── POST /auth/refresh ──────────────────────────────────────────────────

    @Test
    void refreshToken_withCookie_returns200WithTokenResponse() throws Exception {
        when(authService.refresh("old-refresh"))
                .thenReturn(new AuthService.RefreshResult("new-access", "new-refresh", 900L, "u@test.com", "USER", "FREE"));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.email").value("u@test.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    void refreshToken_noCookie_passesNullToService() throws Exception {
        when(authService.refresh(null))
                .thenReturn(new AuthService.RefreshResult("new-access", "new-refresh", 900L, "u@test.com", "USER", "FREE"));

        mockMvc.perform(post("/auth/refresh"));

        verify(authService).refresh(null);
    }

    @Test
    void refreshToken_setsNewRefreshTokenCookie() throws Exception {
        when(authService.refresh(anyString()))
                .thenReturn(new AuthService.RefreshResult("new-access", "new-refresh", 900L, "u@test.com", "USER", "FREE"));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "old-refresh")))
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=new-refresh")));
    }

    @Test
    void refreshToken_enforces_rateLimit() throws Exception {
        when(authService.refresh(any()))
                .thenReturn(new AuthService.RefreshResult("a", "b", 900L, "u@test.com", "USER", "FREE"));

        mockMvc.perform(post("/auth/refresh"));

        verify(rateLimitService).enforce(eq("auth:refresh:ip"), anyString(), anyInt(), anyInt());
    }

    @Test
    void refreshToken_auditsRefresh() throws Exception {
        when(authService.refresh(any()))
                .thenReturn(new AuthService.RefreshResult("a", "b", 900L, "u@test.com", "USER", "FREE"));

        mockMvc.perform(post("/auth/refresh"));

        verify(audit).log(AuthAuditLogger.Event.REFRESH, null, null);
    }

    // ─── POST /auth/logout ───────────────────────────────────────────────────

    @Test
    void logout_returns200WithMessage() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out."));
    }

    @Test
    void logout_withCookie_revokesToken() throws Exception {
        mockMvc.perform(post("/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", "old-token")));

        verify(authService).revokeRefreshToken("old-token");
    }

    @Test
    void logout_noCookie_doesNotRevokeToken() throws Exception {
        mockMvc.perform(post("/auth/logout"));

        verify(authService, never()).revokeRefreshToken(any());
    }

    @Test
    void logout_clearsCookieInResponse() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void logout_auditsLogout() throws Exception {
        mockMvc.perform(post("/auth/logout"));

        verify(audit).log(AuthAuditLogger.Event.LOGOUT, null, null);
    }

    // ─── GET /auth/devices ───────────────────────────────────────────────────

    @Test
    void listDevices_returnsDeviceList() throws Exception {
        authenticateAs(USER_ID);
        UserDevice device = makeDevice(TestIds.uuid(10), USER_ID);
        when(deviceService.getDevicesForUser(USER_ID)).thenReturn(List.of(device));

        mockMvc.perform(get("/auth/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TestIds.uuid(10).toString()))
                .andExpect(jsonPath("$[0].browser").value("Chrome"));
    }

    @Test
    void listDevices_emptyList_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(deviceService.getDevicesForUser(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/auth/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── DELETE /auth/devices/{id} ────────────────────────────────────────────

    @Test
    void removeDevice_returns204() throws Exception {
        authenticateAs(USER_ID);
        UUID deviceId = TestIds.uuid(10);

        mockMvc.perform(delete("/auth/devices/" + deviceId))
                .andExpect(status().isNoContent());

        verify(deviceService).removeDevice(USER_ID, deviceId);
    }

    @Test
    void removeDevice_appHttpException_propagatesStatus() throws Exception {
        authenticateAs(USER_ID);
        UUID deviceId = TestIds.uuid(10);
        doThrow(new backend.exceptions.http.ResourceNotFoundException("Device not found"))
                .when(deviceService).removeDevice(USER_ID, deviceId);

        mockMvc.perform(delete("/auth/devices/" + deviceId))
                .andExpect(status().isNotFound());
    }

    // ─── POST /auth/google ───────────────────────────────────────────────────

    @Test
    void googleLogin_missingIdToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void googleLogin_knownDevice_returns200WithAuthResponse() throws Exception {
        when(authService.googleAuthenicate("g-token"))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("g-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-tok"));
    }

    @Test
    void googleLogin_unknownDevice_returns200WithDeviceVerificationRequired() throws Exception {
        when(authService.googleAuthenicate("g-token"))
                .thenReturn(AuthService.LoginAttemptResult.pendingVerification());

        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("g-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEVICE_VERIFICATION_REQUIRED"));
    }

    @Test
    void googleLogin_providerNotConfigured_returns503() throws Exception {
        when(authService.googleAuthenicate(anyString()))
                .thenThrow(new OAuthProviderNotConfiguredException("Google not configured"));

        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("g-token")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void googleLogin_invalidToken_returns401() throws Exception {
        when(authService.googleAuthenicate(anyString()))
                .thenThrow(new InvalidOAuthTokenException("bad token"));

        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("g-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLogin_enforces_rateLimit() throws Exception {
        when(authService.googleAuthenicate(anyString()))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oauthBody("g-token")));

        verify(rateLimitService).enforce(eq("auth:oauth:ip"), anyString(), anyInt(), anyInt());
    }

    // ─── POST /auth/apple ────────────────────────────────────────────────────

    @Test
    void appleLogin_missingIdToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void appleLogin_knownDevice_returns200WithAuthResponse() throws Exception {
        when(authService.appleAuthenticate("apple-token"))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("apple-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-tok"));
    }

    @Test
    void appleLogin_providerNotConfigured_returns503() throws Exception {
        when(authService.appleAuthenticate(anyString()))
                .thenThrow(new OAuthProviderNotConfiguredException("Apple not configured"));

        mockMvc.perform(post("/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("apple-token")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void appleLogin_invalidToken_returns401() throws Exception {
        when(authService.appleAuthenticate(anyString()))
                .thenThrow(new InvalidOAuthTokenException("bad apple token"));

        mockMvc.perform(post("/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("apple-token")))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /auth/microsoft ─────────────────────────────────────────────────

    @Test
    void microsoftLogin_missingIdToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/microsoft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void microsoftLogin_knownDevice_returns200WithAuthResponse() throws Exception {
        when(authService.microsoftAuthenticate("ms-token"))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/microsoft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("ms-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-tok"));
    }

    @Test
    void microsoftLogin_providerNotConfigured_returns503() throws Exception {
        when(authService.microsoftAuthenticate(anyString()))
                .thenThrow(new OAuthProviderNotConfiguredException("MS not configured"));

        mockMvc.perform(post("/auth/microsoft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("ms-token")))
                .andExpect(status().isServiceUnavailable());
    }

    // ─── captcha enforcement ─────────────────────────────────────────────────

    @Test
    void login_captchaFailed_returns400BeforeAuthAttempt() throws Exception {
        when(captchaService.verify(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("user@test.com")))
                .andExpect(status().isBadRequest());

        verify(authService, never()).localAuthenicate(anyString(), anyString());
    }

    @Test
    void login_captchaVerifiedWithTokenAndIp() throws Exception {
        when(authService.localAuthenicate(anyString(), anyString()))
                .thenReturn(AuthService.LoginAttemptResult.success(LOGIN_RESULT));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("user@test.com")));

        verify(captchaService).verify(eq(TEST_CAPTCHA), anyString());
    }

    @Test
    void signup_captchaFailed_returns400BeforeSignupAttempt() throws Exception {
        when(captchaService.verify(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("new@test.com")))
                .andExpect(status().isBadRequest());

        verify(authService, never()).signup(anyString(), anyString());
    }

    @Test
    void signup_captchaVerifiedWithTokenAndIp() throws Exception {
        when(authService.signup(anyString(), anyString()))
                .thenReturn(new AuthService.SignupResult("new@test.com", "ok"));

        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("new@test.com")));

        verify(captchaService).verify(eq(TEST_CAPTCHA), anyString());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    /** Valid login body: email + TEST_PASSWORD (satisfies @StrongPassword) + TEST_CAPTCHA. */
    private String loginBody(String email) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("email", email, "password", TEST_PASSWORD, "captcha", TEST_CAPTCHA));
    }

    /** Valid signup body: same shape as login body. */
    private String signupBody(String email) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("email", email, "password", TEST_PASSWORD, "captcha", TEST_CAPTCHA));
    }

    private String oauthBody(String idToken) throws Exception {
        return objectMapper.writeValueAsString(Map.of("idToken", idToken));
    }

    private UserDevice makeDevice(UUID deviceId, UUID ownerId) {
        User user = new User();
        user.setId(ownerId);

        UserDevice d = new UserDevice();
        d.setId(deviceId);
        d.setUser(user);
        d.setFingerprint("fp-abc");
        d.setDeviceType(DeviceType.DESKTOP);
        d.setBrowser("Chrome");
        d.setOs("Windows");
        d.setLastIp("1.2.3.4");
        d.setCreatedAt(Instant.now());
        d.setLastSeenAt(Instant.now());
        return d;
    }
}
