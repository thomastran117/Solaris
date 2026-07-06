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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PromotionRuleIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;

    @AfterEach
    void cleanPromotions() {
        try { jdbcTemplate.execute("DELETE FROM promotion_rule_target_products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM promotion_rule_target_bundles"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM promotion_rule_target_segments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM promotion_rules"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Promo Company " + UUID.randomUUID());
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

    private String percentageOffBody(String name) throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("percent", 10);
        config.put("appliesTo", "ORDER");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("ruleType", "PERCENTAGE_OFF");
        body.put("config", config);
        return objectMapper.writeValueAsString(body);
    }

    // ── GET /companies/{companyId}/promotion-rules ────────────────────────────

    @Test
    void listRules_returnsEmptyWhenNoRules() throws Exception {
        User owner = createActiveUser("promo-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listRules_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("promo-list-403-owner@example.com", "Password1!");
        User employee = createActiveUser("promo-list-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(get("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRules_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("promo-list-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/promotion-rules", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/promotion-rules ───────────────────────────

    @Test
    void createRule_returns201WithValidPercentageOff() throws Exception {
        User owner = createActiveUser("promo-create-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageOffBody("10% Off Everything")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("10% Off Everything"))
                .andExpect(jsonPath("$.data.ruleType").value("PERCENTAGE_OFF"))
                .andExpect(jsonPath("$.data.id").isNotEmpty());

        Integer ruleRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM promotion_rules WHERE name = '10% Off Everything'", Integer.class);
        assertEquals(1, ruleRows, "Promotion rule should be persisted");
    }

    @Test
    void createRule_managerCanCreate() throws Exception {
        User owner = createActiveUser("promo-create-mgr-owner@example.com", "Password1!");
        User manager = createActiveUser("promo-create-mgr@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(manager, company, CompanyRole.MANAGER);

        mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageOffBody("Manager Rule")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Manager Rule"));
    }

    @Test
    void createRule_returns400WhenNameMissing() throws Exception {
        User owner = createActiveUser("promo-create-400-name@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("percent", 10);
        config.put("appliesTo", "ORDER");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ruleType", "PERCENTAGE_OFF");
        body.put("config", config);

        mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRule_returns400WhenConfigIsInvalid() throws Exception {
        User owner = createActiveUser("promo-create-400-cfg@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("percent", -5); // invalid: must be > 0

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Bad Rule");
        body.put("ruleType", "PERCENTAGE_OFF");
        body.put("config", config);

        mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRule_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("promo-create-403-owner@example.com", "Password1!");
        User employee = createActiveUser("promo-create-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageOffBody("Employee Rule")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRule_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("promo-create-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageOffBody("Anon Rule")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/promotion-rules/{ruleId} ──────────────────

    @Test
    void getRule_returns200ForOwner() throws Exception {
        User owner = createActiveUser("promo-get-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        String createResponse = mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageOffBody("Get Test Rule")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String ruleId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        mockMvc.perform(get("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), ruleId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ruleId))
                .andExpect(jsonPath("$.data.name").value("Get Test Rule"));
    }

    @Test
    void getRule_returns404WhenNotFound() throws Exception {
        User owner = createActiveUser("promo-get-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRule_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("promo-get-403-owner@example.com", "Password1!");
        User employee = createActiveUser("promo-get-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(get("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /companies/{companyId}/promotion-rules/{ruleId} ────────────────

    @Test
    void updateRule_updatesNameSuccessfully() throws Exception {
        User owner = createActiveUser("promo-patch-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        String createResponse = mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageOffBody("Original Name")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String ruleId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("name", "Updated Name");

        mockMvc.perform(patch("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), ruleId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"));

        Integer renamedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM promotion_rules WHERE name = 'Updated Name'", Integer.class);
        assertEquals(1, renamedRows, "Promotion rule rename should be persisted");
    }

    @Test
    void updateRule_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("promo-patch-403-owner@example.com", "Password1!");
        User employee = createActiveUser("promo-patch-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("name", "Attempt");

        mockMvc.perform(patch("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /companies/{companyId}/promotion-rules/{ruleId} ───────────────

    @Test
    void deleteRule_returns204OnSuccess() throws Exception {
        User owner = createActiveUser("promo-del-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        String createResponse = mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageOffBody("Rule To Delete")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String ruleId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        mockMvc.perform(delete("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), ruleId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM promotion_rules", Integer.class);
        assertEquals(0, remaining, "Deleted promotion rule should be removed from the database");
    }

    @Test
    void deleteRule_returns404WhenNotFound() throws Exception {
        User owner = createActiveUser("promo-del-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRule_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("promo-del-403-owner@example.com", "Password1!");
        User employee = createActiveUser("promo-del-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(delete("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRule_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("promo-del-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(delete("/companies/{companyId}/promotion-rules/{ruleId}",
                        company.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── List rules returns rule after creation ────────────────────────────────

    @Test
    void listRules_returnsRuleAfterCreation() throws Exception {
        User owner = createActiveUser("promo-list-after@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(percentageOffBody("Listed Rule")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/companies/{companyId}/promotion-rules", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Listed Rule"));
    }
}
