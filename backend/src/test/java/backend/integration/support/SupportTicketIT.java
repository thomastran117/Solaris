package backend.integration.support;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.SupportTicket;
import backend.models.core.User;
import backend.models.enums.TicketCategory;
import backend.models.enums.TicketStatus;
import backend.models.enums.UserRole;
import backend.repositories.SupportTicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportTicketIT extends AbstractIntegrationIT {

    @Autowired private SupportTicketRepository ticketRepository;

    @AfterEach
    void cleanTickets() {
        try { jdbcTemplate.execute("DELETE FROM support_ticket_messages"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM support_tickets"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createStaffUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.SUPPORT);
        user.setStatus(backend.models.enums.UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private String createTicketBody(String subject, String description, TicketCategory category) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", subject);
        body.put("description", description);
        body.put("category", category.name());
        return objectMapper.writeValueAsString(body);
    }

    private UUID createTicketViaApi(User user, String subject) throws Exception {
        String response = mockMvc.perform(post("/support/tickets")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketBody(subject, "Test description", TicketCategory.OTHER)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    private void closeTicketDirect(UUID ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket);
    }

    // ── POST /support/tickets ─────────────────────────────────────────────────

    @Test
    void createTicket_returns201WithTicketData() throws Exception {
        User user = createActiveUser("ticket-create@example.com", "Password1!");

        mockMvc.perform(post("/support/tickets")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketBody("My subject", "My description", TicketCategory.BILLING)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.subject").value("My subject"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.category").value("BILLING"));
    }

    @Test
    void createTicket_returns400WhenSubjectMissing() throws Exception {
        User user = createActiveUser("ticket-nosubject@example.com", "Password1!");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "Some description");
        body.put("category", "OTHER");

        mockMvc.perform(post("/support/tickets")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTicket_returns400WhenCategoryMissing() throws Exception {
        User user = createActiveUser("ticket-nocat@example.com", "Password1!");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", "Subject");
        body.put("description", "Description");

        mockMvc.perform(post("/support/tickets")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTicket_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/support/tickets")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketBody("Subject", "Description", TicketCategory.OTHER)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /support/tickets ──────────────────────────────────────────────────

    @Test
    void listTickets_userSeesOwnTicketsOnly() throws Exception {
        User userA = createActiveUser("ticket-list-a@example.com", "Password1!");
        User userB = createActiveUser("ticket-list-b@example.com", "Password1!");
        createTicketViaApi(userA, "User A ticket");
        createTicketViaApi(userB, "User B ticket");

        mockMvc.perform(get("/support/tickets")
                        .header("Authorization", bearer(accessTokenFor(userA)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].subject").value("User A ticket"));
    }

    @Test
    void listTickets_staffSeesAllTickets() throws Exception {
        User userA = createActiveUser("ticket-staff-a@example.com", "Password1!");
        User userB = createActiveUser("ticket-staff-b@example.com", "Password1!");
        User staff = createStaffUser("ticket-staff@example.com");
        createTicketViaApi(userA, "Ticket A");
        createTicketViaApi(userB, "Ticket B");

        mockMvc.perform(get("/support/tickets")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void listTickets_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/support/tickets")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /support/tickets/{id} ─────────────────────────────────────────────

    @Test
    void getTicket_returnsOwnTicket() throws Exception {
        User user = createActiveUser("ticket-get@example.com", "Password1!");
        UUID ticketId = createTicketViaApi(user, "My ticket");

        mockMvc.perform(get("/support/tickets/{id}", ticketId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.subject").value("My ticket"));
    }

    @Test
    void getTicket_staffCanViewAnyTicket() throws Exception {
        User user = createActiveUser("ticket-get-owner@example.com", "Password1!");
        User staff = createStaffUser("ticket-get-staff@example.com");
        UUID ticketId = createTicketViaApi(user, "User ticket");

        mockMvc.perform(get("/support/tickets/{id}", ticketId)
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ticketId.toString()));
    }

    @Test
    void getTicket_returns403ForOtherUser() throws Exception {
        User owner = createActiveUser("ticket-owner@example.com", "Password1!");
        User other = createActiveUser("ticket-other@example.com", "Password1!");
        UUID ticketId = createTicketViaApi(owner, "Private ticket");

        mockMvc.perform(get("/support/tickets/{id}", ticketId)
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTicket_returns404ForUnknownId() throws Exception {
        User user = createActiveUser("ticket-404@example.com", "Password1!");

        mockMvc.perform(get("/support/tickets/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTicket_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/support/tickets/{id}", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /support/tickets/{id}/messages ───────────────────────────────────

    @Test
    void addMessage_ownerCanAddMessageToOwnTicket() throws Exception {
        User user = createActiveUser("ticket-msg-owner@example.com", "Password1!");
        UUID ticketId = createTicketViaApi(user, "Ticket for messages");

        Map<String, String> body = Map.of("body", "Hello, I need help");

        mockMvc.perform(post("/support/tickets/{id}/messages", ticketId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.body").value("Hello, I need help"));
    }

    @Test
    void addMessage_staffCanAddMessageToAnyTicket() throws Exception {
        User user = createActiveUser("ticket-msg-user@example.com", "Password1!");
        User staff = createStaffUser("ticket-msg-staff@example.com");
        UUID ticketId = createTicketViaApi(user, "Ticket for staff message");

        Map<String, String> body = Map.of("body", "Staff reply");

        mockMvc.perform(post("/support/tickets/{id}/messages", ticketId)
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.body").value("Staff reply"));
    }

    @Test
    void addMessage_returns409WhenTicketIsClosed() throws Exception {
        User user = createActiveUser("ticket-closed@example.com", "Password1!");
        UUID ticketId = createTicketViaApi(user, "Closed ticket");
        closeTicketDirect(ticketId);

        Map<String, String> body = Map.of("body", "Trying to message closed ticket");

        mockMvc.perform(post("/support/tickets/{id}/messages", ticketId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void addMessage_returns403ForUnrelatedUser() throws Exception {
        User owner = createActiveUser("ticket-msg-real@example.com", "Password1!");
        User other = createActiveUser("ticket-msg-intruder@example.com", "Password1!");
        UUID ticketId = createTicketViaApi(owner, "Owner ticket");

        Map<String, String> body = Map.of("body", "Unauthorized message");

        mockMvc.perform(post("/support/tickets/{id}/messages", ticketId)
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addMessage_returns400WhenBodyBlank() throws Exception {
        User user = createActiveUser("ticket-msg-blank@example.com", "Password1!");
        UUID ticketId = createTicketViaApi(user, "Ticket for blank message");

        Map<String, String> body = Map.of("body", "");

        mockMvc.perform(post("/support/tickets/{id}/messages", ticketId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /support/tickets/{id}/status ────────────────────────────────────

    @Test
    void updateStatus_staffCanChangeStatus() throws Exception {
        User user = createActiveUser("ticket-status-user@example.com", "Password1!");
        User staff = createStaffUser("ticket-status-staff@example.com");
        UUID ticketId = createTicketViaApi(user, "Ticket for status change");

        Map<String, String> body = Map.of("status", "RESOLVED");

        mockMvc.perform(patch("/support/tickets/{id}/status", ticketId)
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    void updateStatus_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("ticket-status-403@example.com", "Password1!");
        UUID ticketId = createTicketViaApi(user, "Ticket for forbidden status change");

        Map<String, String> body = Map.of("status", "RESOLVED");

        mockMvc.perform(patch("/support/tickets/{id}/status", ticketId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /support/tickets/{id}/assign ────────────────────────────────────

    @Test
    void assignTicket_staffCanAssignToAnotherStaff() throws Exception {
        User user = createActiveUser("ticket-assign-user@example.com", "Password1!");
        User staff1 = createStaffUser("ticket-assign-staff1@example.com");
        User staff2 = createStaffUser("ticket-assign-staff2@example.com");
        UUID ticketId = createTicketViaApi(user, "Ticket for assignment");

        Map<String, String> body = Map.of("staffUserId", staff2.getId().toString());

        mockMvc.perform(patch("/support/tickets/{id}/assign", ticketId)
                        .header("Authorization", bearer(accessTokenFor(staff1)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedToId").value(staff2.getId().toString()));
    }

    @Test
    void assignTicket_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("ticket-assign-403@example.com", "Password1!");
        User staff = createStaffUser("ticket-assign-target-staff@example.com");
        UUID ticketId = createTicketViaApi(user, "Ticket for forbidden assignment");

        Map<String, String> body = Map.of("staffUserId", staff.getId().toString());

        mockMvc.perform(patch("/support/tickets/{id}/assign", ticketId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /support/tickets/{id}/priority ─────────────────────────────────

    @Test
    void updatePriority_staffCanChangePriority() throws Exception {
        User user = createActiveUser("ticket-prio-user@example.com", "Password1!");
        User staff = createStaffUser("ticket-prio-staff@example.com");
        UUID ticketId = createTicketViaApi(user, "Ticket for priority change");

        Map<String, String> body = Map.of("priority", "HIGH");

        mockMvc.perform(patch("/support/tickets/{id}/priority", ticketId)
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("HIGH"));
    }

    @Test
    void updatePriority_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("ticket-prio-403@example.com", "Password1!");
        UUID ticketId = createTicketViaApi(user, "Ticket for forbidden priority change");

        Map<String, String> body = Map.of("priority", "HIGH");

        mockMvc.perform(patch("/support/tickets/{id}/priority", ticketId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }
}
