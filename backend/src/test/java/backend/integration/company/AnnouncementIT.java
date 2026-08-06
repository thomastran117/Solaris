package backend.integration.company;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnnouncementIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner, String name) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName(name);
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addMember(User user, Company company, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Map<String, Object> createAnnouncementBody(String title, String body) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("title", title);
        req.put("body", body);
        req.put("type", "GENERAL");
        return req;
    }

    private String createAnnouncementAndGetId(Company company, User owner, String title) throws Exception {
        String response = mockMvc.perform(post("/companies/{id}/announcements", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAnnouncementBody(title, "Body text")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.path("data").path("id").asText();
    }

    // ── POST /companies/{id}/announcements ────────────────────────────────────

    @Test
    void createAnnouncement_returns201ForOwner() throws Exception {
        User owner = createActiveUser("ann-create@example.com", "Password1!");
        Company company = createCompany(owner, "Announce Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{id}/announcements", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createAnnouncementBody("Big Sale!", "Check it out.")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Big Sale!"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company_announcements WHERE title = 'Big Sale!'", Integer.class);
        assertEquals(1, rows, "Announcement should be persisted");
    }

    @Test
    void createAnnouncement_returns201ForManager() throws Exception {
        User owner = createActiveUser("ann-create-mgr-owner@example.com", "Password1!");
        User manager = createActiveUser("ann-create-mgr@example.com", "Password1!");
        Company company = createCompany(owner, "Manager Announce Co");
        addMember(manager, company, CompanyRole.MANAGER);

        mockMvc.perform(post("/companies/{id}/announcements", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createAnnouncementBody("Manager News", "Details here.")))
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated());
    }

    @Test
    void createAnnouncement_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("ann-create-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("ann-create-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Emp Announce Co");
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{id}/announcements", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createAnnouncementBody("Sneaky Post", "Nope.")))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAnnouncement_returns400WhenTitleMissing() throws Exception {
        User owner = createActiveUser("ann-create-notitle@example.com", "Password1!");
        Company company = createCompany(owner, "No Title Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{id}/announcements", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Some body\",\"type\":\"GENERAL\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAnnouncement_returns400WhenTypeMissing() throws Exception {
        User owner = createActiveUser("ann-create-notype@example.com", "Password1!");
        Company company = createCompany(owner, "No Type Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{id}/announcements", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hi\",\"body\":\"Something\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAnnouncement_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("ann-create-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Announce Co");

        mockMvc.perform(post("/companies/{id}/announcements", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createAnnouncementBody("Ghost Post", "No auth."))))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{id}/announcements ─────────────────────────────────────

    @Test
    void listPublishedAnnouncements_returnsEmptyWhenNonePublished() throws Exception {
        User owner = createActiveUser("ann-list-pub@example.com", "Password1!");
        Company company = createCompany(owner, "Pub List Co");
        addMember(owner, company, CompanyRole.OWNER);
        createAnnouncementAndGetId(company, owner, "Draft Only");  // DRAFT — not visible

        mockMvc.perform(get("/companies/{id}/announcements", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listPublishedAnnouncements_returnsPublishedAfterPatch() throws Exception {
        User owner = createActiveUser("ann-list-after-pub@example.com", "Password1!");
        Company company = createCompany(owner, "Published Co");
        addMember(owner, company, CompanyRole.OWNER);
        String annId = createAnnouncementAndGetId(company, owner, "Published Post");

        // Publish via PATCH
        mockMvc.perform(patch("/companies/{id}/announcements/{annId}", company.getId(), annId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publish\":true}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(get("/companies/{id}/announcements", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    // ── GET /companies/{id}/announcements/all ─────────────────────────────────

    @Test
    void listAllAnnouncements_includesDraftsForMember() throws Exception {
        User owner = createActiveUser("ann-list-all@example.com", "Password1!");
        Company company = createCompany(owner, "All List Co");
        addMember(owner, company, CompanyRole.OWNER);
        createAnnouncementAndGetId(company, owner, "Draft Post");

        mockMvc.perform(get("/companies/{id}/announcements/all", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void listAllAnnouncements_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("ann-list-all-owner@example.com", "Password1!");
        User stranger = createActiveUser("ann-list-all-stranger@example.com", "Password1!");
        Company company = createCompany(owner, "Stranger List Co");

        mockMvc.perform(get("/companies/{id}/announcements/all", company.getId())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllAnnouncements_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("ann-list-all-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth All Co");

        mockMvc.perform(get("/companies/{id}/announcements/all", company.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /companies/{id}/announcements/{annId} ───────────────────────────

    @Test
    void updateAnnouncement_returns200WithUpdatedTitle() throws Exception {
        User owner = createActiveUser("ann-update@example.com", "Password1!");
        Company company = createCompany(owner, "Update Ann Co");
        addMember(owner, company, CompanyRole.OWNER);
        String annId = createAnnouncementAndGetId(company, owner, "Old Title");

        mockMvc.perform(patch("/companies/{id}/announcements/{annId}", company.getId(), annId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Title\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("New Title"));

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM company_announcements", String.class);
        assertEquals("New Title", title, "Announcement title change should be persisted");
    }

    @Test
    void updateAnnouncement_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("ann-upd-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("ann-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Emp Update Ann Co");
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        String annId = createAnnouncementAndGetId(company, owner, "Protected Ann");

        mockMvc.perform(patch("/companies/{id}/announcements/{annId}", company.getId(), annId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}")
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAnnouncement_returns404ForUnknownId() throws Exception {
        User owner = createActiveUser("ann-upd-404@example.com", "Password1!");
        Company company = createCompany(owner, "404 Ann Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(patch("/companies/{id}/announcements/{annId}",
                        company.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ghost\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /companies/{id}/announcements/{annId} ──────────────────────────

    @Test
    void deleteAnnouncement_returns204ForDraftOwner() throws Exception {
        User owner = createActiveUser("ann-del@example.com", "Password1!");
        Company company = createCompany(owner, "Delete Ann Co");
        addMember(owner, company, CompanyRole.OWNER);
        String annId = createAnnouncementAndGetId(company, owner, "To Delete");

        mockMvc.perform(delete("/companies/{id}/announcements/{annId}", company.getId(), annId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company_announcements", Integer.class);
        assertEquals(0, rows, "Deleted announcement should be removed from the database");
    }

    @Test
    void deleteAnnouncement_returns400WhenAlreadyPublished() throws Exception {
        User owner = createActiveUser("ann-del-pub@example.com", "Password1!");
        Company company = createCompany(owner, "Published Del Co");
        addMember(owner, company, CompanyRole.OWNER);
        String annId = createAnnouncementAndGetId(company, owner, "Published Ann");

        mockMvc.perform(patch("/companies/{id}/announcements/{annId}", company.getId(), annId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publish\":true}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/companies/{id}/announcements/{annId}", company.getId(), annId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAnnouncement_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("ann-del-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Del Co");
        addMember(owner, company, CompanyRole.OWNER);
        String annId = createAnnouncementAndGetId(company, owner, "Unauth Ann");

        mockMvc.perform(delete("/companies/{id}/announcements/{annId}", company.getId(), annId))
                .andExpect(status().isUnauthorized());
    }
}
