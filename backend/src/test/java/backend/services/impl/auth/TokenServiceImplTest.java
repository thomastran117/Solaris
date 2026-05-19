package backend.services.impl.auth;

import backend.configurations.environment.EnvironmentSetting;
import backend.services.intf.CacheService;
import backend.services.intf.auth.TokenService;
import backend.testutil.TestIds;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenServiceImplTest {

    // 88 'A' characters: valid standard Base64 that decodes to 66 zero-bytes (≥ 64 required for HS512).
    private static final String TEST_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String TEST_ISSUER = "shopwave-api";
    private static final long ACCESS_TTL = 900L;
    private static final long REFRESH_TTL = 604800L;

    private CacheService cache;
    private EnvironmentSetting env;
    private EnvironmentSetting.Security security;
    private EnvironmentSetting.Security.Jwt jwt;
    private TokenServiceImpl service;

    @BeforeEach
    void setUp() {
        cache = mock(CacheService.class);
        env = mock(EnvironmentSetting.class);
        security = mock(EnvironmentSetting.Security.class);
        jwt = mock(EnvironmentSetting.Security.Jwt.class);

        when(env.getSecurity()).thenReturn(security);
        when(security.getJwt()).thenReturn(jwt);
        when(jwt.getSecret()).thenReturn(TEST_SECRET);
        when(jwt.getIssuer()).thenReturn(TEST_ISSUER);
        when(jwt.getAccessTokenTtlSeconds()).thenReturn(ACCESS_TTL);
        when(jwt.getRefreshTokenTtlSeconds()).thenReturn(REFRESH_TTL);

        service = new TokenServiceImpl(env, cache);
        // @PostConstruct is not called in plain unit tests — invoke manually
        service.validateJwtSecret();
    }

    // ─── generateAccessToken ─────────────────────────────────────────────────

    @Test
    void generateAccessToken_returnsNonNullJwt() {
        String token = service.generateAccessToken(TestIds.uuid(1), "USER", "u@test.com");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateAccessToken_containsCorrectSubjectRoleAndEmail() {
        UUID userId = TestIds.uuid(1);
        String token = service.generateAccessToken(userId, "USER", "u@test.com");

        UUID subject = UUID.fromString(service.getClaimFromToken(token, Claims::getSubject));
        String role = service.getClaimFromToken(token, c -> c.get("role", String.class));
        String email = service.getClaimFromToken(token, c -> c.get("email", String.class));

        assertEquals(userId, subject);
        assertEquals("USER", role);
        assertEquals("u@test.com", email);
    }

    @Test
    void generateAccessToken_containsCorrectIssuer() {
        String token = service.generateAccessToken(TestIds.uuid(1), "USER", "u@test.com");

        String issuer = service.getClaimFromToken(token, Claims::getIssuer);

        assertEquals(TEST_ISSUER, issuer);
    }

    // ─── generateRefreshToken ────────────────────────────────────────────────

    @Test
    void generateRefreshToken_returnsNonNullOpaqueToken() {
        String token = service.generateRefreshToken(TestIds.uuid(1), "USER", "u@test.com");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateRefreshToken_storesPayloadInCacheWithTtl() {
        UUID userId = TestIds.uuid(1);

        service.generateRefreshToken(userId, "USER", "u@test.com");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cache).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertTrue(keyCaptor.getValue().startsWith("refresh:token:"));
        assertTrue(valueCaptor.getValue().startsWith(userId.toString()));
        assertEquals(REFRESH_TTL, ttlCaptor.getValue());
    }

    @Test
    void generateRefreshToken_addsTokenIdToUserSet() {
        UUID userId = TestIds.uuid(1);

        service.generateRefreshToken(userId, "USER", "u@test.com");

        verify(cache).setAdd(eq("refresh:user:" + userId), anyString(), eq(REFRESH_TTL));
    }

    // ─── validateRefreshToken ────────────────────────────────────────────────

    @Test
    void validateRefreshToken_nullToken_returnsFalse() {
        assertFalse(service.validateRefreshToken(null));
    }

    @Test
    void validateRefreshToken_blankToken_returnsFalse() {
        assertFalse(service.validateRefreshToken("   "));
    }

    @Test
    void validateRefreshToken_existsInCache_returnsTrue() {
        when(cache.exists("refresh:token:abc")).thenReturn(true);
        assertTrue(service.validateRefreshToken("abc"));
    }

    @Test
    void validateRefreshToken_notInCache_returnsFalse() {
        when(cache.exists("refresh:token:abc")).thenReturn(false);
        assertFalse(service.validateRefreshToken("abc"));
    }

    // ─── getRefreshTokenPayload ───────────────────────────────────────────────

    @Test
    void getRefreshTokenPayload_nullToken_returnsNull() {
        assertNull(service.getRefreshTokenPayload(null));
    }

    @Test
    void getRefreshTokenPayload_notInCache_returnsNull() {
        when(cache.get(anyString())).thenReturn(null);
        assertNull(service.getRefreshTokenPayload("missing"));
    }

    @Test
    void getRefreshTokenPayload_validPayload_returnsRecord() {
        UUID userId = TestIds.uuid(7);
        when(cache.get("refresh:token:tok")).thenReturn(userId + ":USER:u@test.com");

        TokenService.RefreshTokenPayload payload = service.getRefreshTokenPayload("tok");

        assertNotNull(payload);
        assertEquals(userId, payload.userId());
        assertEquals("USER", payload.role());
        assertEquals("u@test.com", payload.email());
    }

    @Test
    void getRefreshTokenPayload_malformedPayload_returnsNull() {
        when(cache.get("refresh:token:tok")).thenReturn("not-a-uuid:USER:email");
        assertNull(service.getRefreshTokenPayload("tok"));
    }

    // ─── rotateRefreshToken ──────────────────────────────────────────────────

    @Test
    void rotateRefreshToken_notFoundInCache_returnsNull() {
        when(cache.getAndDelete(anyString())).thenReturn(null);
        assertNull(service.rotateRefreshToken("old-token"));
    }

    @Test
    void rotateRefreshToken_deletesOldAndStoresNew() {
        UUID userId = TestIds.uuid(3);
        when(cache.getAndDelete("refresh:token:old")).thenReturn(userId + ":USER:u@test.com");

        String newToken = service.rotateRefreshToken("old");

        assertNotNull(newToken);
        verify(cache).set(eq("refresh:token:" + newToken), anyString(), eq(REFRESH_TTL));
    }

    @Test
    void rotateRefreshToken_removesOldFromUserSet() {
        UUID userId = TestIds.uuid(3);
        when(cache.getAndDelete("refresh:token:old")).thenReturn(userId + ":USER:u@test.com");

        service.rotateRefreshToken("old");

        verify(cache).setRemove("refresh:user:" + userId, "old");
    }

    // ─── generateTokenPair ───────────────────────────────────────────────────

    @Test
    void generateTokenPair_returnsBothAccessAndRefreshTokens() {
        Map<String, Object> pair = service.generateTokenPair(TestIds.uuid(1), "USER", "u@test.com");

        assertNotNull(pair.get("accessToken"));
        assertNotNull(pair.get("refreshToken"));
        assertEquals("Bearer", pair.get("tokenType"));
    }

    // ─── revokeRefreshToken ──────────────────────────────────────────────────

    @Test
    void revokeRefreshToken_nullToken_doesNothing() {
        service.revokeRefreshToken(null);
        verifyNoInteractions(cache);
    }

    @Test
    void revokeRefreshToken_deletesFromCacheAndRemovesFromUserSet() {
        UUID userId = TestIds.uuid(4);
        when(cache.get("refresh:token:tok")).thenReturn(userId + ":USER:u@test.com");

        service.revokeRefreshToken("tok");

        verify(cache).delete("refresh:token:tok");
        verify(cache).setRemove("refresh:user:" + userId, "tok");
    }

    @Test
    void revokeRefreshToken_noPayload_stillDeletesKey() {
        when(cache.get("refresh:token:tok")).thenReturn(null);

        service.revokeRefreshToken("tok");

        verify(cache).delete("refresh:token:tok");
        verify(cache, never()).setRemove(any(), any());
    }

    // ─── revokeAllRefreshTokensForUser ────────────────────────────────────────

    @Test
    void revokeAllRefreshTokensForUser_deletesAllTokensAndUserSet() {
        UUID userId = TestIds.uuid(5);
        when(cache.setMembers("refresh:user:" + userId)).thenReturn(Set.of("tok1", "tok2"));

        service.revokeAllRefreshTokensForUser(userId);

        verify(cache).delete("refresh:token:tok1");
        verify(cache).delete("refresh:token:tok2");
        verify(cache).delete("refresh:user:" + userId);
    }

    // ─── getAuthentication ────────────────────────────────────────────────────

    @Test
    void getAuthentication_extractsUserIdAsSubjectAndRole() {
        UUID userId = TestIds.uuid(9);
        String token = service.generateAccessToken(userId, "ADMIN", "a@test.com");

        Authentication auth = service.getAuthentication(token);

        assertInstanceOf(UsernamePasswordAuthenticationToken.class, auth);
        assertEquals(userId, auth.getPrincipal());
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ─── validateJwtSecret (startup guard) ───────────────────────────────────

    @Test
    void validateJwtSecret_blankSecret_throwsIllegalState() {
        when(jwt.getSecret()).thenReturn("");
        TokenServiceImpl svc = new TokenServiceImpl(env, cache);
        assertThrows(IllegalStateException.class, svc::validateJwtSecret);
    }

    @Test
    void validateJwtSecret_tooShortSecret_throwsIllegalState() {
        // Valid Base64 that decodes to only 16 bytes — too short for HS512
        when(jwt.getSecret()).thenReturn("dGVzdC1zaG9ydA==");
        TokenServiceImpl svc = new TokenServiceImpl(env, cache);
        assertThrows(IllegalStateException.class, svc::validateJwtSecret);
    }
}
