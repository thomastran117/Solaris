package backend.integration.pricing;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
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

/**
 * End-to-end coverage of the admin tax-rate CRUD against the real DB: persistence + read-back,
 * jurisdiction normalization/validation, and the soft-delete behavior that keeps rows for the
 * historical {@code order.taxRateId} snapshot.
 */
class TaxRateIT extends AbstractIntegrationIT {


    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createAdminUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private String createBody(String country, String state, String postalCode,
                             String rate, boolean shippingTaxable) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("country", country);
        body.put("state", state);
        body.put("postalCode", postalCode);
        body.put("rate", rate);
        body.put("shippingTaxable", shippingTaxable);
        return objectMapper.writeValueAsString(body);
    }

    private UUID createViaApi(User admin, String country, String state, String rate) throws Exception {
        String response = mockMvc.perform(post("/admin/tax-rates")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(country, state, "", rate, false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    // ── create + read-back ──────────────────────────────────────────────────────

    @Test
    void create_persistsNormalizedRate_andReadsBack() throws Exception {
        User admin = createAdminUser("tax-admin1@example.com");

        String response = mockMvc.perform(post("/admin/tax-rates")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("us", "ca", "", "0.07250", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.country").value("US"))
                .andExpect(jsonPath("$.data.state").value("CA"))
                .andExpect(jsonPath("$.data.shippingTaxable").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());

        mockMvc.perform(get("/admin/tax-rates/" + id)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.country").value("US"))
                .andExpect(jsonPath("$.data.state").value("CA"));

        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tax_rates", Integer.class);
        assertEquals(1, rows, "Tax rate should be persisted");
    }

    @Test
    void create_oneLetterState_returns400() throws Exception {
        User admin = createAdminUser("tax-admin2@example.com");

        mockMvc.perform(post("/admin/tax-rates")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("US", "C", "", "0.07250", false)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_rateAtOrAboveOne_returns400() throws Exception {
        User admin = createAdminUser("tax-admin3@example.com");

        mockMvc.perform(post("/admin/tax-rates")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("US", "CA", "", "1.00", false)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateJurisdiction_returns409() throws Exception {
        User admin = createAdminUser("tax-admin4@example.com");
        createViaApi(admin, "US", "NY", "0.04000");

        mockMvc.perform(post("/admin/tax-rates")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("US", "NY", "", "0.05000", false)))
                .andExpect(status().isConflict());
    }

    // ── list ────────────────────────────────────────────────────────────────────

    @Test
    void list_returnsCreatedRates() throws Exception {
        User admin = createAdminUser("tax-admin5@example.com");
        createViaApi(admin, "US", "CA", "0.07250");
        createViaApi(admin, "US", "TX", "0.06250");

        mockMvc.perform(get("/admin/tax-rates")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Test
    void update_changesRateAndActiveFlag() throws Exception {
        User admin = createAdminUser("tax-admin6@example.com");
        UUID id = createViaApi(admin, "US", "WA", "0.06500");

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("rate", "0.09000");
        patch.put("active", false);

        mockMvc.perform(patch("/admin/tax-rates/" + id)
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate").value(closeTo(0.09000, 0.00001)))
                .andExpect(jsonPath("$.data.active").value(false));

        assertEquals(Boolean.FALSE,
                jdbcTemplate.queryForObject("SELECT active FROM tax_rates", Boolean.class),
                "Active-flag change should be persisted");
    }

    // ── soft delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_softDeactivates_keepingRowForSnapshotReferences() throws Exception {
        User admin = createAdminUser("tax-admin7@example.com");
        UUID id = createViaApi(admin, "US", "FL", "0.06000");

        mockMvc.perform(delete("/admin/tax-rates/" + id)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNoContent());

        // Soft delete: the row must physically remain in the table (for historical
        // order.taxRateId joins) but be marked inactive.
        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tax_rates", Integer.class),
                "Soft-deleted tax rate row should remain in the database");
        assertEquals(Boolean.FALSE,
                jdbcTemplate.queryForObject("SELECT active FROM tax_rates", Boolean.class),
                "Soft-deleted tax rate should be inactive in the database");

        // Row still exists (so historical order.taxRateId joins resolve), but is now inactive.
        mockMvc.perform(get("/admin/tax-rates/" + id)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    // ── authz ────────────────────────────────────────────────────────────────────

    @Test
    void create_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("tax-user@example.com", "Password1!");

        mockMvc.perform(post("/admin/tax-rates")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("US", "CA", "", "0.07250", false)))
                .andExpect(status().isForbidden());
    }
}
