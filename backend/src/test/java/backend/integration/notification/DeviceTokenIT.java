package backend.integration.notification;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeviceTokenIT extends AbstractIntegrationIT {

    // user_devices is cleaned by AbstractIntegrationIT.cleanDatabase()

    private String tokenBody(String platform, String token) throws Exception {
        return objectMapper.writeValueAsString(Map.of("platform", platform, "token", token));
    }

    // ── POST /devices/push-token ───────────────────────────────────────────────

    @Test
    void registerToken_android_returns204() throws Exception {
        User user = createActiveUser("dt-android@example.com", "Password1!");
        registerKnownDevice(user);

        mockMvc.perform(post("/devices/push-token")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("ANDROID", "fcm-token-android-abc123")))
                .andExpect(status().isNoContent());

        // The token must be persisted onto the user's device row (204 returns no body).
        assertEquals("fcm-token-android-abc123",
                userDeviceRepository.findAll().get(0).getFcmToken());
    }

    @Test
    void registerToken_ios_returns204() throws Exception {
        User user = createActiveUser("dt-ios@example.com", "Password1!");
        registerKnownDevice(user);

        mockMvc.perform(post("/devices/push-token")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("IOS", "apns-token-ios-xyz789")))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerToken_web_returns204() throws Exception {
        User user = createActiveUser("dt-web@example.com", "Password1!");
        registerKnownDevice(user);

        mockMvc.perform(post("/devices/push-token")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("WEB", "web-push-subscription-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerToken_noDeviceRow_returns204() throws Exception {
        // Service logs a warning but never throws — still 204
        User user = createActiveUser("dt-nodev@example.com", "Password1!");

        mockMvc.perform(post("/devices/push-token")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("ANDROID", "fcm-token-no-device")))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerToken_invalidPlatform_returns400() throws Exception {
        User user = createActiveUser("dt-badplatform@example.com", "Password1!");

        mockMvc.perform(post("/devices/push-token")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("BLACKBERRY", "some-token")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerToken_blankToken_returns400() throws Exception {
        User user = createActiveUser("dt-blanktoken@example.com", "Password1!");

        mockMvc.perform(post("/devices/push-token")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("ANDROID", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerToken_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/devices/push-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("ANDROID", "some-fcm-token")))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /devices/push-token/{token} ────────────────────────────────────

    @Test
    void revokeToken_existingToken_returns204() throws Exception {
        User user = createActiveUser("dt-revoke@example.com", "Password1!");
        registerKnownDevice(user);

        // Register first so there is a token to revoke
        mockMvc.perform(post("/devices/push-token")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("ANDROID", "fcm-revoke-test-token")))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/devices/push-token/fcm-revoke-test-token")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());

        // The token must be cleared from the device row after revocation.
        assertNull(userDeviceRepository.findAll().get(0).getFcmToken(),
                "Revoked push token should be cleared in the database");
    }

    @Test
    void revokeToken_nonExistentToken_returns204() throws Exception {
        // Service silently no-ops — still 204
        User user = createActiveUser("dt-revoke-missing@example.com", "Password1!");

        mockMvc.perform(delete("/devices/push-token/does-not-exist-token")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());
    }

    @Test
    void revokeToken_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/devices/push-token/some-token"))
                .andExpect(status().isUnauthorized());
    }
}
