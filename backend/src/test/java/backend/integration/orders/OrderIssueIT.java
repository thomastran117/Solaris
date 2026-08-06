package backend.integration.orders;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Order;
import backend.models.core.OrderIssue;
import backend.models.core.User;
import backend.models.enums.OrderIssueState;
import backend.models.enums.OrderIssueType;
import backend.models.enums.OrderStatus;
import backend.models.enums.UserRole;
import backend.repositories.OrderIssueRepository;
import backend.repositories.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderIssueIT extends AbstractIntegrationIT {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderIssueRepository issueRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Order createOrder(User customer, OrderStatus status) {
        Order order = new Order();
        order.setUser(customer);
        order.setTotalAmount(BigDecimal.valueOf(49.99));
        order.setStatus(status);
        return orderRepository.save(order);
    }

    private User createStaffUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.SUPPORT);
        user.setStatus(backend.models.enums.UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private OrderIssue createIssueDirect(Order order, User reporter, OrderIssueState state) {
        OrderIssue issue = new OrderIssue();
        issue.setOrder(order);
        issue.setReportedBy(reporter);
        issue.setType(OrderIssueType.DAMAGED);
        issue.setState(state);
        return issueRepository.save(issue);
    }

    // ── POST /orders/{orderId}/issues ─────────────────────────────────────────

    @Test
    void openIssue_returns201ForOrderOwner() throws Exception {
        User customer = createActiveUser("issue-open@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);

        MvcResult openResult = mockMvc.perform(post("/orders/{oid}/issues", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NOT_RECEIVED\",\"description\":\"Item never arrived\",\"openTicket\":false}")
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("NOT_RECEIVED"))
                .andExpect(jsonPath("$.data.state").value("REPORTED"))
                .andReturn();

        UUID issueId = UUID.fromString(objectMapper.readTree(
                openResult.getResponse().getContentAsString()).path("data").path("id").asText());
        OrderIssue persisted = issueRepository.findById(issueId).orElseThrow();
        assertEquals(OrderIssueState.REPORTED, persisted.getState());
        assertEquals(OrderIssueType.NOT_RECEIVED, persisted.getType());
    }

    @Test
    void openIssue_returns404ForUnknownOrder() throws Exception {
        User customer = createActiveUser("issue-open-404@example.com", "Password1!");

        mockMvc.perform(post("/orders/{oid}/issues", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DAMAGED\",\"openTicket\":false}")
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void openIssue_returns403ForNonOwner() throws Exception {
        User customer = createActiveUser("issue-open-403-cust@example.com", "Password1!");
        User other = createActiveUser("issue-open-403-other@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);

        mockMvc.perform(post("/orders/{oid}/issues", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DAMAGED\",\"openTicket\":false}")
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void openIssue_returns400WhenTypeMissing() throws Exception {
        User customer = createActiveUser("issue-open-400@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);

        mockMvc.perform(post("/orders/{oid}/issues", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTicket\":false}")
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openIssue_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/orders/{oid}/issues", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DAMAGED\",\"openTicket\":false}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /orders/{orderId}/issues ──────────────────────────────────────────

    @Test
    void getIssuesByOrder_returns200ForOwner() throws Exception {
        User customer = createActiveUser("issue-list-owner@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        createIssueDirect(order, customer, OrderIssueState.REPORTED);

        mockMvc.perform(get("/orders/{oid}/issues", order.getId())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void getIssuesByOrder_returns200ForStaff() throws Exception {
        User customer = createActiveUser("issue-list-staff-cust@example.com", "Password1!");
        User staff = createStaffUser("issue-list-staff@example.com");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        createIssueDirect(order, customer, OrderIssueState.REPORTED);

        mockMvc.perform(get("/orders/{oid}/issues", order.getId())
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void getIssuesByOrder_returns403ForNonOwner() throws Exception {
        User customer = createActiveUser("issue-list-403-cust@example.com", "Password1!");
        User other = createActiveUser("issue-list-403-other@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);

        mockMvc.perform(get("/orders/{oid}/issues", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getIssuesByOrder_returns401WhenUnauthenticated() throws Exception {
        User customer = createActiveUser("issue-list-unauth-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);

        mockMvc.perform(get("/orders/{oid}/issues", order.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /support/issues ───────────────────────────────────────────────────

    @Test
    void listIssues_returns200ForStaff() throws Exception {
        User staff = createStaffUser("support-list@example.com");
        User customer = createActiveUser("support-list-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        createIssueDirect(order, customer, OrderIssueState.REPORTED);

        mockMvc.perform(get("/support/issues")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void listIssues_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("support-list-403@example.com", "Password1!");

        mockMvc.perform(get("/support/issues")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIssues_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/support/issues"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /support/issues/{id}/transition ──────────────────────────────────

    @Test
    void transition_returns200ForStaff() throws Exception {
        User staff = createStaffUser("transition-staff@example.com");
        User customer = createActiveUser("transition-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        OrderIssue issue = createIssueDirect(order, customer, OrderIssueState.REPORTED);

        mockMvc.perform(post("/support/issues/{id}/transition", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"INVESTIGATING\"}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("INVESTIGATING"));

        assertEquals(OrderIssueState.INVESTIGATING,
                issueRepository.findById(issue.getId()).orElseThrow().getState());
    }

    @Test
    void transition_returns400WhenStateMissing() throws Exception {
        User staff = createStaffUser("transition-400-staff@example.com");
        User customer = createActiveUser("transition-400-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        OrderIssue issue = createIssueDirect(order, customer, OrderIssueState.REPORTED);

        mockMvc.perform(post("/support/issues/{id}/transition", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transition_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("transition-403@example.com", "Password1!");

        mockMvc.perform(post("/support/issues/{id}/transition", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"INVESTIGATING\"}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void transition_returns404ForUnknownIssue() throws Exception {
        User staff = createStaffUser("transition-404-staff@example.com");

        mockMvc.perform(post("/support/issues/{id}/transition", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"INVESTIGATING\"}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /support/issues/{id}/resolve/refund ──────────────────────────────

    @Test
    void resolveWithRefund_returns200ForStaff() throws Exception {
        User staff = createStaffUser("refund-staff@example.com");
        User customer = createActiveUser("refund-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        OrderIssue issue = createIssueDirect(order, customer, OrderIssueState.INVESTIGATING);

        mockMvc.perform(post("/support/issues/{id}/resolve/refund", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundAmountCents\":1000,\"reason\":\"Item was damaged\"}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("RESOLVED_REFUND"));

        assertEquals(OrderIssueState.RESOLVED_REFUND,
                issueRepository.findById(issue.getId()).orElseThrow().getState());
    }

    @Test
    void resolveWithRefund_returns400WhenAmountMissing() throws Exception {
        User staff = createStaffUser("refund-400-staff@example.com");
        User customer = createActiveUser("refund-400-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        OrderIssue issue = createIssueDirect(order, customer, OrderIssueState.INVESTIGATING);

        mockMvc.perform(post("/support/issues/{id}/resolve/refund", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolveWithRefund_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("refund-403@example.com", "Password1!");

        mockMvc.perform(post("/support/issues/{id}/resolve/refund", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundAmountCents\":1000}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── POST /support/issues/{id}/resolve/credit ──────────────────────────────

    @Test
    void resolveWithCredit_returns200ForStaff() throws Exception {
        User staff = createStaffUser("credit-staff@example.com");
        User customer = createActiveUser("credit-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        OrderIssue issue = createIssueDirect(order, customer, OrderIssueState.INVESTIGATING);

        mockMvc.perform(post("/support/issues/{id}/resolve/credit", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":500,\"reason\":\"Goodwill credit\"}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("RESOLVED_CREDIT"));

        assertEquals(OrderIssueState.RESOLVED_CREDIT,
                issueRepository.findById(issue.getId()).orElseThrow().getState());
    }

    @Test
    void resolveWithCredit_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("credit-403@example.com", "Password1!");

        mockMvc.perform(post("/support/issues/{id}/resolve/credit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":500}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── POST /support/issues/{id}/reject ──────────────────────────────────────

    @Test
    void rejectIssue_returns200ForStaff() throws Exception {
        User staff = createStaffUser("reject-staff@example.com");
        User customer = createActiveUser("reject-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        OrderIssue issue = createIssueDirect(order, customer, OrderIssueState.INVESTIGATING);

        mockMvc.perform(post("/support/issues/{id}/reject", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Issue not covered by policy\"}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("REJECTED"));

        assertEquals(OrderIssueState.REJECTED,
                issueRepository.findById(issue.getId()).orElseThrow().getState());
    }

    @Test
    void rejectIssue_returns400WhenReasonMissing() throws Exception {
        User staff = createStaffUser("reject-400-staff@example.com");
        User customer = createActiveUser("reject-400-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        OrderIssue issue = createIssueDirect(order, customer, OrderIssueState.INVESTIGATING);

        mockMvc.perform(post("/support/issues/{id}/reject", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectIssue_returns400WhenAlreadyResolved() throws Exception {
        User staff = createStaffUser("reject-terminal-staff@example.com");
        User customer = createActiveUser("reject-terminal-cust@example.com", "Password1!");
        Order order = createOrder(customer, OrderStatus.DELIVERED);
        OrderIssue issue = createIssueDirect(order, customer, OrderIssueState.REJECTED);

        mockMvc.perform(post("/support/issues/{id}/reject", issue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Already rejected\"}")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectIssue_returns403ForRegularUser() throws Exception {
        User user = createActiveUser("reject-403@example.com", "Password1!");

        mockMvc.perform(post("/support/issues/{id}/reject", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Denied\"}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectIssue_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/support/issues/{id}/reject", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Denied\"}"))
                .andExpect(status().isUnauthorized());
    }
}
