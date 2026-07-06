package backend.integration.company;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.repositories.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyFollowIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;

    @AfterEach
    void cleanFollows() {
        try { jdbcTemplate.execute("DELETE FROM company_follows"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createActiveCompany(User owner, String name) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName(name);
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private Company createInactiveCompany(User owner, String name) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName(name);
        c.setStatus(CompanyStatus.INACTIVE);
        return companyRepository.save(c);
    }

    // ── POST /companies/{id}/follow ───────────────────────────────────────────

    @Test
    void follow_returns200WhenFollowingActiveCompany() throws Exception {
        User owner = createActiveUser("follow-owner@example.com", "Password1!");
        User follower = createActiveUser("follow-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Follow Me Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationsEnabled").value(true));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company_follows", Integer.class);
        assertEquals(1, rows, "Follow relationship should be persisted");
    }

    @Test
    void follow_returns409WhenAlreadyFollowing() throws Exception {
        User owner = createActiveUser("follow-dup-owner@example.com", "Password1!");
        User follower = createActiveUser("follow-dup-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Dup Follow Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void follow_returns400ForInactiveCompany() throws Exception {
        User owner = createActiveUser("follow-inactive-owner@example.com", "Password1!");
        User follower = createActiveUser("follow-inactive-user@example.com", "Password1!");
        Company company = createInactiveCompany(owner, "Inactive Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void follow_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("follow-unauth-owner@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Unauth Follow Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /companies/{id}/follow ─────────────────────────────────────────

    @Test
    void unfollow_returns204AfterFollowing() throws Exception {
        User owner = createActiveUser("unfollow-owner@example.com", "Password1!");
        User follower = createActiveUser("unfollow-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Unfollow Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company_follows", Integer.class);
        assertEquals(0, rows, "Unfollow should remove the relationship from the database");
    }

    @Test
    void unfollow_returns204EvenWhenNotFollowing() throws Exception {
        User owner = createActiveUser("unfollow-noop-owner@example.com", "Password1!");
        User user = createActiveUser("unfollow-noop-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Noop Unfollow Co");

        mockMvc.perform(delete("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void unfollow_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("unfollow-unauth-owner@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Unauth Unfollow Co");

        mockMvc.perform(delete("/companies/{id}/follow", company.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{id}/follow/status ─────────────────────────────────────

    @Test
    void getFollowStatus_returns200WithFollowingFalseWhenNotFollowing() throws Exception {
        User owner = createActiveUser("follow-status-owner@example.com", "Password1!");
        User user = createActiveUser("follow-status-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Status Co");

        mockMvc.perform(get("/companies/{id}/follow/status", company.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(false));
    }

    @Test
    void getFollowStatus_returns200WithFollowingTrueAfterFollow() throws Exception {
        User owner = createActiveUser("follow-status2-owner@example.com", "Password1!");
        User follower = createActiveUser("follow-status2-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Status After Follow Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(get("/companies/{id}/follow/status", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true));
    }

    @Test
    void getFollowStatus_returns200ForUnauthenticatedWithFollowingFalse() throws Exception {
        User owner = createActiveUser("follow-status-anon-owner@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Anon Status Co");

        mockMvc.perform(get("/companies/{id}/follow/status", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(false));
    }

    // ── PATCH /companies/{id}/follow ──────────────────────────────────────────

    @Test
    void patchFollow_returns200UpdatingNotificationPreference() throws Exception {
        User owner = createActiveUser("follow-patch-owner@example.com", "Password1!");
        User follower = createActiveUser("follow-patch-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Patch Follow Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/companies/{id}/follow", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationsEnabled\":false}")
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationsEnabled").value(false));

        Boolean notif = jdbcTemplate.queryForObject(
                "SELECT notifications_enabled FROM company_follows", Boolean.class);
        assertEquals(Boolean.FALSE, notif, "Notification preference change should be persisted");
    }

    @Test
    void patchFollow_returns400WhenNotificationsEnabledMissing() throws Exception {
        User owner = createActiveUser("follow-patch-null-owner@example.com", "Password1!");
        User follower = createActiveUser("follow-patch-null-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Null Patch Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/companies/{id}/follow", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchFollow_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("follow-patch-unauth-owner@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Unauth Patch Co");

        mockMvc.perform(patch("/companies/{id}/follow", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationsEnabled\":false}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /me/following ─────────────────────────────────────────────────────

    @Test
    void listFollowing_returnsEmptyWhenNotFollowingAnyone() throws Exception {
        User user = createActiveUser("following-empty@example.com", "Password1!");

        mockMvc.perform(get("/me/following")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listFollowing_returnsFollowedCompanies() throws Exception {
        User owner = createActiveUser("following-owner@example.com", "Password1!");
        User follower = createActiveUser("following-user@example.com", "Password1!");
        Company company = createActiveCompany(owner, "Followed Co");

        mockMvc.perform(post("/companies/{id}/follow", company.getId())
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(get("/me/following")
                        .header("Authorization", bearer(accessTokenFor(follower)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void listFollowing_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/me/following")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }
}
