package backend.integration.auth;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthRefreshIT extends AbstractIntegrationIT {

    private static final String REFRESH_URL = "/auth/refresh";
    private static final String LOGOUT_URL  = "/auth/logout";
    private static final String PASSWORD    = "Password123!";

    // ── Refresh happy path ────────────────────────────────────────────────────

    @Test
    void refresh_validCookie_returns200WithNewAccessToken() throws Exception {
        User user = createActiveUser("refresh@example.com", PASSWORD);
        String refreshToken = tokenService.generateRefreshToken(
                user.getId(), user.getRole().name(), user.getEmail());

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value(notNullValue()))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")));
    }

    @Test
    void refresh_rotatesToken_oldTokenIsInvalidated() throws Exception {
        User user = createActiveUser("rotate@example.com", PASSWORD);
        String oldToken = tokenService.generateRefreshToken(
                user.getId(), user.getRole().name(), user.getEmail());

        // First refresh succeeds and issues a new token
        MvcResult result = mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("refreshToken", oldToken)))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the new refresh cookie from the response
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        String newToken = extractRefreshTokenFromCookieHeader(setCookie);
        assertNotNull(newToken);
        assertNotEquals(oldToken, newToken);

        // Reusing the old token must now fail
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("refreshToken", oldToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_newTokenIsUsable() throws Exception {
        User user = createActiveUser("reuse@example.com", PASSWORD);
        String first = tokenService.generateRefreshToken(
                user.getId(), user.getRole().name(), user.getEmail());

        MvcResult r1 = mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("refreshToken", first)))
                .andExpect(status().isOk())
                .andReturn();

        String second = extractRefreshTokenFromCookieHeader(
                r1.getResponse().getHeader("Set-Cookie"));

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("refreshToken", second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value(notNullValue()));
    }

    // ── Refresh failure cases ─────────────────────────────────────────────────

    @Test
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post(REFRESH_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("refreshToken", "completely-bogus-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_revokedToken_returns401() throws Exception {
        User user = createActiveUser("revoked@example.com", PASSWORD);
        String token = tokenService.generateRefreshToken(
                user.getId(), user.getRole().name(), user.getEmail());
        tokenService.revokeRefreshToken(token);

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isUnauthorized());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_validCookie_returns200AndClearsCookie() throws Exception {
        User user = createActiveUser("logout@example.com", PASSWORD);
        String token = tokenService.generateRefreshToken(
                user.getId(), user.getRole().name(), user.getEmail());

        mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").isNotEmpty())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void logout_revokesToken_subsequentRefreshFails() throws Exception {
        User user = createActiveUser("logout2@example.com", PASSWORD);
        String token = tokenService.generateRefreshToken(
                user.getId(), user.getRole().name(), user.getEmail());

        mockMvc.perform(post(LOGOUT_URL)
                        .cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isOk());

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_noCookie_stillReturns200() throws Exception {
        mockMvc.perform(post(LOGOUT_URL))
                .andExpect(status().isOk());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String extractRefreshTokenFromCookieHeader(String setCookieHeader) {
        if (setCookieHeader == null) return null;
        for (String part : setCookieHeader.split(";")) {
            part = part.trim();
            if (part.startsWith("refreshToken=")) {
                return part.substring("refreshToken=".length());
            }
        }
        return null;
    }
}
