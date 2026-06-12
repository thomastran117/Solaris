package backend.integration.auth;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests the real JWT filter chain and @RequireAuth AOP enforcement, neither of which
 * is exercised by the standalone-MockMvc unit tests.
 */
class AuthTokenIT extends AbstractIntegrationIT {

    private static final String HELLO_URL   = "/auth/hello";
    private static final String DEVICES_URL = "/auth/devices";

    // ── Valid token ───────────────────────────────────────────────────────────

    @Test
    void protectedEndpoint_validToken_returns200() throws Exception {
        User user = createActiveUser("token@example.com", "Password123!");

        mockMvc.perform(get(HELLO_URL)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk());
    }

    @Test
    void listDevices_validToken_returns200WithEmptyArray() throws Exception {
        User user = createActiveUser("devlist@example.com", "Password123!");

        mockMvc.perform(get(DEVICES_URL)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listDevices_validToken_registeredDevice_returnsDevice() throws Exception {
        User user = createActiveUser("devlist2@example.com", "Password123!");
        registerKnownDevice(user);

        mockMvc.perform(get(DEVICES_URL)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fingerprint").isNotEmpty());
    }

    // ── Missing / malformed token ─────────────────────────────────────────────

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get(HELLO_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_malformedBearerHeader_returns401() throws Exception {
        mockMvc.perform(get(HELLO_URL)
                        .header("Authorization", "NotBearer abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_emptyBearerToken_returns401() throws Exception {
        mockMvc.perform(get(HELLO_URL)
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    // ── Expired token ─────────────────────────────────────────────────────────

    @Test
    void protectedEndpoint_expiredToken_returns401() throws Exception {
        User user = createActiveUser("expired@example.com", "Password123!");
        String expired = buildExpiredToken(user);

        mockMvc.perform(get(HELLO_URL)
                        .header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    // ── Tampered / wrong-key token ────────────────────────────────────────────

    @Test
    void protectedEndpoint_tamperedToken_returns401() throws Exception {
        User user = createActiveUser("tampered@example.com", "Password123!");
        String valid = accessTokenFor(user);
        // Replace the signature segment entirely — avoids base64 padding edge cases
        // where flipping the last character changes only trailing zero-padding bits.
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".aGFja2VkU2lnbmF0dXJlWFhY";

        mockMvc.perform(get(HELLO_URL)
                        .header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_wrongIssuer_returns401() throws Exception {
        User user = createActiveUser("issuer@example.com", "Password123!");
        String wrongIssuer = buildTokenWithWrongIssuer(user);

        mockMvc.perform(get(HELLO_URL)
                        .header("Authorization", bearer(wrongIssuer)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_blacklistedToken_returns401() throws Exception {
        User user = createActiveUser("blacklisted@example.com", "Password123!");
        String token = accessTokenFor(user);
        tokenService.blacklistAccessToken(token);

        mockMvc.perform(get(HELLO_URL)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    // ── Device management ─────────────────────────────────────────────────────

    @Test
    void removeDevice_validToken_ownDevice_returns204() throws Exception {
        User user = createActiveUser("rm@example.com", "Password123!");
        registerKnownDevice(user);

        UUID deviceId = userDeviceRepository.findByUserId(user.getId()).get(0).getId();

        mockMvc.perform(delete(DEVICES_URL + "/" + deviceId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeDevice_validToken_otherUsersDevice_returns403() throws Exception {
        User owner  = createActiveUser("owner@example.com", "Password123!");
        User other  = createActiveUser("other@example.com", "Password123!");
        registerKnownDevice(owner);

        UUID deviceId = userDeviceRepository.findByUserId(owner.getId()).get(0).getId();

        mockMvc.perform(delete(DEVICES_URL + "/" + deviceId)
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeDevice_noToken_returns401() throws Exception {
        mockMvc.perform(delete(DEVICES_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildExpiredToken(User user) {
        // Use the test signing key but set expiration in the past
        Key key = Keys.hmacShaKeyFor(
                io.jsonwebtoken.io.Decoders.BASE64.decode(
                        "dGVzdC1zZWNyZXQtZm9yLWludGVncmF0aW9uLXRlc3RzLW9ubHktZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9uIQ=="));
        return Jwts.builder()
                .setIssuer("shopwave-test")
                .setSubject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .setIssuedAt(new Date(System.currentTimeMillis() - 10_000))
                .setExpiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    private String buildTokenWithWrongIssuer(User user) {
        Key key = Keys.hmacShaKeyFor(
                io.jsonwebtoken.io.Decoders.BASE64.decode(
                        "dGVzdC1zZWNyZXQtZm9yLWludGVncmF0aW9uLXRlc3RzLW9ubHktZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9uIQ=="));
        return Jwts.builder()
                .setIssuer("evil-issuer")
                .setSubject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }
}
