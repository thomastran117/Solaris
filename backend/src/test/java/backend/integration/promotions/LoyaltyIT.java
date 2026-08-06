package backend.integration.promotions;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
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

class LoyaltyIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;


    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Loyalty Test Co");
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

    private Map<String, Object> policyBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("earnRatePerDollar", "1.00");
        body.put("cashbackRatePercent", "0.00");
        body.put("earnMode", "POINTS");
        return body;
    }

    private Map<String, Object> tierBody(String name, long minPoints) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("minPoints", minPoints);
        body.put("earnMultiplier", "1.00");
        return body;
    }

    private UUID createPolicy(User owner, Company company) throws Exception {
        String resp = mockMvc.perform(post("/companies/{id}/loyalty/policy", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policyBody("Default Policy"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resp).at("/data/id").asText());
    }

    private UUID createTier(User owner, Company company, String name, long minPoints) throws Exception {
        String resp = mockMvc.perform(post("/companies/{id}/loyalty/tiers", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tierBody(name, minPoints))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resp).at("/data/id").asText());
    }

    // ── Policy management ─────────────────────────────────────────────────────

    @Test
    void createOrUpdatePolicy_returns201WithCorrectFields() throws Exception {
        User owner = createActiveUser("loyalty-policy-create@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{id}/loyalty/policy", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policyBody("Standard Rewards"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Standard Rewards"))
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.earnMode").value("POINTS"))
                .andExpect(jsonPath("$.data.active").value(true));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loyalty_policies WHERE name = 'Standard Rewards'", Integer.class);
        assertEquals(1, rows, "Loyalty policy should be persisted");
    }

    @Test
    void createOrUpdatePolicy_updatesExistingPolicy() throws Exception {
        User owner = createActiveUser("loyalty-policy-update@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createPolicy(owner, company);

        Map<String, Object> updated = policyBody("Upgraded Rewards");
        updated.put("earnRatePerDollar", "2.00");

        mockMvc.perform(post("/companies/{id}/loyalty/policy", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Upgraded Rewards"));
    }

    @Test
    void createOrUpdatePolicy_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("loyalty-policy-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("loyalty-policy-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{id}/loyalty/policy", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policyBody("Blocked"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPolicy_returns200WithPolicy() throws Exception {
        User owner = createActiveUser("loyalty-policy-get@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createPolicy(owner, company);

        mockMvc.perform(get("/companies/{id}/loyalty/policy", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Default Policy"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void getPolicy_returns404WhenNoPolicy() throws Exception {
        User owner = createActiveUser("loyalty-policy-get-none@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{id}/loyalty/policy", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPolicy_returns200ForEmployee() throws Exception {
        User owner = createActiveUser("loyalty-policy-emp-get-owner@example.com", "Password1!");
        User employee = createActiveUser("loyalty-policy-emp-get@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        createPolicy(owner, company);

        // requireAnyAccess — any active member can read the policy
        mockMvc.perform(get("/companies/{id}/loyalty/policy", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Default Policy"));
    }

    // ── Tier management ───────────────────────────────────────────────────────

    @Test
    void createTier_returns201WithCorrectFields() throws Exception {
        User owner = createActiveUser("loyalty-tier-create@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{id}/loyalty/tiers", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tierBody("Silver", 500L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Silver"))
                .andExpect(jsonPath("$.data.minPoints").value(500))
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andReturn();

        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loyalty_tiers WHERE name = 'Silver'", Integer.class),
                "Loyalty tier should be persisted");
    }

    @Test
    void createTier_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("loyalty-tier-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("loyalty-tier-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{id}/loyalty/tiers", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tierBody("Bronze", 0L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTiers_returnsAllTiersForMember() throws Exception {
        User owner = createActiveUser("loyalty-tiers-list@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createTier(owner, company, "Bronze", 0L);
        createTier(owner, company, "Silver", 500L);

        mockMvc.perform(get("/companies/{id}/loyalty/tiers", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void updateTier_updatesNameAndMinPoints() throws Exception {
        User owner = createActiveUser("loyalty-tier-update@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID tierId = createTier(owner, company, "Bronze", 0L);

        Map<String, Object> update = tierBody("Gold", 1000L);

        mockMvc.perform(put("/companies/{cid}/loyalty/tiers/{tierId}", company.getId(), tierId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Gold"))
                .andExpect(jsonPath("$.data.minPoints").value(1000));
    }

    @Test
    void updateTier_returns404ForUnknown() throws Exception {
        User owner = createActiveUser("loyalty-tier-update-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(put("/companies/{cid}/loyalty/tiers/{tierId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tierBody("X", 0L))))
                .andExpect(status().isNotFound());
    }

    // ── Customer self-service ─────────────────────────────────────────────────

    @Test
    void getAccount_returns200WithEmptyAccountWhenNone() throws Exception {
        User customer = createActiveUser("loyalty-acct-empty@example.com", "Password1!");
        User owner = createActiveUser("loyalty-acct-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/loyalty/account")
                        .param("companyId", company.getId().toString())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsBalance").value(0))
                .andExpect(jsonPath("$.data.lifetimePoints").value(0));
    }

    @Test
    void getTransactions_returns404WhenNoAccount() throws Exception {
        User customer = createActiveUser("loyalty-tx-none@example.com", "Password1!");
        User owner = createActiveUser("loyalty-tx-none-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/loyalty/transactions")
                        .param("companyId", company.getId().toString())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getExpiryWarning_returns200WithZeroesWhenNoAccount() throws Exception {
        User customer = createActiveUser("loyalty-expiry-empty@example.com", "Password1!");
        User owner = createActiveUser("loyalty-expiry-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/loyalty/expiry-warning")
                        .param("companyId", company.getId().toString())
                        .param("days", "30")
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsExpiringSoon").value(0));
    }

    @Test
    void getReferralInfo_returns200WithReferralCode() throws Exception {
        User customer = createActiveUser("loyalty-referral@example.com", "Password1!");
        User owner = createActiveUser("loyalty-referral-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/loyalty/referral")
                        .param("companyId", company.getId().toString())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referralCode").isNotEmpty())
                .andExpect(jsonPath("$.data.totalReferrals").value(0));
    }

    @Test
    void getRedemptionQuote_returnsInvalidWhenNoPolicy() throws Exception {
        User customer = createActiveUser("loyalty-quote-nopolicy@example.com", "Password1!");
        User owner = createActiveUser("loyalty-quote-nopolicy-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/loyalty/quote")
                        .param("companyId", company.getId().toString())
                        .param("points", "100")
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    void getRedemptionQuote_returnsValidWithActivePolicy() throws Exception {
        User customer = createActiveUser("loyalty-quote-policy@example.com", "Password1!");
        User owner = createActiveUser("loyalty-quote-policy-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createPolicy(owner, company);

        mockMvc.perform(get("/loyalty/quote")
                        .param("companyId", company.getId().toString())
                        .param("points", "100")
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()));
    }

    @Test
    void applyReferralCode_returns400WhenApplyingOwnCode() throws Exception {
        User customer = createActiveUser("loyalty-self-ref@example.com", "Password1!");
        User owner = createActiveUser("loyalty-self-ref-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        // First get the customer's own referral code
        String referralResp = mockMvc.perform(get("/loyalty/referral")
                        .param("companyId", company.getId().toString())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String ownCode = objectMapper.readTree(referralResp).at("/data/referralCode").asText();

        Map<String, String> body = Map.of("code", ownCode);

        mockMvc.perform(post("/loyalty/referral/apply")
                        .param("companyId", company.getId().toString())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── Operator actions ──────────────────────────────────────────────────────

    @Test
    void issueBonus_returns201WithTransaction() throws Exception {
        User owner = createActiveUser("loyalty-bonus@example.com", "Password1!");
        User customer = createActiveUser("loyalty-bonus-customer@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", customer.getId().toString());
        body.put("points", 200);
        body.put("reason", "Welcome bonus");

        mockMvc.perform(post("/companies/{id}/loyalty/bonus", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.pointsDelta").value(200))
                .andExpect(jsonPath("$.data.type").value("EARN_BONUS"))
                .andExpect(jsonPath("$.data.accountId").isNotEmpty());

        // The bonus must be committed to the account balance in the database.
        Long balance = jdbcTemplate.queryForObject(
                "SELECT points_balance FROM loyalty_accounts", Long.class);
        assertEquals(200L, balance, "Bonus points should be credited to the account balance");
    }

    @Test
    void issueBonus_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("loyalty-bonus-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("loyalty-bonus-emp@example.com", "Password1!");
        User customer = createActiveUser("loyalty-bonus-emp-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", customer.getId().toString());
        body.put("points", 100);
        body.put("reason", "Blocked");

        mockMvc.perform(post("/companies/{id}/loyalty/bonus", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjustPoints_returns200WithTransaction() throws Exception {
        User owner = createActiveUser("loyalty-adjust@example.com", "Password1!");
        User customer = createActiveUser("loyalty-adjust-customer@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        // Issue bonus to create the account first
        Map<String, Object> bonusBody = new LinkedHashMap<>();
        bonusBody.put("userId", customer.getId().toString());
        bonusBody.put("points", 500);
        bonusBody.put("reason", "Setup bonus");

        String bonusResp = mockMvc.perform(post("/companies/{id}/loyalty/bonus", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bonusBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID accountId = UUID.fromString(objectMapper.readTree(bonusResp).at("/data/accountId").asText());

        Map<String, Object> adjustBody = new LinkedHashMap<>();
        adjustBody.put("pointsDelta", -100);
        adjustBody.put("reason", "Manual correction");

        mockMvc.perform(post("/companies/{cid}/loyalty/accounts/{accountId}/adjust",
                        company.getId(), accountId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsDelta").value(-100))
                .andExpect(jsonPath("$.data.reason").value("Manual correction"));

        // 500 (bonus) - 100 (adjustment) must be committed to the account balance.
        Long balance = jdbcTemplate.queryForObject(
                "SELECT points_balance FROM loyalty_accounts", Long.class);
        assertEquals(400L, balance, "Adjusted points balance should be committed to the database");
    }

    @Test
    void adjustPoints_returns404ForUnknownAccount() throws Exception {
        User owner = createActiveUser("loyalty-adjust-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pointsDelta", 50);
        body.put("reason", "Test");

        mockMvc.perform(post("/companies/{cid}/loyalty/accounts/{accountId}/adjust",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }
}
