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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderSseIT extends AbstractIntegrationIT {

    @Autowired private OrderRepository orderRepository;

    @AfterEach
    void cleanOrders() {
        try { jdbcTemplate.execute("DELETE FROM order_status_history"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM order_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM orders"); } catch (Exception ignored) {}
    }

    private Order createOrder(User user) {
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(BigDecimal.TEN);
        order.setStatus(OrderStatus.PAID);
        return orderRepository.save(order);
    }

    // ── /stream tests ─────────────────────────────────────────────────────────

    @Test
    void streamOrderStatus_returns200WithEventStreamContentType() throws Exception {
        User user = createActiveUser("sse-ct@example.com", "Password1!");
        Order order = createOrder(user);

        var mvcResult = mockMvc.perform(get("/orders/{id}/stream", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void streamOrderStatus_sendsSnapshotEventImmediately() throws Exception {
        User user = createActiveUser("sse-snap@example.com", "Password1!");
        Order order = createOrder(user);

        var mvcResult = mockMvc.perform(get("/orders/{id}/stream", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:snapshot")));
    }

    @Test
    void streamOrderStatus_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("sse-owner@example.com", "Password1!");
        User other = createActiveUser("sse-other@example.com", "Password1!");
        Order order = createOrder(owner);

        mockMvc.perform(get("/orders/{id}/stream", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamOrderStatus_returns404ForUnknownOrder() throws Exception {
        User user = createActiveUser("sse-missing@example.com", "Password1!");

        mockMvc.perform(get("/orders/{id}/stream", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }

    // ── /driver-location tests ────────────────────────────────────────────────

    @Test
    void getDriverLocation_returnsEmptyWhenNoDriverAssigned() throws Exception {
        User user = createActiveUser("sse-dloc-empty@example.com", "Password1!");
        Order order = createOrder(user);

        mockMvc.perform(get("/orders/{id}/driver-location", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
    }

    @Test
    void getDriverLocation_returnsLocationWhenPresent() throws Exception {
        User user = createActiveUser("sse-dloc-present@example.com", "Password1!");
        Order order = createOrder(user);

        String locationJson = "{\"driverId\":\"" + UUID.randomUUID() + "\",\"lat\":1.23,\"lng\":4.56,\"timestamp\":\"2026-01-01T00:00:00Z\"}";
        cacheService.set("delivery:location:" + order.getId(), locationJson, 600);

        mockMvc.perform(get("/orders/{id}/driver-location", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(1.23))
                .andExpect(jsonPath("$.lng").value(4.56));
    }
}
