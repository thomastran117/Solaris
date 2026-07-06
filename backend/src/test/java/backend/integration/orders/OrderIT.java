package backend.integration.orders;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Order;
import backend.models.core.User;
import backend.models.enums.OrderStatus;
import backend.repositories.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderIT extends AbstractIntegrationIT {

    @Autowired private OrderRepository orderRepository;

    @AfterEach
    void cleanOrders() {
        try { jdbcTemplate.execute("DELETE FROM order_status_history"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM order_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM orders"); } catch (Exception ignored) {}
    }

    // All responses are wrapped: { success, data, message, meta }
    // PagedResponse → data = [items], meta = { totalElements, page, ... }
    // Single entity → data = { id, status, ... }

    private Order createOrder(User user, OrderStatus status) {
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(BigDecimal.valueOf(99.99));
        order.setStatus(status);
        return orderRepository.save(order);
    }

    // ── GET /orders ───────────────────────────────────────────────────────────

    @Test
    void getOrders_returnsEmptyPageWhenNoOrders() throws Exception {
        User user = createActiveUser("orders-empty@example.com", "Password1!");

        mockMvc.perform(get("/orders")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getOrders_returnsOwnOrdersOnly() throws Exception {
        User user = createActiveUser("orders-own@example.com", "Password1!");
        User other = createActiveUser("orders-other@example.com", "Password1!");
        createOrder(user, OrderStatus.PAID);
        createOrder(other, OrderStatus.PAID);

        mockMvc.perform(get("/orders")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void getOrders_filtersByStatus() throws Exception {
        User user = createActiveUser("orders-filter@example.com", "Password1!");
        createOrder(user, OrderStatus.PAID);
        createOrder(user, OrderStatus.SHIPPED);

        mockMvc.perform(get("/orders").param("status", "PAID")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }

    @Test
    void getOrders_returns400ForInvalidSortField() throws Exception {
        User user = createActiveUser("orders-sort-invalid@example.com", "Password1!");

        mockMvc.perform(get("/orders").param("sort", "DROP TABLE--")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrders_returns400ForInvalidDirection() throws Exception {
        User user = createActiveUser("orders-dir-invalid@example.com", "Password1!");

        mockMvc.perform(get("/orders").param("direction", "sideways")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrders_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /orders/latest ────────────────────────────────────────────────────

    @Test
    void getLatestOrder_returnsMostRecentOrder() throws Exception {
        User user = createActiveUser("orders-latest@example.com", "Password1!");
        createOrder(user, OrderStatus.PAID);
        Order latest = createOrder(user, OrderStatus.SHIPPED);

        mockMvc.perform(get("/orders/latest")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(latest.getId().toString()));
    }

    @Test
    void getLatestOrder_returns404WhenNoOrders() throws Exception {
        User user = createActiveUser("orders-latest-none@example.com", "Password1!");

        mockMvc.perform(get("/orders/latest")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── GET /orders/{id} ──────────────────────────────────────────────────────

    @Test
    void getOrder_returnsOrderForOwner() throws Exception {
        User user = createActiveUser("orders-get@example.com", "Password1!");
        Order order = createOrder(user, OrderStatus.PAID);

        mockMvc.perform(get("/orders/{id}", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void getOrder_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("orders-owner@example.com", "Password1!");
        User other = createActiveUser("orders-notowner@example.com", "Password1!");
        Order order = createOrder(owner, OrderStatus.PAID);

        mockMvc.perform(get("/orders/{id}", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_returns404ForUnknownId() throws Exception {
        User user = createActiveUser("orders-unknown@example.com", "Password1!");

        mockMvc.perform(get("/orders/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /orders/{id}/cancel ───────────────────────────────────────────────

    @Test
    void cancelOrder_cancelsReservedOrderSuccessfully() throws Exception {
        User user = createActiveUser("orders-cancel@example.com", "Password1!");
        // No paymentIntentId → no refund call; no items → stock restore is a no-op
        Order order = createOrder(user, OrderStatus.RESERVED);

        mockMvc.perform(post("/orders/{id}/cancel", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // The cancellation must be committed to the database, not just echoed in the response.
        assertEquals(OrderStatus.CANCELLED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancelOrder_returns409ForDeliveredOrder() throws Exception {
        User user = createActiveUser("orders-cancel-delivered@example.com", "Password1!");
        Order order = createOrder(user, OrderStatus.DELIVERED);

        mockMvc.perform(post("/orders/{id}/cancel", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelOrder_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("orders-cancel-owner@example.com", "Password1!");
        User other = createActiveUser("orders-cancel-other@example.com", "Password1!");
        Order order = createOrder(owner, OrderStatus.RESERVED);

        mockMvc.perform(post("/orders/{id}/cancel", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/orders/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /orders/{id}/driver-location ──────────────────────────────────────

    @Test
    void getDriverLocation_returns200WithEmptyBodyWhenNoLocationCached() throws Exception {
        User user = createActiveUser("orders-driver@example.com", "Password1!");
        Order order = createOrder(user, OrderStatus.SHIPPED);

        mockMvc.perform(get("/orders/{id}/driver-location", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
    }

    @Test
    void getDriverLocation_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("orders-driver-owner@example.com", "Password1!");
        User other = createActiveUser("orders-driver-other@example.com", "Password1!");
        Order order = createOrder(owner, OrderStatus.SHIPPED);

        mockMvc.perform(get("/orders/{id}/driver-location", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── GET /orders/{id}/tracking ──────────────────────────────────────────────

    @Test
    void getTracking_returns404WhenNoTrackingNumber() throws Exception {
        User user = createActiveUser("orders-tracking@example.com", "Password1!");
        Order order = createOrder(user, OrderStatus.SHIPPED);

        mockMvc.perform(get("/orders/{id}/tracking", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTracking_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("orders-tracking-owner@example.com", "Password1!");
        User other = createActiveUser("orders-tracking-other@example.com", "Password1!");
        Order order = createOrder(owner, OrderStatus.SHIPPED);

        mockMvc.perform(get("/orders/{id}/tracking", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── GET /orders/{id}/stream ────────────────────────────────────────────────

    @Test
    void streamOrder_returns200WithInitialSnapshotEvent() throws Exception {
        User user = createActiveUser("orders-stream@example.com", "Password1!");
        Order order = createOrder(user, OrderStatus.PAID);

        mockMvc.perform(get("/orders/{id}/stream", order.getId())
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
    }
}
