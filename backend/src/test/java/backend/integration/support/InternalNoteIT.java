package backend.integration.support;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.NoteEntityType;
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

class InternalNoteIT extends AbstractIntegrationIT {


    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createStaffUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.SUPPORT);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private User createAdminUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private String noteBody(NoteEntityType entityType, UUID entityId, String body) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entityType", entityType.name());
        map.put("entityId", entityId.toString());
        map.put("body", body);
        return objectMapper.writeValueAsString(map);
    }

    private UUID addNoteViaApi(User staff, NoteEntityType entityType, UUID entityId, String body) throws Exception {
        String response = mockMvc.perform(post("/support/notes")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteBody(entityType, entityId, body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    // ── POST /support/notes ───────────────────────────────────────────────────

    @Test
    void addNote_staffCanAddNote() throws Exception {
        User staff = createStaffUser("note-add-staff@example.com");
        UUID entityId = UUID.randomUUID();

        mockMvc.perform(post("/support/notes")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteBody(NoteEntityType.TICKET, entityId, "Internal note body")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.body").value("Internal note body"))
                .andExpect(jsonPath("$.data.entityType").value("TICKET"))
                .andExpect(jsonPath("$.data.entityId").value(entityId.toString()));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM internal_notes", Integer.class);
        assertEquals(1, rows, "Internal note should be persisted");
    }

    @Test
    void addNote_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("note-add-user@example.com", "Password1!");
        UUID entityId = UUID.randomUUID();

        mockMvc.perform(post("/support/notes")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteBody(NoteEntityType.TICKET, entityId, "Should be forbidden")))
                .andExpect(status().isForbidden());
    }

    @Test
    void addNote_returns400WhenBodyBlank() throws Exception {
        User staff = createStaffUser("note-blank-staff@example.com");
        UUID entityId = UUID.randomUUID();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entityType", "TICKET");
        body.put("entityId", entityId.toString());
        body.put("body", "");

        mockMvc.perform(post("/support/notes")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addNote_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/support/notes")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noteBody(NoteEntityType.TICKET, UUID.randomUUID(), "Unauthenticated note")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /support/notes ────────────────────────────────────────────────────

    @Test
    void listNotes_staffCanListNotesForEntity() throws Exception {
        User staff = createStaffUser("note-list-staff@example.com");
        UUID entityId = UUID.randomUUID();
        addNoteViaApi(staff, NoteEntityType.TICKET, entityId, "First note");
        addNoteViaApi(staff, NoteEntityType.TICKET, entityId, "Second note");

        mockMvc.perform(get("/support/notes")
                        .param("entityType", "TICKET")
                        .param("entityId", entityId.toString())
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void listNotes_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("note-list-user@example.com", "Password1!");

        mockMvc.perform(get("/support/notes")
                        .param("entityType", "TICKET")
                        .param("entityId", UUID.randomUUID().toString())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void listNotes_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/support/notes")
                        .param("entityType", "TICKET")
                        .param("entityId", UUID.randomUUID().toString())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /support/notes/{id} ────────────────────────────────────────────

    @Test
    void deleteNote_authorCanDeleteOwnNote() throws Exception {
        User staff = createStaffUser("note-del-author@example.com");
        UUID noteId = addNoteViaApi(staff, NoteEntityType.ORDER, UUID.randomUUID(), "Deletable note");

        mockMvc.perform(delete("/support/notes/{id}", noteId)
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNote_adminCanDeleteAnyNote() throws Exception {
        User staff = createStaffUser("note-del-staff@example.com");
        User admin = createAdminUser("note-del-admin@example.com");
        UUID noteId = addNoteViaApi(staff, NoteEntityType.ORDER, UUID.randomUUID(), "Staff note");

        mockMvc.perform(delete("/support/notes/{id}", noteId)
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNote_returns403WhenOtherStaffTriesToDelete() throws Exception {
        User author = createStaffUser("note-del-author2@example.com");
        User otherStaff = createStaffUser("note-del-other@example.com");
        UUID noteId = addNoteViaApi(author, NoteEntityType.ORDER, UUID.randomUUID(), "Author's note");

        mockMvc.perform(delete("/support/notes/{id}", noteId)
                        .header("Authorization", bearer(accessTokenFor(otherStaff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteNote_returns404ForUnknownId() throws Exception {
        User staff = createStaffUser("note-del-404@example.com");

        mockMvc.perform(delete("/support/notes/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }
}
