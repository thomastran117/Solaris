package backend.integration.orders;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Order;
import backend.models.core.OrderStatusHistory;
import backend.models.core.User;
import backend.models.enums.OrderHistoryEventType;
import backend.models.enums.OrderStatus;
import backend.repositories.OrderRepository;
import backend.repositories.OrderStatusHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderStatusHistoryIT extends AbstractIntegrationIT {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderStatusHistoryRepository historyRepository;

    @AfterEach
    void cleanOrders() {
        try { jdbcTemplate.execute("DELETE FROM order_status_history"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM order_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM orders"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Order createOrder(User user) {
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(BigDecimal.TEN);
        order.setStatus(OrderStatus.PAID);
        return orderRepository.save(order);
    }

    private OrderStatusHistory createHistoryEntry(UUID orderId, OrderStatus status, Instant at, String note) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(orderId);
        h.setEventType(OrderHistoryEventType.STATUS_CHANGED);
        h.setStatus(status);
        h.setOccurredAt(at);
        h.setNote(note);
        return historyRepository.save(h);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void getOrderHistory_returnsEntriesOrderedByOccurredAtAsc() throws Exception {
        User user = createActiveUser("hist-order@example.com", "Password1!");
        Order order = createOrder(user);

        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        createHistoryEntry(order.getId(), OrderStatus.RESERVED, base.minusSeconds(30), "Reserved");
        createHistoryEntry(order.getId(), OrderStatus.PAID, base.minusSeconds(20), "Payment confirmed");
        createHistoryEntry(order.getId(), OrderStatus.PACKED, base.minusSeconds(10), null);

        mockMvc.perform(get("/orders/{id}/history", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("RESERVED"))
                .andExpect(jsonPath("$[1].status").value("PAID"))
                .andExpect(jsonPath("$[2].status").value("PACKED"))
                .andExpect(jsonPath("$[1].note").value("Payment confirmed"))
                .andExpect(jsonPath("$[2].note").doesNotExist());
    }

    @Test
    void getOrderHistory_returns403ForNonOwner() throws Exception {
        User owner = createActiveUser("hist-owner@example.com", "Password1!");
        User other = createActiveUser("hist-other@example.com", "Password1!");
        Order order = createOrder(owner);
        createHistoryEntry(order.getId(), OrderStatus.PAID, Instant.now(), null);

        mockMvc.perform(get("/orders/{id}/history", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderHistory_returns404ForUnknownOrder() throws Exception {
        User user = createActiveUser("hist-unknown@example.com", "Password1!");

        mockMvc.perform(get("/orders/{id}/history", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderHistory_returnsEmptyArrayForOrderWithNoHistory() throws Exception {
        User user = createActiveUser("hist-empty@example.com", "Password1!");
        Order order = createOrder(user);

        mockMvc.perform(get("/orders/{id}/history", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
