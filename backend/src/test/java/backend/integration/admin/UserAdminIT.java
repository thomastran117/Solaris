package backend.integration.admin;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserAdminIT extends AbstractIntegrationIT {

    // users table is cleaned by AbstractIntegrationIT.cleanDatabase()

    private User createAdmin(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Password1!"));
        u.setRole(UserRole.ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private String roleBody(String role) throws Exception {
        return objectMapper.writeValueAsString(Map.of("role", role));
    }

    // ── PUT /admin/users/{userId}/role ────────────────────────────────────────

    @Test
    void updateRole_toModerator_returns200() throws Exception {
        User admin = createAdmin("ua-admin@example.com");
        User target = createActiveUser("ua-target-mod@example.com", "Password1!");

        mockMvc.perform(put("/admin/users/" + target.getId() + "/role")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("MODERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message", containsString("MODERATOR")));
    }

    @Test
    void updateRole_toSupport_returns200() throws Exception {
        User admin = createAdmin("ua-admin-support@example.com");
        User target = createActiveUser("ua-target-support@example.com", "Password1!");

        mockMvc.perform(put("/admin/users/" + target.getId() + "/role")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("SUPPORT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message", containsString("SUPPORT")));
    }

    @Test
    void updateRole_demoteToUser_returns200() throws Exception {
        User admin = createAdmin("ua-admin-demote@example.com");

        User moderator = new User();
        moderator.setEmail("ua-mod-demote@example.com");
        moderator.setPassword(passwordEncoder.encode("Password1!"));
        moderator.setRole(UserRole.MODERATOR);
        moderator.setStatus(UserStatus.ACTIVE);
        userRepository.save(moderator);

        mockMvc.perform(put("/admin/users/" + moderator.getId() + "/role")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message", containsString("USER")));
    }

    @Test
    void updateRole_persistsRoleChange() throws Exception {
        User admin = createAdmin("ua-admin-persist@example.com");
        User target = createActiveUser("ua-target-persist@example.com", "Password1!");

        mockMvc.perform(put("/admin/users/" + target.getId() + "/role")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("SUPPORT")))
                .andExpect(status().isOk());

        User updated = userRepository.findById(target.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.SUPPORT, updated.getRole());
    }

    @Test
    void updateRole_unknownUser_returns404() throws Exception {
        User admin = createAdmin("ua-admin-404@example.com");

        mockMvc.perform(put("/admin/users/" + UUID.randomUUID() + "/role")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("MODERATOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRole_missingRole_returns400() throws Exception {
        User admin = createAdmin("ua-admin-badreq@example.com");
        User target = createActiveUser("ua-target-badreq@example.com", "Password1!");

        mockMvc.perform(put("/admin/users/" + target.getId() + "/role")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("ua-nonadmin@example.com", "Password1!");
        User target = createActiveUser("ua-target-forbidden@example.com", "Password1!");

        mockMvc.perform(put("/admin/users/" + target.getId() + "/role")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("MODERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/admin/users/" + UUID.randomUUID() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody("MODERATOR")))
                .andExpect(status().isUnauthorized());
    }
}
