package backend.integration.auth;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.services.intf.notification.SmsService;
import com.fasterxml.jackson.databind.JsonNode;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MfaIT extends AbstractIntegrationIT {

    @MockitoBean
    SmsService smsService;

    private static final String PASSWORD = "Password123!";
    private static final String PHONE    = "+15551234567";


    // ─── Authentication guard ─────────────────────────────────────────────────

    @Test
    void listMethods_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/mfa"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Email enrollment ─────────────────────────────────────────────────────

    @Test
    void enrollEmail_fullFlow_credentialIsVerified() throws Exception {
        User user  = createActiveUser("email-mfa@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));
        String userId = user.getId().toString();

        mockMvc.perform(post("/mfa/enroll/email")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        String code = cacheService.get("mfa:otp:email:" + userId);
        assertNotNull(code, "OTP should be stored in Redis after initiation");
        assertTrue(code.matches("\\d{6}"));

        mockMvc.perform(post("/mfa/verify/email")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/mfa")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("EMAIL"))
                .andExpect(jsonPath("$.data[0].verified").value(true));
    }

    @Test
    void enrollEmail_wrongCode_returns400() throws Exception {
        User user  = createActiveUser("email-wrong@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(post("/mfa/enroll/email")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(post("/mfa/verify/email")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollEmail_expiredOtp_returns400() throws Exception {
        User user  = createActiveUser("email-exp@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));
        String userId = user.getId().toString();

        mockMvc.perform(post("/mfa/enroll/email")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        // Simulate expiry by deleting the Redis key
        cacheService.getAndDelete("mfa:otp:email:" + userId);

        mockMvc.perform(post("/mfa/verify/email")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "123456"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollEmail_twice_replacesOldCredential() throws Exception {
        User user  = createActiveUser("email-dup@example.com", PASSWORD);
        String token  = bearer(accessTokenFor(user));
        String userId = user.getId().toString();

        // First enrollment
        mockMvc.perform(post("/mfa/enroll/email")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
        String code1 = cacheService.get("mfa:otp:email:" + userId);
        mockMvc.perform(post("/mfa/verify/email")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code1))))
                .andExpect(status().isOk());

        // Second enrollment (re-enroll)
        mockMvc.perform(post("/mfa/enroll/email")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
        String code2 = cacheService.get("mfa:otp:email:" + userId);
        mockMvc.perform(post("/mfa/verify/email")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code2))))
                .andExpect(status().isOk());

        // Should still be exactly 1 EMAIL credential
        mockMvc.perform(get("/mfa").header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type=='EMAIL')]", hasSize(1)));
    }

    // ─── SMS enrollment ───────────────────────────────────────────────────────

    @Test
    void enrollSms_fullFlow_credentialIsVerified() throws Exception {
        User user  = createActiveUser("sms-mfa@example.com", PASSWORD);
        String token  = bearer(accessTokenFor(user));
        String userId = user.getId().toString();

        mockMvc.perform(post("/mfa/enroll/sms")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", PHONE))))
                .andExpect(status().isOk());

        String json = cacheService.get("mfa:otp:sms:" + userId);
        assertNotNull(json, "SMS OTP payload should be stored in Redis");
        String code = (String) objectMapper.readValue(json, Map.class).get("code");

        mockMvc.perform(post("/mfa/verify/sms")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/mfa").header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("SMS"))
                .andExpect(jsonPath("$.data[0].verified").value(true));
    }

    @Test
    void enrollSms_invalidPhone_returns400() throws Exception {
        User user  = createActiveUser("sms-inv@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(post("/mfa/enroll/sms")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", "5551234567"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollSms_wrongCode_returns400() throws Exception {
        User user  = createActiveUser("sms-wrong@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(post("/mfa/enroll/sms")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", PHONE))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/mfa/verify/sms")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                .andExpect(status().isBadRequest());
    }

    // ─── TOTP enrollment ──────────────────────────────────────────────────────

    @Test
    void enrollTotp_fullFlow_returnsBackupCodes() throws Exception {
        User user  = createActiveUser("totp-mfa@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        String initiateBody = mockMvc.perform(post("/mfa/enroll/totp")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").isNotEmpty())
                .andExpect(jsonPath("$.data.otpAuthUri").value(containsString("otpauth://")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString();

        JsonNode data   = objectMapper.readTree(initiateBody).get("data");
        String secret   = data.get("secret").asText();
        String totpCode = generateTotpCode(secret);

        mockMvc.perform(post("/mfa/verify/totp")
                        .header("Authorization", token)
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", totpCode))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.data.backupCodes", hasSize(8)));

        mockMvc.perform(get("/mfa").header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("TOTP"))
                .andExpect(jsonPath("$.data[0].verified").value(true));
    }

    @Test
    void enrollTotp_expiredPendingSecret_returns400() throws Exception {
        User user  = createActiveUser("totp-exp@example.com", PASSWORD);
        String token  = bearer(accessTokenFor(user));
        String userId = user.getId().toString();

        mockMvc.perform(post("/mfa/enroll/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        // Simulate expiry
        cacheService.getAndDelete("mfa:totp:pending:" + userId);

        mockMvc.perform(post("/mfa/verify/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "123456"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollTotp_wrongCode_returns400() throws Exception {
        User user  = createActiveUser("totp-wrong@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(post("/mfa/enroll/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(post("/mfa/verify/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollTotp_wrongCodeThenCorrect_succeedsWithoutReinitiating() throws Exception {
        User user  = createActiveUser("totp-retry@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        String initiateBody = mockMvc.perform(post("/mfa/enroll/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(initiateBody).get("data").get("secret").asText();

        // First attempt with a wrong code must NOT destroy the pending secret.
        mockMvc.perform(post("/mfa/verify/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                .andExpect(status().isBadRequest());

        // Retrying with the correct code (same secret, no re-initiation) succeeds.
        mockMvc.perform(post("/mfa/verify/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", generateTotpCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backupCodes", hasSize(8)));
    }

    @Test
    void enrollTotp_twice_replacesOldCredential() throws Exception {
        User user  = createActiveUser("totp-dup@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        // First enrollment
        enrollTotpCompletely(token);

        // Second enrollment
        enrollTotpCompletely(token);

        mockMvc.perform(get("/mfa").header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type=='TOTP')]", hasSize(1)));
    }

    // ─── removeMethod ─────────────────────────────────────────────────────────

    @Test
    void removeMethod_enrolledMethod_returns204() throws Exception {
        User user  = createActiveUser("del-mfa@example.com", PASSWORD);
        String token  = bearer(accessTokenFor(user));
        String userId = user.getId().toString();

        mockMvc.perform(post("/mfa/enroll/email")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
        String code = cacheService.get("mfa:otp:email:" + userId);
        mockMvc.perform(post("/mfa/verify/email")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/mfa/EMAIL")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/mfa").header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void removeMethod_notEnrolled_returns404() throws Exception {
        User user  = createActiveUser("del-none@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(delete("/mfa/TOTP")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeMethod_invalidType_returns400() throws Exception {
        User user  = createActiveUser("del-bad@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        // Unknown enum value must be a clean 400, not a 500.
        mockMvc.perform(delete("/mfa/CARRIER_PIGEON")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ─── Rate limiting ─────────────────────────────────────────────────────────

    @Test
    void enrollSms_exceedsPerUserLimit_returns429() throws Exception {
        User user  = createActiveUser("sms-flood@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        // Per-user SMS budget is 5 within the window; the 6th send must be rejected so a
        // single account cannot be used to run up Twilio costs or bomb a number.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/mfa/enroll/sms")
                            .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("phoneNumber", "+1555000" + (1000 + i)))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/mfa/enroll/sms")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", "+15550009999"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void verifyTotp_exceedsAttemptLimit_returns429() throws Exception {
        User user  = createActiveUser("totp-hammer@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(post("/mfa/enroll/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        // The pending secret survives wrong guesses, so verify must be throttled. 10 attempts
        // are allowed (each a wrong-code 400); the 11th must be rejected before reaching the service.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/mfa/verify/totp")
                            .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/mfa/verify/totp")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                .andExpect(status().isTooManyRequests());
    }

    // ─── Enable / disable ──────────────────────────────────────────────────────

    @Test
    void newlyEnrolledMethod_isEnabledByDefault() throws Exception {
        User user  = createActiveUser("enabled-default@example.com", PASSWORD);
        String token  = bearer(accessTokenFor(user));
        String userId = user.getId().toString();

        mockMvc.perform(post("/mfa/enroll/email")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
        String code = cacheService.get("mfa:otp:email:" + userId);
        mockMvc.perform(post("/mfa/verify/email")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/mfa").header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].enabled").value(true));
    }

    @Test
    void updateMethod_disableThenEnableTotp_reflectedInSettings() throws Exception {
        User user  = createActiveUser("totp-toggle@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        enrollTotpCompletely(token);

        // Disable — the credential (and its secret) is retained, just turned off.
        mockMvc.perform(patch("/mfa/TOTP")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("TOTP"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get("/mfa").header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(jsonPath("$.data[0].type").value("TOTP"))
                .andExpect(jsonPath("$.data[0].enabled").value(false));

        // Re-enable.
        mockMvc.perform(patch("/mfa/TOTP")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void updateMethod_disableSms_reflectedInSettings() throws Exception {
        User user  = createActiveUser("sms-toggle@example.com", PASSWORD);
        String token  = bearer(accessTokenFor(user));

        enrollSmsCompletely(token, user.getId(), PHONE);

        mockMvc.perform(patch("/mfa/SMS")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("SMS"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void updateMethod_notEnrolled_returns404() throws Exception {
        User user  = createActiveUser("toggle-none@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(patch("/mfa/TOTP")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMethod_missingEnabledField_returns400() throws Exception {
        User user  = createActiveUser("toggle-bad@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(patch("/mfa/TOTP")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unenrollTotp_afterEnroll_returns204AndRemoves() throws Exception {
        User user  = createActiveUser("totp-unenroll@example.com", PASSWORD);
        String token = bearer(accessTokenFor(user));

        enrollTotpCompletely(token);

        mockMvc.perform(delete("/mfa/TOTP")
                        .header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/mfa").header("Authorization", token).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generateTotpCode(String secret) throws Exception {
        DefaultCodeGenerator gen = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        long bucket = new SystemTimeProvider().getTime() / 30;
        return gen.generate(secret, bucket);
    }

    private void enrollTotpCompletely(String bearerToken) throws Exception {
        String body = mockMvc.perform(post("/mfa/enroll/totp")
                        .header("Authorization", bearerToken).header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secret   = objectMapper.readTree(body).get("data").get("secret").asText();
        String totpCode = generateTotpCode(secret);

        mockMvc.perform(post("/mfa/verify/totp")
                        .header("Authorization", bearerToken).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", totpCode))))
                .andExpect(status().isOk());
    }

    private void enrollSmsCompletely(String bearerToken, UUID userId, String phone) throws Exception {
        mockMvc.perform(post("/mfa/enroll/sms")
                        .header("Authorization", bearerToken).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", phone))))
                .andExpect(status().isOk());

        String json = cacheService.get("mfa:otp:sms:" + userId);
        String code = (String) objectMapper.readValue(json, Map.class).get("code");

        mockMvc.perform(post("/mfa/verify/sms")
                        .header("Authorization", bearerToken).header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk());
    }
}
