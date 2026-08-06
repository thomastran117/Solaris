package backend.integration.customers;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
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

class CustomerSegmentIT extends AbstractIntegrationIT {

    @AfterEach
    void cleanSegments() {
        // user_segments must be deleted before customer_segments (FK).
        // The base @AfterEach also deletes user_segments, but runs after this method,
        // so we delete it here first to unblock the customer_segments DELETE.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createAdminUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private String createSegmentBody(String code, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", name);
        return objectMapper.writeValueAsString(body);
    }

    private UUID createSegmentViaApi(User admin, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSegmentBody(code, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    // ── GET /admin/customer-segments ─────────────────────────────────────────

    @Test
    void listSegments_empty_returns200WithEmptyList() throws Exception {
        User admin = createAdminUser("admin-list@example.com");

        mockMvc.perform(get("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listSegments_afterCreate_returnsSegment() throws Exception {
        User admin = createAdminUser("admin-list2@example.com");
        createSegmentViaApi(admin, "VIP", "VIP Customers");

        mockMvc.perform(get("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("VIP"))
                .andExpect(jsonPath("$.data[0].name").value("VIP Customers"));
    }

    @Test
    void listSegments_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("user-list@example.com", "Password1!");

        mockMvc.perform(get("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSegments_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/customer-segments"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /admin/customer-segments/{segmentId} ──────────────────────────────

    @Test
    void getSegment_returns200WithFields() throws Exception {
        User admin = createAdminUser("admin-get@example.com");
        UUID segmentId = createSegmentViaApi(admin, "WHOLESALE", "Wholesale");

        mockMvc.perform(get("/admin/customer-segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(segmentId.toString()))
                .andExpect(jsonPath("$.data.code").value("WHOLESALE"))
                .andExpect(jsonPath("$.data.name").value("Wholesale"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void getSegment_unknownId_returns404() throws Exception {
        User admin = createAdminUser("admin-get404@example.com");

        mockMvc.perform(get("/admin/customer-segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSegment_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("user-get@example.com", "Password1!");

        mockMvc.perform(get("/admin/customer-segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSegment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/customer-segments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /admin/customer-segments ─────────────────────────────────────────

    @Test
    void createSegment_returns201WithFields() throws Exception {
        User admin = createAdminUser("admin-create@example.com");

        mockMvc.perform(post("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSegmentBody("GOLD", "Gold Members")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.code").value("GOLD"))
                .andExpect(jsonPath("$.data.name").value("Gold Members"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_segments WHERE code = 'GOLD'", Integer.class);
        assertEquals(1, rows, "Segment should be persisted");
    }

    @Test
    void createSegment_codeIsUppercased() throws Exception {
        User admin = createAdminUser("admin-upper@example.com");

        mockMvc.perform(post("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSegmentBody("silver", "Silver Members")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SILVER"));
    }

    @Test
    void createSegment_duplicateCode_returns409() throws Exception {
        User admin = createAdminUser("admin-dup@example.com");
        createSegmentViaApi(admin, "PREMIUM", "Premium");

        mockMvc.perform(post("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSegmentBody("PREMIUM", "Premium Duplicate")))
                .andExpect(status().isConflict());
    }

    @Test
    void createSegment_invalidCodeWithSpaces_returns400() throws Exception {
        User admin = createAdminUser("admin-badcode@example.com");

        mockMvc.perform(post("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSegmentBody("INVALID CODE", "Bad Code")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSegment_missingCode_returns400() throws Exception {
        User admin = createAdminUser("admin-nocode@example.com");

        mockMvc.perform(post("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "No Code"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSegment_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("user-create@example.com", "Password1!");

        mockMvc.perform(post("/admin/customer-segments")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSegmentBody("FOO", "Foo")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSegment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/customer-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSegmentBody("FOO", "Foo")))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /admin/customer-segments/{segmentId} ────────────────────────────

    @Test
    void updateSegment_updatesName_returns200() throws Exception {
        User admin = createAdminUser("admin-update@example.com");
        UUID segmentId = createSegmentViaApi(admin, "BRONZE", "Bronze");

        mockMvc.perform(patch("/admin/customer-segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Bronze Members Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Bronze Members Updated"))
                .andExpect(jsonPath("$.data.code").value("BRONZE"));

        assertEquals("Bronze Members Updated",
                jdbcTemplate.queryForObject("SELECT name FROM customer_segments", String.class));
    }

    @Test
    void updateSegment_unknownId_returns404() throws Exception {
        User admin = createAdminUser("admin-update404@example.com");

        mockMvc.perform(patch("/admin/customer-segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Name"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSegment_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("user-update@example.com", "Password1!");

        mockMvc.perform(patch("/admin/customer-segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Bad"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSegment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/admin/customer-segments/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Bad"))))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /admin/customer-segments/{segmentId} ───────────────────────────

    @Test
    void deleteSegment_returns204() throws Exception {
        User admin = createAdminUser("admin-delete@example.com");
        UUID segmentId = createSegmentViaApi(admin, "DELETE_ME", "Delete Me");

        mockMvc.perform(delete("/admin/customer-segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer_segments", Integer.class);
        assertEquals(0, rows, "Deleted segment should be removed from the database");
    }

    @Test
    void deleteSegment_deletedSegmentIsGone_returns404() throws Exception {
        User admin = createAdminUser("admin-delete2@example.com");
        UUID segmentId = createSegmentViaApi(admin, "GONE", "Gone");

        mockMvc.perform(delete("/admin/customer-segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/customer-segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSegment_unknownId_returns404() throws Exception {
        User admin = createAdminUser("admin-delete404@example.com");

        mockMvc.perform(delete("/admin/customer-segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSegment_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("user-delete@example.com", "Password1!");

        mockMvc.perform(delete("/admin/customer-segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSegment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/admin/customer-segments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /admin/users/{userId}/segments/{segmentId} ───────────────────────

    @Test
    void assignSegment_returns204() throws Exception {
        User admin = createAdminUser("admin-assign@example.com");
        User customer = createActiveUser("assign-cust@example.com", "Password1!");
        UUID segmentId = createSegmentViaApi(admin, "ASSIGN_TEST", "Assign Test");

        mockMvc.perform(post("/admin/users/" + customer.getId() + "/segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_segments", Integer.class);
        assertEquals(1, rows, "Segment assignment should be persisted");
    }

    @Test
    void assignSegment_unknownUser_returns404() throws Exception {
        User admin = createAdminUser("admin-assign404u@example.com");
        UUID segmentId = createSegmentViaApi(admin, "ASSIGN_U404", "Assign U404");

        mockMvc.perform(post("/admin/users/" + UUID.randomUUID() + "/segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignSegment_unknownSegment_returns404() throws Exception {
        User admin = createAdminUser("admin-assign404s@example.com");
        User customer = createActiveUser("assign-cust2@example.com", "Password1!");

        mockMvc.perform(post("/admin/users/" + customer.getId() + "/segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignSegment_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("user-assign@example.com", "Password1!");

        mockMvc.perform(post("/admin/users/" + UUID.randomUUID() + "/segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignSegment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/users/" + UUID.randomUUID() + "/segments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /admin/users/{userId}/segments/{segmentId} ─────────────────────

    @Test
    void removeSegment_afterAssign_returns204() throws Exception {
        User admin = createAdminUser("admin-remove@example.com");
        User customer = createActiveUser("remove-cust@example.com", "Password1!");
        UUID segmentId = createSegmentViaApi(admin, "REMOVE_TEST", "Remove Test");

        mockMvc.perform(post("/admin/users/" + customer.getId() + "/segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/admin/users/" + customer.getId() + "/segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_segments", Integer.class);
        assertEquals(0, rows, "Segment assignment should be removed from the database");
    }

    @Test
    void removeSegment_unknownUser_returns404() throws Exception {
        User admin = createAdminUser("admin-remove404u@example.com");
        UUID segmentId = createSegmentViaApi(admin, "REMOVE_U404", "Remove U404");

        mockMvc.perform(delete("/admin/users/" + UUID.randomUUID() + "/segments/" + segmentId)
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeSegment_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("user-remove@example.com", "Password1!");

        mockMvc.perform(delete("/admin/users/" + UUID.randomUUID() + "/segments/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeSegment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/admin/users/" + UUID.randomUUID() + "/segments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
