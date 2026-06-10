package backend.integration.admin;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers SearchAdminController (POST /admin/search/reindex) — ADMIN-only.
 *
 * IndexVersionManager.rolloverIndex is a no-op (app.elasticsearch.versioning.enabled=false).
 * ProductIndexingService.reindexAll iterates an empty DB — no tasks submitted.
 *
 * Rate limit: 1 reindex per hour globally (key ratelimit:admin:reindex:global).
 * Redis is flushed before each test, so limit resets per test.
 */
class SearchAdminIT extends AbstractIntegrationIT {

    // users table cleaned by AbstractIntegrationIT.cleanDatabase()

    private User createAdmin(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Password1!"));
        u.setRole(UserRole.ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    // ── POST /admin/search/reindex ─────────────────────────────────────────────

    @Test
    void reindex_admin_returns200() throws Exception {
        User admin = createAdmin("sa-admin@example.com");

        mockMvc.perform(post("/admin/search/reindex")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message")
                        .value("Index rollover and full reindex triggered successfully"));
    }

    @Test
    void reindex_rateLimitedOnSecondCall_returns429() throws Exception {
        User admin = createAdmin("sa-admin-rl@example.com");
        String auth = bearer(accessTokenFor(admin));

        // First call: counter hits 1 — within limit of 1
        mockMvc.perform(post("/admin/search/reindex")
                        .header("Authorization", auth))
                .andExpect(status().isOk());

        // Second call: counter hits 2 — exceeds limit of 1
        mockMvc.perform(post("/admin/search/reindex")
                        .header("Authorization", auth))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void reindex_nonAdmin_returns403() throws Exception {
        User user = createAdmin("sa-user-403@example.com");
        // Override to USER role
        user.setRole(UserRole.USER);
        userRepository.save(user);

        mockMvc.perform(post("/admin/search/reindex")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reindex_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/search/reindex"))
                .andExpect(status().isUnauthorized());
    }
}
