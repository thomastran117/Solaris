package backend.integration.auth;

import backend.integration.AbstractIntegrationIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthLoginIT extends AbstractIntegrationIT {

    private static final String URL = "/auth/login";
    private static final String PASSWORD = "Password123!";

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void login_activeUser_returns200WithAuthResponse() throws Exception {
        createActiveUser("active@example.com", PASSWORD);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(loginBody("active@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(notNullValue()))
                .andExpect(jsonPath("$.data.email").value("active@example.com"))
                .andExpect(jsonPath("$.data.usertype").value("USER"))
                .andExpect(jsonPath("$.data.userid").value(notNullValue()))
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")));
    }

    // ── Credential failures ───────────────────────────────────────────────────

    @Test
    void login_wrongPassword_returns401() throws Exception {
        createActiveUser("user@example.com", PASSWORD);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(loginBody("user@example.com", "WrongPass1!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_nonExistentEmail_returns401() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(loginBody("ghost@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ── Account status failures ───────────────────────────────────────────────

    @Test
    void login_pendingVerification_returns403() throws Exception {
        createPendingUser("pending@example.com", PASSWORD);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(loginBody("pending@example.com", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_suspendedAccount_returns403() throws Exception {
        createSuspendedUser("suspended@example.com", PASSWORD);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(loginBody("suspended@example.com", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ── Bean validation ───────────────────────────────────────────────────────

    @Test
    void login_missingCaptcha_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("email", "user@example.com", "password", PASSWORD));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_malformedEmail_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("not-an-email", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_weakPassword_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("user@example.com", "weak")))
                .andExpect(status().isBadRequest());
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    @Test
    void login_emailRateLimitExceeded_returns429() throws Exception {
        // Default loginPerEmailLimit = 5; the 6th request within the window must be rejected.
        createActiveUser("ratelimit@example.com", PASSWORD);
        String wrongBody = loginBody("ratelimit@example.com", "WrongPass1!");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("User-Agent", TEST_USER_AGENT)
                            .content(wrongBody))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(wrongBody))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_accountLockedOut_returns429() throws Exception {
        // Seed the lockout key directly to simulate an account locked after repeated failures.
        String email = "locked@example.com";
        createActiveUser(email, PASSWORD);
        cacheService.set("lockout:until:" + email, "1", 900);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", TEST_USER_AGENT)
                        .content(loginBody(email, PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }
}
