package backend.integration.profile;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfileIT extends AbstractIntegrationIT {

    // All responses wrapped: { success, data: {...}, message, meta }

    // ── GET /profile ──────────────────────────────────────────────────────────

    @Test
    void getProfile_returnsProfileForAuthenticatedUser() throws Exception {
        User user = createActiveUser("profile-get@example.com", "Password1!");

        mockMvc.perform(get("/profile")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.email").value("profile-get@example.com"))
                .andExpect(jsonPath("$.data.tier").value("FREE"));
    }

    @Test
    void getProfile_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /profile ────────────────────────────────────────────────────────

    @Test
    void updateProfile_updatesFirstAndLastName() throws Exception {
        User user = createActiveUser("profile-update@example.com", "Password1!");

        Map<String, String> body = new LinkedHashMap<>();
        body.put("firstName", "Alice");
        body.put("lastName", "Smith");

        mockMvc.perform(patch("/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Alice"))
                .andExpect(jsonPath("$.data.lastName").value("Smith"))
                .andExpect(jsonPath("$.data.email").value("profile-update@example.com"));
    }

    @Test
    void updateProfile_updatesPhoneNumber() throws Exception {
        User user = createActiveUser("profile-phone@example.com", "Password1!");

        Map<String, String> body = new LinkedHashMap<>();
        body.put("phoneNumber", "+1 555-123-4567");

        mockMvc.perform(patch("/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNumber").value("+1 555-123-4567"));
    }

    @Test
    void updateProfile_returns400ForInvalidPhoneNumber() throws Exception {
        User user = createActiveUser("profile-badphone@example.com", "Password1!");

        Map<String, String> body = new LinkedHashMap<>();
        body.put("phoneNumber", "not-a-phone!!!");

        mockMvc.perform(patch("/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_returns401WhenUnauthenticated() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("firstName", "Bob");

        mockMvc.perform(patch("/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
