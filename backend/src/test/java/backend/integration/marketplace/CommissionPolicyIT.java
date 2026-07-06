package backend.integration.marketplace;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.CommissionPolicy;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.MarketplaceProfile;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.repositories.CommissionPolicyRepository;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketplaceProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommissionPolicyIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private MarketplaceProfileRepository marketplaceProfileRepository;
    @Autowired private CommissionPolicyRepository commissionPolicyRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM commission_rules"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM commission_policies"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM marketplace_profiles"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createMarketplace(User operator) {
        Company company = new Company();
        company.setOwner(operator);
        company.setName("Market Co " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        CompanyMembership membership = new CompanyMembership();
        membership.setCompany(company);
        membership.setUser(operator);
        membership.setRole(CompanyRole.OWNER);
        membership.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(company);
        profile.setSlug("market-" + UUID.randomUUID().toString().substring(0, 8));
        profile.setAcceptingApplications(true);
        marketplaceProfileRepository.save(profile);

        return company;
    }

    private String base(UUID marketplaceId) {
        return "/marketplaces/" + marketplaceId + "/commission-policies";
    }

    private String minimalBody(String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of("name", name, "defaultRate", "0.1500"));
    }

    // ── GET /marketplaces/{marketplaceId}/commission-policies ─────────────────

    @Test
    void list_empty_returns200() throws Exception {
        User operator = createActiveUser("cp-list-empty@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void list_onlyReturnsActivePolicies() throws Exception {
        User operator = createActiveUser("cp-list-active@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        String token = bearer(accessTokenFor(operator));

        // Active policy via API
        mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Active Policy")))
                .andExpect(status().isCreated());

        // Inactive policy created directly via repository
        CommissionPolicy inactive = new CommissionPolicy();
        inactive.setMarketplaceId(marketplace.getId());
        inactive.setName("Inactive Policy");
        inactive.setDefaultRate(new BigDecimal("0.1500"));
        inactive.setActive(false);
        commissionPolicyRepository.save(inactive);

        mockMvc.perform(get(base(marketplace.getId()))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Active Policy"))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void list_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("cp-list-op@example.com", "Password1!");
        User other = createActiveUser("cp-list-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unknownMarketplace_returns403() throws Exception {
        User user = createActiveUser("cp-list-unknown@example.com", "Password1!");

        mockMvc.perform(get(base(UUID.randomUUID()))
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(base(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /marketplaces/{marketplaceId}/commission-policies ────────────────

    @Test
    void create_minimalPolicy_returns201() throws Exception {
        User operator = createActiveUser("cp-create-min@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        MvcResult result = mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Base Rate")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.marketplaceId").value(marketplace.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Base Rate"))
                .andExpect(jsonPath("$.data.defaultRate").value(0.15))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.rules").isArray())
                .andExpect(jsonPath("$.data.rules").isEmpty())
                .andReturn();

        UUID policyId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
        CommissionPolicy persisted = commissionPolicyRepository.findById(policyId).orElseThrow();
        assertEquals("Base Rate", persisted.getName());
        assertTrue(persisted.isActive());
        assertEquals(0, new BigDecimal("0.1500").compareTo(persisted.getDefaultRate()));
    }

    @Test
    void create_withRules_returns201() throws Exception {
        User operator = createActiveUser("cp-create-rules@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Category Policy",
                "defaultRate", "0.1500",
                "rules", List.of(Map.of(
                        "ruleType", "CATEGORY",
                        "matchValue", "Electronics",
                        "rate", "0.0800",
                        "priority", 10
                ))
        ));

        mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Category Policy"))
                .andExpect(jsonPath("$.data.rules", hasSize(1)))
                .andExpect(jsonPath("$.data.rules[0].ruleType").value("CATEGORY"))
                .andExpect(jsonPath("$.data.rules[0].matchValue").value("Electronics"))
                .andExpect(jsonPath("$.data.rules[0].rate").value(0.08))
                .andExpect(jsonPath("$.data.rules[0].priority").value(10));

        Integer ruleRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM commission_rules", Integer.class);
        assertEquals(1, ruleRows, "Commission rule should be persisted");
    }

    @Test
    void create_multipleRules_returns201() throws Exception {
        User operator = createActiveUser("cp-create-multi@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Multi-Rule Policy",
                "defaultRate", "0.1500",
                "rules", List.of(
                        Map.of("ruleType", "BRAND", "matchValue", "Nike", "rate", "0.0600", "priority", 5),
                        Map.of("ruleType", "CATEGORY", "matchValue", "Shoes", "rate", "0.1000", "priority", 20)
                )
        ));

        mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rules", hasSize(2)))
                .andExpect(jsonPath("$.data.rules[*].ruleType", containsInAnyOrder("BRAND", "CATEGORY")))
                .andExpect(jsonPath("$.data.rules[*].matchValue", containsInAnyOrder("Nike", "Shoes")));
    }

    @Test
    void create_missingName_returns400() throws Exception {
        User operator = createActiveUser("cp-create-noname@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        String body = objectMapper.writeValueAsString(Map.of("defaultRate", "0.10"));

        mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingDefaultRate_returns400() throws Exception {
        User operator = createActiveUser("cp-create-norate@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        String body = objectMapper.writeValueAsString(Map.of("name", "No Rate Policy"));

        mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_rateAboveMax_returns400() throws Exception {
        User operator = createActiveUser("cp-create-hirate@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        String body = objectMapper.writeValueAsString(Map.of("name", "Too High", "defaultRate", "1.5000"));

        mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("cp-create-op@example.com", "Password1!");
        User other = createActiveUser("cp-create-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Unauthorized")))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        // Send a valid body so @NotBlank validation does not fire before auth check
        mockMvc.perform(post(base(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Test")))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /marketplaces/{marketplaceId}/commission-policies/{policyId} ───

    @Test
    void delete_existingPolicy_returns204() throws Exception {
        User operator = createActiveUser("cp-delete-ok@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        String token = bearer(accessTokenFor(operator));

        String response = mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("To Delete")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID policyId = UUID.fromString(
                objectMapper.readTree(response).path("data").path("id").asText());

        mockMvc.perform(delete(base(marketplace.getId()) + "/" + policyId)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        assertTrue(commissionPolicyRepository.findById(policyId).isEmpty(),
                "Deleted commission policy should be removed from the database");

        // Verify removed from list
        mockMvc.perform(get(base(marketplace.getId()))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void delete_unknownPolicy_returns404() throws Exception {
        User operator = createActiveUser("cp-delete-404@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(delete(base(marketplace.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_policyFromOtherMarketplace_returns404() throws Exception {
        User operatorA = createActiveUser("cp-del-a@example.com", "Password1!");
        User operatorB = createActiveUser("cp-del-b@example.com", "Password1!");
        Company marketplaceA = createMarketplace(operatorA);
        Company marketplaceB = createMarketplace(operatorB);

        // Create policy in marketplace A
        String response = mockMvc.perform(post(base(marketplaceA.getId()))
                        .header("Authorization", bearer(accessTokenFor(operatorA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Policy A")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID policyId = UUID.fromString(
                objectMapper.readTree(response).path("data").path("id").asText());

        // Operator B tries to delete it via marketplace B's path — marketplaceId filter mismatch → 404
        mockMvc.perform(delete(base(marketplaceB.getId()) + "/" + policyId)
                        .header("Authorization", bearer(accessTokenFor(operatorB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("cp-del-op@example.com", "Password1!");
        User other = createActiveUser("cp-del-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        String response = mockMvc.perform(post(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Policy")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID policyId = UUID.fromString(
                objectMapper.readTree(response).path("data").path("id").asText());

        mockMvc.perform(delete(base(marketplace.getId()) + "/" + policyId)
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete(base(UUID.randomUUID()) + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
