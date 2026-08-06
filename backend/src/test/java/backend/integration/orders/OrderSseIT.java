package backend.integration.orders;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Order;
import backend.models.core.User;
import backend.models.enums.OrderStatus;
import backend.repositories.OrderRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderSseIT extends AbstractIntegrationIT {

    @Autowired private OrderRepository orderRepository;


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

        // Use asyncStarted() without asyncDispatch — SseEmitter never completes so
        // asyncDispatch would block for the full 300 s emitter timeout.
        MvcResult mvcResult = mockMvc.perform(get("/orders/{id}/stream", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String contentType = mvcResult.getResponse().getContentType();
        assertNotNull(contentType, "Response should have a Content-Type header");
        assertTrue(contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE),
                "Expected text/event-stream but got: " + contentType);
    }

    @Test
    void streamOrderStatus_sendsSnapshotEventImmediately() throws Exception {
        User user = createActiveUser("sse-snap@example.com", "Password1!");
        Order order = createOrder(user);

        MvcResult mvcResult = mockMvc.perform(get("/orders/{id}/stream", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        // The controller sends the snapshot synchronously before returning; wait
        // briefly for it to be flushed into the MockHttpServletResponse body.
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String body = mvcResult.getResponse().getContentAsString();
            assertNotNull(body, "Response body should not be null");
            assertTrue(body.contains("event:snapshot"), "Snapshot event not found in: " + body);
        });
    }

    @Test
    void streamOrderStatus_returns404ForNonOwner() throws Exception {
        User owner = createActiveUser("sse-owner@example.com", "Password1!");
        User other = createActiveUser("sse-other@example.com", "Password1!");
        Order order = createOrder(owner);

        // Do NOT set Accept: text/event-stream — the error is a JSON response and the
        // SSE content-type constraint on the endpoint prevents the error handler from
        // writing JSON when only text/event-stream is accepted.
        mockMvc.perform(get("/orders/{id}/stream", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamOrderStatus_returns404ForUnknownOrder() throws Exception {
        User user = createActiveUser("sse-missing@example.com", "Password1!");

        mockMvc.perform(get("/orders/{id}/stream", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
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
        assertNotNull(cacheService.get("delivery:location:" + order.getId()),
                "Cache must be readable immediately after set");

        mockMvc.perform(get("/orders/{id}/driver-location", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lat").value(1.23))
                .andExpect(jsonPath("$.data.lng").value(4.56));
    }
}
