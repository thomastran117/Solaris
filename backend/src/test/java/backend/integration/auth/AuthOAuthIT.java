package backend.integration.auth;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.UserStatus;
import backend.models.other.OAuthUser;
import backend.security.oauth.InvalidOAuthTokenException;
import backend.security.oauth.OAuthProviderNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OAuth login endpoints (Google, Microsoft, Apple).
 *
 * OAuthService is @MockitoBean'd in AbstractIntegrationIT so no real provider calls are made.
 * These tests verify the full controller → authService → userRepository → response path.
 */
class AuthOAuthIT extends AbstractIntegrationIT {

    private static final String GOOGLE_URL    = "/auth/google";
    private static final String MICROSOFT_URL = "/auth/microsoft";
    private static final String APPLE_URL     = "/auth/apple";

    // ── Google ────────────────────────────────────────────────────────────────

    @Test
    void google_validToken_newUser_returns200AndCreatesUser() throws Exception {
        when(oauthService.verifyGoogleToken(any()))
                .thenReturn(new OAuthUser("g-sub-1", "guser@example.com", "G User", "google"));

        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(oauthBody("mock-google-token")))
                .andExpect(status().isOk());

        assertTrue(userRepository.findByEmail("guser@example.com").isPresent());
    }

    @Test
    void google_validToken_existingUser_returns200() throws Exception {
        createActiveUser("gexist@example.com", "Password123!");

        when(oauthService.verifyGoogleToken(any()))
                .thenReturn(new OAuthUser("g-sub-2", "gexist@example.com", "G Exist", "google"));

        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(oauthBody("mock-google-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("gexist@example.com"));
    }

    @Test
    void google_providerNotConfigured_returns503() throws Exception {
        when(oauthService.verifyGoogleToken(any()))
                .thenThrow(new OAuthProviderNotConfiguredException("Google"));

        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("any-token")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void google_invalidToken_returns401() throws Exception {
        when(oauthService.verifyGoogleToken(any()))
                .thenThrow(new InvalidOAuthTokenException("bad token"));

        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("bad-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void google_missingIdToken_returns400() throws Exception {
        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── Microsoft ─────────────────────────────────────────────────────────────

    @Test
    void microsoft_validToken_newUser_returns200() throws Exception {
        when(oauthService.verifyMicrosoftToken(any()))
                .thenReturn(new OAuthUser("ms-sub-1", "msuser@example.com", "MS User", "microsoft"));

        mockMvc.perform(post(MICROSOFT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(oauthBody("mock-ms-token")))
                .andExpect(status().isOk());

        assertTrue(userRepository.findByEmail("msuser@example.com").isPresent());
    }

    @Test
    void microsoft_invalidToken_returns401() throws Exception {
        when(oauthService.verifyMicrosoftToken(any()))
                .thenThrow(new InvalidOAuthTokenException("bad ms token"));

        mockMvc.perform(post(MICROSOFT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("bad-token")))
                .andExpect(status().isUnauthorized());
    }

    // ── Apple ─────────────────────────────────────────────────────────────────

    @Test
    void apple_validToken_newUser_returns200() throws Exception {
        when(oauthService.verifyAppleToken(any()))
                .thenReturn(new OAuthUser("ap-sub-1", "apuser@example.com", "AP User", "apple"));

        mockMvc.perform(post(APPLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(oauthBody("mock-apple-token")))
                .andExpect(status().isOk());

        assertTrue(userRepository.findByEmail("apuser@example.com").isPresent());
    }

    @Test
    void apple_providerNotConfigured_returns503() throws Exception {
        when(oauthService.verifyAppleToken(any()))
                .thenThrow(new OAuthProviderNotConfiguredException("Apple"));

        mockMvc.perform(post(APPLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("any-token")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void apple_invalidToken_returns401() throws Exception {
        when(oauthService.verifyAppleToken(any()))
                .thenThrow(new InvalidOAuthTokenException("bad apple token"));

        mockMvc.perform(post(APPLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("bad-token")))
                .andExpect(status().isUnauthorized());
    }

    // ── Microsoft ─────────────────────────────────────────────────────────────

    @Test
    void microsoft_providerNotConfigured_returns503() throws Exception {
        when(oauthService.verifyMicrosoftToken(any()))
                .thenThrow(new OAuthProviderNotConfiguredException("Microsoft"));

        mockMvc.perform(post(MICROSOFT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oauthBody("any-token")))
                .andExpect(status().isServiceUnavailable());
    }

    // ── Account state ─────────────────────────────────────────────────────────

    @Test
    void oauth_createsUserWithActiveStatus() throws Exception {
        when(oauthService.verifyGoogleToken(any()))
                .thenReturn(new OAuthUser("g-sub-active", "oauthactive@example.com", "OAuth Active", "google"));

        mockMvc.perform(post(GOOGLE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(oauthBody("mock-token")))
                .andExpect(status().isOk());

        User created = userRepository.findByEmail("oauthactive@example.com").orElseThrow();
        assertEquals(UserStatus.ACTIVE, created.getStatus());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String oauthBody(String idToken) throws Exception {
        return objectMapper.writeValueAsString(Map.of("idToken", idToken));
    }
}
