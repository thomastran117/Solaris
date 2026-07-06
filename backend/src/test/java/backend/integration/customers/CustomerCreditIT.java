package backend.integration.customers;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.CreditEntryType;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerCreditIT extends AbstractIntegrationIT {

    @AfterEach
    void cleanCredits() {
        try { jdbcTemplate.execute("DELETE FROM customer_credits"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createSupportUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.SUPPORT);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private String issueCreditBody(long amountCents, CreditEntryType type) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amountCents", amountCents);
        body.put("type", type.name());
        body.put("reason", "Test credit issuance");
        return objectMapper.writeValueAsString(body);
    }

    private UUID issueCreditViaApi(User support, User customer, long amountCents) throws Exception {
        String response = mockMvc.perform(post("/support/customers/" + customer.getId() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(support)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueCreditBody(amountCents, CreditEntryType.COMPENSATION_ISSUED)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    // ── GET /me/credits ───────────────────────────────────────────────────────

    @Test
    void getMyCredits_returns200WithEmptyBalance() throws Exception {
        User user = createActiveUser("credit-me@example.com", "Password1!");

        mockMvc.perform(get("/me/credits")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.balanceCents").value(0))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.entries").isArray());
    }

    @Test
    void getMyCredits_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/me/credits"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /support/customers/{userId}/credits ───────────────────────────────

    @Test
    void getCustomerCredits_supportUser_returns200() throws Exception {
        User support = createSupportUser("support-get@example.com");
        User customer = createActiveUser("customer-get@example.com", "Password1!");

        mockMvc.perform(get("/support/customers/" + customer.getId() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(customer.getId().toString()))
                .andExpect(jsonPath("$.data.balanceCents").value(0))
                .andExpect(jsonPath("$.data.entries").isArray());
    }

    @Test
    void getCustomerCredits_regularUser_returns403() throws Exception {
        User user = createActiveUser("user-getcredits@example.com", "Password1!");
        User customer = createActiveUser("customer-get2@example.com", "Password1!");

        mockMvc.perform(get("/support/customers/" + customer.getId() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCustomerCredits_unknownUser_returns404() throws Exception {
        User support = createSupportUser("support-get404@example.com");

        mockMvc.perform(get("/support/customers/" + UUID.randomUUID() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCustomerCredits_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/support/customers/" + UUID.randomUUID() + "/credits"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /support/customers/{userId}/credits ──────────────────────────────

    @Test
    void issueCredit_returns201WithFields() throws Exception {
        User support = createSupportUser("support-issue@example.com");
        User customer = createActiveUser("customer-issue@example.com", "Password1!");

        mockMvc.perform(post("/support/customers/" + customer.getId() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(support)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueCreditBody(500L, CreditEntryType.COMPENSATION_ISSUED)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.amountCents").value(500))
                .andExpect(jsonPath("$.data.type").value("COMPENSATION_ISSUED"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.issuedById").value(support.getId().toString()));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_credits WHERE amount_cents = 500", Integer.class);
        assertEquals(1, rows, "Credit entry should be persisted");
    }

    @Test
    void issueCredit_updatesBalanceOnSubsequentGet() throws Exception {
        User support = createSupportUser("support-balance@example.com");
        User customer = createActiveUser("customer-balance@example.com", "Password1!");

        issueCreditViaApi(support, customer, 1000L);

        mockMvc.perform(get("/support/customers/" + customer.getId() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceCents").value(1000))
                .andExpect(jsonPath("$.data.entries", hasSize(1)));
    }

    @Test
    void issueCredit_missingAmountCents_returns400() throws Exception {
        User support = createSupportUser("support-bad@example.com");
        User customer = createActiveUser("customer-bad@example.com", "Password1!");

        mockMvc.perform(post("/support/customers/" + customer.getId() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(support)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "COMPENSATION_ISSUED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issueCredit_regularUser_returns403() throws Exception {
        User user = createActiveUser("user-issue@example.com", "Password1!");
        User customer = createActiveUser("customer-403@example.com", "Password1!");

        mockMvc.perform(post("/support/customers/" + customer.getId() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueCreditBody(500L, CreditEntryType.COMPENSATION_ISSUED)))
                .andExpect(status().isForbidden());
    }

    @Test
    void issueCredit_unknownCustomer_returns404() throws Exception {
        User support = createSupportUser("support-issue404@example.com");

        mockMvc.perform(post("/support/customers/" + UUID.randomUUID() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(support)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueCreditBody(500L, CreditEntryType.COMPENSATION_ISSUED)))
                .andExpect(status().isNotFound());
    }

    @Test
    void issueCredit_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/support/customers/" + UUID.randomUUID() + "/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueCreditBody(500L, CreditEntryType.COMPENSATION_ISSUED)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /support/credits/{entryId}/reverse ────────────────────────────────

    @Test
    void reverseCredit_returns200WithReversalEntry() throws Exception {
        User support = createSupportUser("support-rev@example.com");
        User customer = createActiveUser("customer-rev@example.com", "Password1!");

        UUID entryId = issueCreditViaApi(support, customer, 800L);

        mockMvc.perform(post("/support/credits/" + entryId + "/reverse")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("REVERSED"))
                .andExpect(jsonPath("$.data.amountCents").value(-800));

        // A compensating reversal entry (negative amount) must be persisted alongside the original.
        Integer reversalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_credits WHERE amount_cents = -800", Integer.class);
        assertEquals(1, reversalRows, "Reversal entry should be persisted");
    }

    @Test
    void reverseCredit_balanceBecomesZeroAfterReversal() throws Exception {
        User support = createSupportUser("support-rev-balance@example.com");
        User customer = createActiveUser("customer-rev-balance@example.com", "Password1!");

        UUID entryId = issueCreditViaApi(support, customer, 600L);

        mockMvc.perform(post("/support/credits/" + entryId + "/reverse")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isOk());

        // Balance should return to zero after reversal
        mockMvc.perform(get("/support/customers/" + customer.getId() + "/credits")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceCents").value(0));
    }

    @Test
    void reverseCredit_alreadyReversed_returns400() throws Exception {
        User support = createSupportUser("support-double-rev@example.com");
        User customer = createActiveUser("customer-double-rev@example.com", "Password1!");

        UUID entryId = issueCreditViaApi(support, customer, 300L);

        // First reversal succeeds
        mockMvc.perform(post("/support/credits/" + entryId + "/reverse")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isOk());

        // Second reversal must be rejected
        mockMvc.perform(post("/support/credits/" + entryId + "/reverse")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reverseCredit_unknownEntry_returns404() throws Exception {
        User support = createSupportUser("support-rev404@example.com");

        mockMvc.perform(post("/support/credits/" + UUID.randomUUID() + "/reverse")
                        .header("Authorization", bearer(accessTokenFor(support))))
                .andExpect(status().isNotFound());
    }

    @Test
    void reverseCredit_regularUser_returns403() throws Exception {
        User user = createActiveUser("user-rev403@example.com", "Password1!");

        mockMvc.perform(post("/support/credits/" + UUID.randomUUID() + "/reverse")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reverseCredit_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/support/credits/" + UUID.randomUUID() + "/reverse"))
                .andExpect(status().isUnauthorized());
    }
}
