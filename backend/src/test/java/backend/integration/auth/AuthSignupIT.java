package backend.integration.auth;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthSignupIT extends AbstractIntegrationIT {

    private static final String SIGNUP_URL = "/auth/signup";
    private static final String VERIFY_URL = "/auth/verify";
    private static final String PASSWORD    = "Password123!";

    // ── Signup ────────────────────────────────────────────────────────────────

    @Test
    void signup_newEmail_returns201WithMessage() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("new@example.com", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.message").isNotEmpty());
    }

    @Test
    void signup_createsUserWithPendingVerificationStatus() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("pending@example.com", PASSWORD)))
                .andExpect(status().isCreated());

        Optional<User> saved = userRepository.findByEmail("pending@example.com");
        assertTrue(saved.isPresent());
        assertEquals(UserStatus.PENDING_VERIFICATION, saved.get().getStatus());
    }

    @Test
    void signup_duplicateEmail_returns409() throws Exception {
        createActiveUser("taken@example.com", PASSWORD);

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("taken@example.com", PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void signup_weakPassword_returns400() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("new@example.com", "weak")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_malformedEmail_returns400() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("not-an-email", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_missingCaptcha_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("email", "new@example.com", "password", PASSWORD));

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── Email verification ────────────────────────────────────────────────────

    @Test
    void verifyEmail_validToken_activatesAccountAndReturns200() throws Exception {
        // Signup creates the user + stores email:verify:{token} in Redis
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("toverify@example.com", PASSWORD)))
                .andExpect(status().isCreated());

        // Seed a known verification token directly so we control the value
        User user = userRepository.findByEmail("toverify@example.com").orElseThrow();
        String token = "test-verify-token-001";
        cacheService.set("email:verify:" + token, user.getId().toString(), 3600);

        mockMvc.perform(post(VERIFY_URL)
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").isNotEmpty());

        User updated = userRepository.findByEmail("toverify@example.com").orElseThrow();
        assertEquals(UserStatus.ACTIVE, updated.getStatus());
    }

    @Test
    void verifyEmail_invalidToken_returns400() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .param("token", "completely-bogus-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_expiredToken_returns400() throws Exception {
        // Nothing in Redis → treated as expired/invalid
        mockMvc.perform(post(VERIFY_URL)
                        .param("token", "expired-token-xyz"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_tokenIsConsumedAfterUse() throws Exception {
        User user = createPendingUser("consume@example.com", PASSWORD);
        String token = "consume-token-001";
        cacheService.set("email:verify:" + token, user.getId().toString(), 3600);

        // First use succeeds
        mockMvc.perform(post(VERIFY_URL).param("token", token))
                .andExpect(status().isOk());

        // Second use must fail (token deleted from Redis after first use)
        mockMvc.perform(post(VERIFY_URL).param("token", token))
                .andExpect(status().isBadRequest());
    }
}
