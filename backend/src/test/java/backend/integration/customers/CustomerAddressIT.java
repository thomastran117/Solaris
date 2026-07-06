package backend.integration.customers;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.CustomerAddress;
import backend.models.core.User;
import backend.repositories.CustomerAddressRepository;
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

class CustomerAddressIT extends AbstractIntegrationIT {

    @Autowired private CustomerAddressRepository addressRepository;

    @AfterEach
    void cleanAddresses() {
        try { jdbcTemplate.execute("DELETE FROM customer_addresses"); } catch (Exception ignored) {}
    }

    // All responses wrapped: { success, data, message, meta }
    // List responses:   data = [items]
    // Single responses: data = { id, label, ... }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CustomerAddress createAddress(User user, String label, boolean isDefault) {
        CustomerAddress addr = new CustomerAddress();
        addr.setUser(user);
        addr.setLabel(label);
        addr.setRecipientName("Test User");
        addr.setStreet("123 Main St");
        addr.setCity("Springfield");
        addr.setState("IL");
        addr.setPostalCode("62701");
        addr.setCountry("US");
        addr.setDefault(isDefault);
        return addressRepository.save(addr);
    }

    private Map<String, Object> validAddressBody(String label) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", label);
        body.put("recipientName", "Jane Doe");
        body.put("street", "456 Oak Ave");
        body.put("city", "Chicago");
        body.put("state", "IL");
        body.put("postalCode", "60601");
        body.put("country", "US");
        return body;
    }

    // ── GET /addresses ────────────────────────────────────────────────────────

    @Test
    void listAddresses_returnsEmptyListWhenNone() throws Exception {
        User user = createActiveUser("addr-list-empty@example.com", "Password1!");

        mockMvc.perform(get("/addresses")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listAddresses_returnsOwnAddressesOnly() throws Exception {
        User user = createActiveUser("addr-list-own@example.com", "Password1!");
        User other = createActiveUser("addr-list-other@example.com", "Password1!");
        createAddress(user, "Home", false);
        createAddress(other, "Work", false);

        mockMvc.perform(get("/addresses")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].label").value("Home"));
    }

    @Test
    void listAddresses_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/addresses"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /addresses/{id} ───────────────────────────────────────────────────

    @Test
    void getAddress_returnsAddressForOwner() throws Exception {
        User user = createActiveUser("addr-get@example.com", "Password1!");
        CustomerAddress addr = createAddress(user, "Home", false);

        mockMvc.perform(get("/addresses/{id}", addr.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(addr.getId().toString()))
                .andExpect(jsonPath("$.data.label").value("Home"))
                .andExpect(jsonPath("$.data.city").value("Springfield"));
    }

    @Test
    void getAddress_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("addr-get-owner@example.com", "Password1!");
        User other = createActiveUser("addr-get-other@example.com", "Password1!");
        CustomerAddress addr = createAddress(owner, "Home", false);

        mockMvc.perform(get("/addresses/{id}", addr.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAddress_returns404ForUnknownId() throws Exception {
        User user = createActiveUser("addr-get-unknown@example.com", "Password1!");

        mockMvc.perform(get("/addresses/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /addresses ───────────────────────────────────────────────────────

    @Test
    void createAddress_returns201WithNewAddress() throws Exception {
        User user = createActiveUser("addr-create@example.com", "Password1!");

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAddressBody("Work")))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.label").value("Work"))
                .andExpect(jsonPath("$.data.city").value("Chicago"))
                .andExpect(jsonPath("$.data.country").value("US"));

        CustomerAddress persisted = addressRepository.findAll().get(0);
        assertEquals("Work", persisted.getLabel());
        assertEquals("Chicago", persisted.getCity());
    }

    @Test
    void createAddress_returns400WhenRequiredFieldsMissing() throws Exception {
        User user = createActiveUser("addr-create-invalid@example.com", "Password1!");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", "Incomplete");
        // missing required recipientName, street, city, state, postalCode, country

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAddress_returns400ForInvalidCountryCode() throws Exception {
        User user = createActiveUser("addr-create-country@example.com", "Password1!");

        Map<String, Object> body = validAddressBody("Home");
        body.put("country", "USA"); // must be 2-letter code

        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAddress_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAddressBody("Home"))))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /addresses/{id} ───────────────────────────────────────────────────

    @Test
    void updateAddress_updatesLabelAndCity() throws Exception {
        User user = createActiveUser("addr-update@example.com", "Password1!");
        CustomerAddress addr = createAddress(user, "Old Label", false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", "New Label");
        body.put("city", "Rockford");

        mockMvc.perform(put("/addresses/{id}", addr.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("New Label"))
                .andExpect(jsonPath("$.data.city").value("Rockford"));

        CustomerAddress updated = addressRepository.findById(addr.getId()).orElseThrow();
        assertEquals("New Label", updated.getLabel());
        assertEquals("Rockford", updated.getCity());
    }

    @Test
    void updateAddress_returns400ForInvalidCountryCode() throws Exception {
        User user = createActiveUser("addr-update-country@example.com", "Password1!");
        CustomerAddress addr = createAddress(user, "Home", false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("country", "United States"); // must be 2-letter code

        mockMvc.perform(put("/addresses/{id}", addr.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAddress_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("addr-update-owner@example.com", "Password1!");
        User other = createActiveUser("addr-update-other@example.com", "Password1!");
        CustomerAddress addr = createAddress(owner, "Home", false);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", "Hijacked");

        mockMvc.perform(put("/addresses/{id}", addr.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /addresses/{id} ────────────────────────────────────────────────

    @Test
    void deleteAddress_returns204AndAddressIsGone() throws Exception {
        User user = createActiveUser("addr-delete@example.com", "Password1!");
        CustomerAddress addr = createAddress(user, "ToDelete", false);

        mockMvc.perform(delete("/addresses/{id}", addr.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        assertTrue(addressRepository.findById(addr.getId()).isEmpty(),
                "Deleted address should be removed from the database");

        mockMvc.perform(get("/addresses/{id}", addr.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAddress_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("addr-delete-owner@example.com", "Password1!");
        User other = createActiveUser("addr-delete-other@example.com", "Password1!");
        CustomerAddress addr = createAddress(owner, "Home", false);

        mockMvc.perform(delete("/addresses/{id}", addr.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /addresses/{id}/default ─────────────────────────────────────────

    @Test
    void setDefault_marksAddressAsDefault() throws Exception {
        User user = createActiveUser("addr-default@example.com", "Password1!");
        createAddress(user, "Primary", true);
        CustomerAddress second = createAddress(user, "Secondary", false);

        mockMvc.perform(patch("/addresses/{id}/default", second.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(second.getId().toString()))
                .andExpect(jsonPath("$.data['default']").value(true));

        assertTrue(addressRepository.findById(second.getId()).orElseThrow().isDefault(),
                "Address should be marked default in the database");
    }

    @Test
    void setDefault_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("addr-default-owner@example.com", "Password1!");
        User other = createActiveUser("addr-default-other@example.com", "Password1!");
        CustomerAddress addr = createAddress(owner, "Home", false);

        mockMvc.perform(patch("/addresses/{id}/default", addr.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void setDefault_returns404ForUnknownId() throws Exception {
        User user = createActiveUser("addr-default-unknown@example.com", "Password1!");

        mockMvc.perform(patch("/addresses/{id}/default", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }
}
