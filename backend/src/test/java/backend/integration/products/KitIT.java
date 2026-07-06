package backend.integration.products;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KitIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;

    @AfterEach
    void cleanKits() {
        try { jdbcTemplate.execute("DELETE FROM kit_slot_choices"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM kit_slots"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_kits"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Kit Test Company " + UUID.randomUUID());
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

    private Map<String, Object> validKitBody(String name) {
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("name", "Main Slot");
        slot.put("required", true);
        slot.put("minQty", 1);
        slot.put("maxQty", 1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("slots", List.of(slot));
        return body;
    }

    private String createKitAndGetId(Company company, User owner, String name) throws Exception {
        return objectMapper.readTree(
                mockMvc.perform(post("/companies/{companyId}/kits", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validKitBody(name)))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).path("data").path("id").asText();
    }

    // ── GET /companies/{companyId}/kits ───────────────────────────────────────

    @Test
    void listKits_returnsEmptyPageWhenNone() throws Exception {
        User owner = createActiveUser("kit-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/kits", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listKits_returnsEmptyPageForUnknownCompany() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/kits", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── POST /companies/{companyId}/kits ──────────────────────────────────────

    @Test
    void createKit_returns201ForOwner() throws Exception {
        User owner = createActiveUser("kit-create-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/kits", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validKitBody("Starter Kit")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Starter Kit"));

        // The kit and its slot must actually be persisted.
        Integer kitRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_kits WHERE name = 'Starter Kit'", Integer.class);
        assertEquals(1, kitRows, "Kit should be persisted");
        Integer slotRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kit_slots", Integer.class);
        assertEquals(1, slotRows, "Kit slot should be persisted");
    }

    @Test
    void createKit_returns201ForManager() throws Exception {
        User owner = createActiveUser("kit-create-mgr-owner@example.com", "Password1!");
        User manager = createActiveUser("kit-create-mgr@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(manager, company, CompanyRole.MANAGER);

        mockMvc.perform(post("/companies/{companyId}/kits", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validKitBody("Manager Kit")))
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated());
    }

    @Test
    void createKit_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("kit-create-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("kit-create-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{companyId}/kits", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validKitBody("Forbidden Kit")))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void createKit_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("kit-create-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post("/companies/{companyId}/kits", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validKitBody("Unauth Kit"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createKit_returns400WhenNameMissing() throws Exception {
        User owner = createActiveUser("kit-create-noname@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> slot = Map.of("name", "Slot");
        Map<String, Object> body = Map.of("slots", List.of(slot));

        mockMvc.perform(post("/companies/{companyId}/kits", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createKit_returns400WhenSlotsEmpty() throws Exception {
        User owner = createActiveUser("kit-create-noslots@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Empty Slots Kit");
        body.put("slots", new ArrayList<>());

        mockMvc.perform(post("/companies/{companyId}/kits", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/kits/{kitId} ───────────────────────────────

    @Test
    void getKit_returns200ForExistingKit() throws Exception {
        User owner = createActiveUser("kit-get@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        String kitId = createKitAndGetId(company, owner, "Fetched Kit");

        // Kits are created as DRAFT; publish to ACTIVE so the public GET endpoint returns it
        mockMvc.perform(patch("/companies/{companyId}/kits/{kitId}", company.getId(), kitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(get("/companies/{companyId}/kits/{kitId}", company.getId(), kitId)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(kitId))
                .andExpect(jsonPath("$.data.name").value("Fetched Kit"));
    }

    @Test
    void getKit_returns404ForUnknownKit() throws Exception {
        User owner = createActiveUser("kit-get-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/kits/{kitId}", company.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /companies/{companyId}/kits/{kitId} ─────────────────────────────

    @Test
    void updateKit_returns200WithUpdatedName() throws Exception {
        User owner = createActiveUser("kit-update@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        String kitId = createKitAndGetId(company, owner, "Old Name");

        mockMvc.perform(patch("/companies/{companyId}/kits/{kitId}", company.getId(), kitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"));

        Integer renamedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_kits WHERE name = 'New Name'", Integer.class);
        assertEquals(1, renamedRows, "Kit rename should be persisted");
    }

    @Test
    void updateKit_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("kit-upd-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("kit-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        String kitId = createKitAndGetId(company, owner, "Protected Kit");

        mockMvc.perform(patch("/companies/{companyId}/kits/{kitId}", company.getId(), kitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}")
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateKit_returns404ForUnknownKit() throws Exception {
        User owner = createActiveUser("kit-upd-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(patch("/companies/{companyId}/kits/{kitId}", company.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /companies/{companyId}/kits/{kitId} ────────────────────────────

    @Test
    void deleteKit_returns204ForOwner() throws Exception {
        User owner = createActiveUser("kit-del@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        String kitId = createKitAndGetId(company, owner, "To Delete");

        mockMvc.perform(delete("/companies/{companyId}/kits/{kitId}", company.getId(), kitId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_kits", Integer.class);
        assertEquals(0, remaining, "Deleted kit should be removed from the database");

        mockMvc.perform(get("/companies/{companyId}/kits/{kitId}", company.getId(), kitId)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteKit_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("kit-del-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("kit-del-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        String kitId = createKitAndGetId(company, owner, "Protected Kit");

        mockMvc.perform(delete("/companies/{companyId}/kits/{kitId}", company.getId(), kitId)
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteKit_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("kit-del-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        String kitId = createKitAndGetId(company, owner, "Unauth Delete Kit");

        mockMvc.perform(delete("/companies/{companyId}/kits/{kitId}", company.getId(), kitId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteKit_returns404ForUnknownKit() throws Exception {
        User owner = createActiveUser("kit-del-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{companyId}/kits/{kitId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{companyId}/kits/{kitId}/import-collection ────────────

    @Test
    void importCollection_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("kit-import-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        String kitId = createKitAndGetId(company, owner, "Import Kit");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionId", UUID.randomUUID().toString());
        body.put("slotId", UUID.randomUUID().toString());

        mockMvc.perform(post("/companies/{companyId}/kits/{kitId}/import-collection",
                        company.getId(), kitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importCollection_returns400WhenBodyMissing() throws Exception {
        User owner = createActiveUser("kit-import-nobody@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        String kitId = createKitAndGetId(company, owner, "Import Kit Body");

        mockMvc.perform(post("/companies/{companyId}/kits/{kitId}/import-collection",
                        company.getId(), kitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }
}
