package backend.integration.preferences;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserPreferenceIT extends AbstractIntegrationIT {


    // ── GET /users/me/preferences ──────────────────────────────────────────────

    @Test
    void getPreferences_noExistingRow_returnsDefaultFalse() throws Exception {
        User user = createActiveUser("pref-get@example.com", "Password1!");

        mockMvc.perform(get("/users/me/preferences")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingOptOut").value(false));
    }

    @Test
    void getPreferences_afterSettingOptOut_returnsTrue() throws Exception {
        User user = createActiveUser("pref-track-verify@example.com", "Password1!");

        mockMvc.perform(put("/users/me/preferences/tracking")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("optOut", true))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/me/preferences")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingOptOut").value(true));
    }

    @Test
    void getPreferences_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/users/me/preferences"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /users/me/preferences/tracking ────────────────────────────────────

    @Test
    void setTracking_optOut_returns204() throws Exception {
        User user = createActiveUser("pref-optout@example.com", "Password1!");

        mockMvc.perform(put("/users/me/preferences/tracking")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("optOut", true))))
                .andExpect(status().isNoContent());

        // The opt-out must be committed to the user_preference row (204 returns no body to trust).
        Boolean optOut = jdbcTemplate.queryForObject(
                "SELECT tracking_opt_out FROM user_preference", Boolean.class);
        assertEquals(Boolean.TRUE, optOut, "Tracking opt-out should be persisted");
    }

    @Test
    void setTracking_optIn_returns204() throws Exception {
        User user = createActiveUser("pref-optin@example.com", "Password1!");

        mockMvc.perform(put("/users/me/preferences/tracking")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("optOut", false))))
                .andExpect(status().isNoContent());
    }

    @Test
    void setTracking_missingOptOut_returns400() throws Exception {
        User user = createActiveUser("pref-badtrack@example.com", "Password1!");

        mockMvc.perform(put("/users/me/preferences/tracking")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setTracking_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/users/me/preferences/tracking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("optOut", true))))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /users/me/preferences/notifications ────────────────────────────────

    @Test
    void getNotifications_noExistingRow_returnsDefaults() throws Exception {
        User user = createActiveUser("pref-notif-default@example.com", "Password1!");

        mockMvc.perform(get("/users/me/preferences/notifications")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushEnabled").value(false))
                .andExpect(jsonPath("$.data.smsEnabled").value(false))
                .andExpect(jsonPath("$.data.smsPhoneNumber").value(nullValue()));
    }

    @Test
    void getNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/users/me/preferences/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /users/me/preferences/notifications ──────────────────────────────

    @Test
    void updateNotifications_enablePush_returns200() throws Exception {
        User user = createActiveUser("pref-push@example.com", "Password1!");

        mockMvc.perform(patch("/users/me/preferences/notifications")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pushEnabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushEnabled").value(true))
                .andExpect(jsonPath("$.data.smsEnabled").value(false));
    }

    @Test
    void updateNotifications_setSmsWithPhone_returns200() throws Exception {
        User user = createActiveUser("pref-sms@example.com", "Password1!");

        mockMvc.perform(patch("/users/me/preferences/notifications")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("smsEnabled", true, "smsPhoneNumber", "+12025551234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smsEnabled").value(true))
                .andExpect(jsonPath("$.data.smsPhoneNumber").value("+12025551234"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT sms_enabled, sms_phone_number FROM user_preference");
        assertEquals(Boolean.TRUE, row.get("sms_enabled"));
        assertEquals("+12025551234", row.get("sms_phone_number"));
    }

    @Test
    void updateNotifications_clearPhone_returnsNullPhone() throws Exception {
        User user = createActiveUser("pref-clearphone@example.com", "Password1!");

        mockMvc.perform(patch("/users/me/preferences/notifications")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("smsPhoneNumber", "+12025559999"))))
                .andExpect(status().isOk());

        // Blank string triggers null-out in service: smsPhoneNumber.isBlank() → null
        mockMvc.perform(patch("/users/me/preferences/notifications")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("smsPhoneNumber", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smsPhoneNumber").value(nullValue()));
    }

    @Test
    void updateNotifications_partialUpdate_doesNotOverwriteOtherFields() throws Exception {
        User user = createActiveUser("pref-partial@example.com", "Password1!");

        // First enable both
        mockMvc.perform(patch("/users/me/preferences/notifications")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("pushEnabled", true, "smsEnabled", true))))
                .andExpect(status().isOk());

        // Disable push only — smsEnabled must stay true
        mockMvc.perform(patch("/users/me/preferences/notifications")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pushEnabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushEnabled").value(false))
                .andExpect(jsonPath("$.data.smsEnabled").value(true));
    }

    @Test
    void updateNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/users/me/preferences/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pushEnabled", true))))
                .andExpect(status().isUnauthorized());
    }
}
