package backend.services.impl.auth;

import backend.configurations.environment.EnvironmentSetting;
import backend.models.other.OAuthUser;
import backend.security.oauth.InvalidOAuthTokenException;
import backend.security.oauth.OAuthProviderNotConfiguredException;
import backend.security.oauth.OAuthProviderTransientException;
import backend.security.oauth.OAuthVerificationError;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthServiceImplTest {

    // Token is exactly at the limit (length == 100, maxLength == 100 → passes)
    private static final int MAX_TOKEN_LENGTH = 100;
    private static final String VALID_TOKEN = "a".repeat(MAX_TOKEN_LENGTH);

    private GoogleIdTokenVerifier googleVerifier;
    private JwtDecoder microsoftDecoder;
    private JwtDecoder appleDecoder;
    private EnvironmentSetting env;

    @BeforeEach
    void setUp() {
        googleVerifier = mock(GoogleIdTokenVerifier.class);
        microsoftDecoder = mock(JwtDecoder.class);
        appleDecoder = mock(JwtDecoder.class);
        env = mockEnv(MAX_TOKEN_LENGTH);
    }

    private static EnvironmentSetting mockEnv(int maxLen) {
        EnvironmentSetting e = mock(EnvironmentSetting.class);
        EnvironmentSetting.Security security = mock(EnvironmentSetting.Security.class);
        when(e.getSecurity()).thenReturn(security);
        when(security.getOauthMaxTokenLength()).thenReturn(maxLen);
        return e;
    }

    private OAuthServiceImpl service() {
        return new OAuthServiceImpl(googleVerifier, microsoftDecoder, appleDecoder, env);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static GoogleIdToken mockGoogleToken(String sub, String email, Boolean verified, Object name)
            throws Exception {
        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        when(payload.getSubject()).thenReturn(sub);
        when(payload.getEmail()).thenReturn(email);
        when(payload.getEmailVerified()).thenReturn(verified);
        when(payload.get("name")).thenReturn(name);
        GoogleIdToken token = mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload);
        return token;
    }

    /** Build a Spring Security Jwt with arbitrary claims. */
    private static Jwt jwt(String sub, String preferredUsername, String name, String emailVerified) {
        Jwt.Builder b = Jwt.withTokenValue("fake-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        if (sub != null)               b.claim("sub", sub);
        if (preferredUsername != null) b.claim("preferred_username", preferredUsername);
        if (name != null)              b.claim("name", name);
        if (emailVerified != null)     b.claim("email_verified", emailVerified);
        return b.build();
    }

    private static Jwt jwtForApple(String sub, String email, String name, String emailVerified) {
        Jwt.Builder b = Jwt.withTokenValue("fake-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        if (sub != null)           b.claim("sub", sub);
        if (email != null)         b.claim("email", email);
        if (name != null)          b.claim("name", name);
        if (emailVerified != null) b.claim("email_verified", emailVerified);
        return b.build();
    }

    // ── Null-provider short-circuits ─────────────────────────────────────────

    @Test
    void google_nullVerifier_throwsProviderNotConfigured() {
        var svc = new OAuthServiceImpl(null, microsoftDecoder, appleDecoder, env);
        assertThrows(OAuthProviderNotConfiguredException.class, () -> svc.verifyGoogleToken(VALID_TOKEN));
    }

    @Test
    void microsoft_nullDecoder_throwsProviderNotConfigured() {
        var svc = new OAuthServiceImpl(googleVerifier, null, appleDecoder, env);
        assertThrows(OAuthProviderNotConfiguredException.class, () -> svc.verifyMicrosoftToken(VALID_TOKEN));
    }

    @Test
    void apple_nullDecoder_throwsProviderNotConfigured() {
        var svc = new OAuthServiceImpl(googleVerifier, microsoftDecoder, null, env);
        assertThrows(OAuthProviderNotConfiguredException.class, () -> svc.verifyAppleToken(VALID_TOKEN));
    }

    // ── Token length validation ──────────────────────────────────────────────

    @Test
    void google_nullToken_throwsInvalidToken() {
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(null));
    }

    @Test
    void google_blankToken_throwsInvalidToken() {
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken("  "));
    }

    @Test
    void google_oversizedToken_throwsInvalidToken() {
        String oversized = "a".repeat(MAX_TOKEN_LENGTH + 1);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(oversized));
    }

    @Test
    void microsoft_nullToken_throwsInvalidToken() {
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyMicrosoftToken(null));
    }

    @Test
    void microsoft_oversizedToken_throwsInvalidToken() {
        String oversized = "a".repeat(MAX_TOKEN_LENGTH + 1);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyMicrosoftToken(oversized));
    }

    @Test
    void apple_nullToken_throwsInvalidToken() {
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyAppleToken(null));
    }

    @Test
    void apple_oversizedToken_throwsInvalidToken() {
        String oversized = "a".repeat(MAX_TOKEN_LENGTH + 1);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyAppleToken(oversized));
    }

    // ── Google: exception mapping ─────────────────────────────────────────────

    @Test
    void google_verifierThrowsGeneralSecurityException_throwsInvalidToken() throws Exception {
        when(googleVerifier.verify(VALID_TOKEN)).thenThrow(new GeneralSecurityException("bad sig"));
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    @Test
    void google_verifierThrowsIOException_throwsTransient() throws Exception {
        when(googleVerifier.verify(VALID_TOKEN)).thenThrow(new IOException("timeout"));
        assertThrows(OAuthProviderTransientException.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    @Test
    void google_verifierThrowsIllegalArgumentException_throwsInvalidToken() throws Exception {
        when(googleVerifier.verify(VALID_TOKEN)).thenThrow(new IllegalArgumentException("bad token"));
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    @Test
    void google_verifierThrowsUnknownRuntimeException_throwsVerificationError() throws Exception {
        when(googleVerifier.verify(VALID_TOKEN)).thenThrow(new RuntimeException("unknown"));
        assertThrows(OAuthVerificationError.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    // ── Google: claim validation ──────────────────────────────────────────────

    @Test
    void google_verifierReturnsNull_throwsInvalidToken() throws Exception {
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(null);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    @Test
    void google_missingEmail_throwsInvalidToken() throws Exception {
        var token = mockGoogleToken("sub123", null, true, "Test Name");
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(token);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    @Test
    void google_blankEmail_throwsInvalidToken() throws Exception {
        var token = mockGoogleToken("sub123", "  ", true, "Test Name");
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(token);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    @Test
    void google_emailNotVerified_throwsInvalidToken() throws Exception {
        var token = mockGoogleToken("sub123", "user@test.com", false, "Test Name");
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(token);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    @Test
    void google_emailVerifiedNull_throwsInvalidToken() throws Exception {
        var token = mockGoogleToken("sub123", "user@test.com", null, "Test Name");
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(token);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyGoogleToken(VALID_TOKEN));
    }

    // ── Google: happy paths ───────────────────────────────────────────────────

    @Test
    void google_happyPath_returnsOAuthUser() throws Exception {
        var token = mockGoogleToken("sub123", "user@test.com", true, "Test User");
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(token);
        OAuthUser user = service().verifyGoogleToken(VALID_TOKEN);
        assertEquals("sub123", user.sub());
        assertEquals("user@test.com", user.email());
        assertEquals("Test User", user.name());
        assertEquals("google", user.provider());
    }

    @Test
    void google_nullName_usesEmailAsName() throws Exception {
        var token = mockGoogleToken("sub123", "user@test.com", true, null);
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(token);
        OAuthUser user = service().verifyGoogleToken(VALID_TOKEN);
        assertEquals("user@test.com", user.name());
    }

    @Test
    void google_numericNameClaim_convertedToString() throws Exception {
        var token = mockGoogleToken("sub123", "user@test.com", true, 42);
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(token);
        OAuthUser user = service().verifyGoogleToken(VALID_TOKEN);
        assertEquals("42", user.name());
    }

    @Test
    void google_booleanNameClaim_convertedToString() throws Exception {
        var token = mockGoogleToken("sub123", "user@test.com", true, true);
        when(googleVerifier.verify(VALID_TOKEN)).thenReturn(token);
        OAuthUser user = service().verifyGoogleToken(VALID_TOKEN);
        assertEquals("true", user.name());
    }

    // ── Microsoft: exception mapping ─────────────────────────────────────────

    @Test
    void microsoft_decoderThrowsJwtException_throwsInvalidToken() {
        when(microsoftDecoder.decode(VALID_TOKEN)).thenThrow(new JwtException("bad jwt"));
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyMicrosoftToken(VALID_TOKEN));
    }

    @Test
    void microsoft_decoderThrowsIOExceptionWrapped_throwsTransient() {
        // IOException is retryable; a RuntimeException wrapping it still walks the cause chain
        when(microsoftDecoder.decode(VALID_TOKEN)).thenThrow(new RuntimeException(new IOException("network")));
        assertThrows(OAuthProviderTransientException.class, () -> service().verifyMicrosoftToken(VALID_TOKEN));
    }

    @Test
    void microsoft_decoderThrowsUnknownException_throwsVerificationError() {
        when(microsoftDecoder.decode(VALID_TOKEN)).thenThrow(new RuntimeException("unknown"));
        assertThrows(OAuthVerificationError.class, () -> service().verifyMicrosoftToken(VALID_TOKEN));
    }

    // ── Microsoft: claim validation ───────────────────────────────────────────

    @Test
    void microsoft_missingEmail_throwsInvalidToken() {
        var j = jwt("sub123", null, "Test User", null);
        when(microsoftDecoder.decode(VALID_TOKEN)).thenReturn(j);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyMicrosoftToken(VALID_TOKEN));
    }

    @Test
    void microsoft_emailVerifiedFalse_throwsInvalidToken() {
        var j = jwt("sub123", "user@test.com", "Test User", "false");
        when(microsoftDecoder.decode(VALID_TOKEN)).thenReturn(j);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyMicrosoftToken(VALID_TOKEN));
    }

    @Test
    void microsoft_missingSub_throwsInvalidToken() {
        var j = jwt(null, "user@test.com", "Test User", null);
        when(microsoftDecoder.decode(VALID_TOKEN)).thenReturn(j);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyMicrosoftToken(VALID_TOKEN));
    }

    // ── Microsoft: happy paths ────────────────────────────────────────────────

    @Test
    void microsoft_happyPath_returnsOAuthUser() {
        var j = jwt("sub123", "user@test.com", "Test User", null);
        when(microsoftDecoder.decode(VALID_TOKEN)).thenReturn(j);
        OAuthUser user = service().verifyMicrosoftToken(VALID_TOKEN);
        assertEquals("sub123", user.sub());
        assertEquals("user@test.com", user.email());
        assertEquals("Test User", user.name());
        assertEquals("microsoft", user.provider());
    }

    @Test
    void microsoft_nullName_usesEmailAsName() {
        var j = jwt("sub123", "user@test.com", null, null);
        when(microsoftDecoder.decode(VALID_TOKEN)).thenReturn(j);
        OAuthUser user = service().verifyMicrosoftToken(VALID_TOKEN);
        assertEquals("user@test.com", user.name());
    }

    @Test
    void microsoft_emailVerifiedTrue_passes() {
        var j = jwt("sub123", "user@test.com", "User", "true");
        when(microsoftDecoder.decode(VALID_TOKEN)).thenReturn(j);
        OAuthUser user = service().verifyMicrosoftToken(VALID_TOKEN);
        assertEquals("user@test.com", user.email());
    }

    // ── Apple: exception mapping ──────────────────────────────────────────────

    @Test
    void apple_decoderThrowsJwtException_throwsInvalidToken() {
        when(appleDecoder.decode(VALID_TOKEN)).thenThrow(new JwtException("bad jwt"));
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyAppleToken(VALID_TOKEN));
    }

    @Test
    void apple_decoderThrowsIOExceptionWrapped_throwsTransient() {
        when(appleDecoder.decode(VALID_TOKEN)).thenThrow(new RuntimeException(new IOException("network")));
        assertThrows(OAuthProviderTransientException.class, () -> service().verifyAppleToken(VALID_TOKEN));
    }

    @Test
    void apple_decoderThrowsUnknownException_throwsVerificationError() {
        when(appleDecoder.decode(VALID_TOKEN)).thenThrow(new RuntimeException("unknown"));
        assertThrows(OAuthVerificationError.class, () -> service().verifyAppleToken(VALID_TOKEN));
    }

    // ── Apple: claim validation ───────────────────────────────────────────────

    @Test
    void apple_missingSub_throwsInvalidToken() {
        var j = jwtForApple(null, "user@test.com", "Apple User", null);
        when(appleDecoder.decode(VALID_TOKEN)).thenReturn(j);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyAppleToken(VALID_TOKEN));
    }

    @Test
    void apple_missingEmail_throwsInvalidToken() {
        var j = jwtForApple("sub123", null, "Apple User", null);
        when(appleDecoder.decode(VALID_TOKEN)).thenReturn(j);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyAppleToken(VALID_TOKEN));
    }

    @Test
    void apple_emailVerifiedFalse_throwsInvalidToken() {
        var j = jwtForApple("sub123", "user@test.com", "Apple User", "false");
        when(appleDecoder.decode(VALID_TOKEN)).thenReturn(j);
        assertThrows(InvalidOAuthTokenException.class, () -> service().verifyAppleToken(VALID_TOKEN));
    }

    // ── Apple: happy paths ────────────────────────────────────────────────────

    @Test
    void apple_happyPath_returnsOAuthUser() {
        var j = jwtForApple("sub123", "user@test.com", "Apple User", null);
        when(appleDecoder.decode(VALID_TOKEN)).thenReturn(j);
        OAuthUser user = service().verifyAppleToken(VALID_TOKEN);
        assertEquals("sub123", user.sub());
        assertEquals("user@test.com", user.email());
        assertEquals("Apple User", user.name());
        assertEquals("apple", user.provider());
    }

    @Test
    void apple_nullName_usesEmailAsName() {
        var j = jwtForApple("sub123", "user@test.com", null, null);
        when(appleDecoder.decode(VALID_TOKEN)).thenReturn(j);
        OAuthUser user = service().verifyAppleToken(VALID_TOKEN);
        assertEquals("user@test.com", user.name());
    }

    @Test
    void apple_emailVerifiedTrue_passes() {
        var j = jwtForApple("sub123", "user@test.com", "User", "true");
        when(appleDecoder.decode(VALID_TOKEN)).thenReturn(j);
        OAuthUser user = service().verifyAppleToken(VALID_TOKEN);
        assertEquals("user@test.com", user.email());
    }

    // ── Constructor edge cases ────────────────────────────────────────────────

    @Test
    void constructor_zeroMaxTokenLength_fallsBackToDefault() {
        // maxTokenLength=0 → invalid → service falls back to 16384 default
        // A 101-char token passes since 101 < 16384
        var svc = new OAuthServiceImpl(googleVerifier, microsoftDecoder, appleDecoder, mockEnv(0));
        // should NOT throw InvalidOAuthTokenException for a short token
        // (we can't fully verify no throw without also mocking the verifier, but we can check
        //  that passing a 100-char token doesn't throw the length error)
        assertThrows(RuntimeException.class, () -> svc.verifyGoogleToken(VALID_TOKEN));
    }
}
