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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyWebhookIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;

    @MockitoBean
    @Qualifier("webhookRestTemplate")
    RestTemplate webhookRestTemplate;


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

    private Map<String, Object> registerBody(String url, List<String> events) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("url", url);
        req.put("events", events);
        return req;
    }

    /** Register a webhook and return its subscription ID. */
    private String registerWebhookAndGetId(Company company, User owner, String url) throws Exception {
        // Make challenge GET fail so status stays PENDING_VERIFICATION
        when(webhookRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("timeout"));

        String response = mockMvc.perform(
                        post("/companies/{id}/webhooks", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        registerBody(url, List.of("ORDER_CREATED", "ORDER_PAID"))))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.path("data").path("subscription").path("id").asText();
    }

    // ── POST /companies/{id}/webhooks ─────────────────────────────────────────

    @Test
    void register_returns201WithSecret_forOwner() throws Exception {
        User owner = createActiveUser("wh-reg-owner@example.com", "Password1!");
        Company company = createCompany(owner, "Webhook Co");
        addMember(owner, company, CompanyRole.OWNER);

        when(webhookRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("timeout"));

        mockMvc.perform(post("/companies/{id}/webhooks", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                registerBody("https://example.com/hook", List.of("ORDER_CREATED"))))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.secret").isString())
                .andExpect(jsonPath("$.data.secret", hasLength(64)))
                .andExpect(jsonPath("$.data.subscription.url").value("https://example.com/hook"))
                .andExpect(jsonPath("$.data.subscription.status").value("PENDING_VERIFICATION"));

        assertEquals("PENDING_VERIFICATION",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM company_webhook_subscriptions", String.class),
                "Webhook subscription should be persisted as PENDING_VERIFICATION");
    }

    @Test
    void register_returns403_forEmployee() throws Exception {
        User owner = createActiveUser("wh-reg-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("wh-reg-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Emp Webhook Co");
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{id}/webhooks", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                registerBody("https://example.com/hook", List.of("ORDER_CREATED"))))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_returns400_whenFiveSubsAlreadyExist() throws Exception {
        User owner = createActiveUser("wh-max@example.com", "Password1!");
        Company company = createCompany(owner, "Max Webhooks Co");
        addMember(owner, company, CompanyRole.OWNER);

        when(webhookRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("timeout"));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/companies/{id}/webhooks", company.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    registerBody("https://example.com/hook" + i, List.of("ORDER_CREATED"))))
                            .header("Authorization", bearer(accessTokenFor(owner)))
                            .header("User-Agent", TEST_USER_AGENT))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/companies/{id}/webhooks", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                registerBody("https://example.com/hook-extra", List.of("ORDER_CREATED"))))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns401_whenUnauthenticated() throws Exception {
        User owner = createActiveUser("wh-reg-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Webhook Co");

        mockMvc.perform(post("/companies/{id}/webhooks", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                registerBody("https://example.com/hook", List.of("ORDER_CREATED")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_marksActive_whenChallengeGetSucceeds() throws Exception {
        User owner = createActiveUser("wh-active@example.com", "Password1!");
        Company company = createCompany(owner, "Active Webhook Co");
        addMember(owner, company, CompanyRole.OWNER);

        // First save captures the subscription — return the challenge token on the GET
        when(webhookRestTemplate.getForEntity(contains("challenge="), eq(String.class)))
                .thenAnswer(inv -> {
                    String url = (String) inv.getArgument(0);
                    String challenge = url.substring(url.indexOf("challenge=") + 10);
                    return ResponseEntity.ok(challenge);
                });

        mockMvc.perform(post("/companies/{id}/webhooks", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                registerBody("https://fast-endpoint.com/wh", List.of("ORDER_CREATED"))))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subscription.status").value("ACTIVE"));

        assertEquals("ACTIVE",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM company_webhook_subscriptions", String.class),
                "Verified webhook subscription should be persisted as ACTIVE");
    }

    // ── GET /companies/{id}/webhooks ──────────────────────────────────────────

    @Test
    void list_returns200WithSubscriptions_forOwner() throws Exception {
        User owner = createActiveUser("wh-list@example.com", "Password1!");
        Company company = createCompany(owner, "List Webhook Co");
        addMember(owner, company, CompanyRole.OWNER);
        registerWebhookAndGetId(company, owner, "https://example.com/hook1");

        mockMvc.perform(get("/companies/{id}/webhooks", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].url").value("https://example.com/hook1"));
    }

    @Test
    void list_returns401_whenUnauthenticated() throws Exception {
        User owner = createActiveUser("wh-list-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth List Co");

        mockMvc.perform(get("/companies/{id}/webhooks", company.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /companies/{id}/webhooks/{subId} ───────────────────────────────

    @Test
    void delete_returns204_forOwner() throws Exception {
        User owner = createActiveUser("wh-del@example.com", "Password1!");
        Company company = createCompany(owner, "Delete Webhook Co");
        addMember(owner, company, CompanyRole.OWNER);
        String subId = registerWebhookAndGetId(company, owner, "https://example.com/del-hook");

        mockMvc.perform(delete("/companies/{id}/webhooks/{subId}", company.getId(), subId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company_webhook_subscriptions", Integer.class);
        assertEquals(0, rows, "Deleted webhook subscription should be removed from the database");

        // Verify it's gone
        mockMvc.perform(get("/companies/{id}/webhooks", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void delete_returns404_forUnknownId() throws Exception {
        User owner = createActiveUser("wh-del-404@example.com", "Password1!");
        Company company = createCompany(owner, "404 Del Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{id}/webhooks/{subId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{id}/webhooks/{subId}/verify ──────────────────────────

    @Test
    void verify_returns200_whenChallengeResponseMatches() throws Exception {
        User owner = createActiveUser("wh-verify@example.com", "Password1!");
        Company company = createCompany(owner, "Verify Webhook Co");
        addMember(owner, company, CompanyRole.OWNER);
        String subId = registerWebhookAndGetId(company, owner, "https://example.com/verify-hook");

        // Stub the challenge GET to return the correct challenge token
        when(webhookRestTemplate.getForEntity(contains("challenge="), eq(String.class)))
                .thenAnswer(inv -> {
                    String url = (String) inv.getArgument(0);
                    String challenge = url.substring(url.indexOf("challenge=") + 10);
                    return ResponseEntity.ok(challenge);
                });

        mockMvc.perform(post("/companies/{id}/webhooks/{subId}/verify",
                        company.getId(), subId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
    }

    @Test
    void verify_returns400_whenChallengeResponseDoesNotMatch() throws Exception {
        User owner = createActiveUser("wh-verify-fail@example.com", "Password1!");
        Company company = createCompany(owner, "Fail Verify Co");
        addMember(owner, company, CompanyRole.OWNER);
        String subId = registerWebhookAndGetId(company, owner, "https://example.com/bad-hook");

        when(webhookRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("wrong-response"));

        mockMvc.perform(post("/companies/{id}/webhooks/{subId}/verify",
                        company.getId(), subId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{id}/webhooks/{subId}/deliveries ───────────────────────

    @Test
    void deliveries_returns200WithEmptyPage_whenNoDeliveries() throws Exception {
        User owner = createActiveUser("wh-dlv@example.com", "Password1!");
        Company company = createCompany(owner, "Delivery Webhook Co");
        addMember(owner, company, CompanyRole.OWNER);
        String subId = registerWebhookAndGetId(company, owner, "https://example.com/dlv-hook");

        mockMvc.perform(get("/companies/{id}/webhooks/{subId}/deliveries",
                        company.getId(), subId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void deliveries_returns404_forUnknownSubscription() throws Exception {
        User owner = createActiveUser("wh-dlv-404@example.com", "Password1!");
        Company company = createCompany(owner, "404 Delivery Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{id}/webhooks/{subId}/deliveries",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }
}
