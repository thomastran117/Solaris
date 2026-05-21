package backend.integration.auth;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.http.DeviceType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests the POST /auth/verify-device endpoint.
 *
 * Device verification tokens are seeded directly into Redis using CacheService,
 * using the same key pattern and payload format that DeviceServiceImpl produces:
 *   key:   "device:verify:{token}"
 *   value: "{userId}|{fingerprint}|{browser}|{os}|{deviceType}|{ip}|{userAgent}"
 */
class AuthDeviceIT extends AbstractIntegrationIT {

    private static final String URL      = "/auth/verify-device";
    private static final String PASSWORD = "Password123!";

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void verifyDevice_validToken_returns200WithAuthResponse() throws Exception {
        User user = createActiveUser("devverify@example.com", PASSWORD);
        String token = seedDeviceToken(user);

        mockMvc.perform(post(URL).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(notNullValue()))
                .andExpect(jsonPath("$.data.email").value("devverify@example.com"))
                .andExpect(jsonPath("$.data.userid").value(notNullValue()))
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")));
    }

    @Test
    void verifyDevice_validToken_registersDeviceInDatabase() throws Exception {
        User user = createActiveUser("devregister@example.com", PASSWORD);
        String fingerprint = deviceService.computeFingerprint(TEST_USER_AGENT);
        String token = seedDeviceToken(user, fingerprint);

        long before = userDeviceRepository.count();

        mockMvc.perform(post(URL).param("token", token))
                .andExpect(status().isOk());

        // A new UserDevice row should have been persisted by recordDeviceSeen
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, userDeviceRepository.count());
    }

    // ── Failure cases ─────────────────────────────────────────────────────────

    @Test
    void verifyDevice_invalidToken_returns400() throws Exception {
        mockMvc.perform(post(URL).param("token", "bogus-device-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyDevice_expiredToken_returns400() throws Exception {
        // Nothing in Redis → treated as expired
        mockMvc.perform(post(URL).param("token", "expired-device-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyDevice_tokenIsConsumedAfterUse() throws Exception {
        User user = createActiveUser("devonce@example.com", PASSWORD);
        String token = seedDeviceToken(user);

        // First use succeeds
        mockMvc.perform(post(URL).param("token", token))
                .andExpect(status().isOk());

        // Second use must fail — token is deleted from Redis on first consume
        mockMvc.perform(post(URL).param("token", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyDevice_missingTokenParam_returns400() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isBadRequest());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Stores a device verification payload in Redis using the format expected by
     * DeviceServiceImpl.consumeDeviceVerificationToken():
     *   {userId}|{fingerprint}|{browser}|{os}|{deviceType}|{ip}|{userAgent}
     */
    private String seedDeviceToken(User user) {
        return seedDeviceToken(user, deviceService.computeFingerprint(TEST_USER_AGENT));
    }

    private String seedDeviceToken(User user, String fingerprint) {
        String token   = "test-device-token-" + user.getEmail().hashCode();
        String payload = user.getId() + "|" +
                fingerprint + "|" +
                "Chrome 120.0.0.0|Windows|" + DeviceType.DESKTOP.name() + "|127.0.0.1|" +
                TEST_USER_AGENT;
        cacheService.set("device:verify:" + token, payload, 600);
        return token;
    }
}
