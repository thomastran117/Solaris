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
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers TrackingWebhookController (POST /webhooks/aftership) — no @RequireAuth,
 * authenticated instead via HMAC-SHA256 signature over the raw request body.
 *
 * app.aftership.webhook-secret is fixed to "test-aftership-webhook-secret" in
 * application-integration-test.properties so signatures can be computed here.
 *
 * The controller swallows all internal-processing exceptions and always returns
 * 200, except for signature-verification failures (400). "Delivered" tags also
 * trigger autoMarkDeliveredByTracking; other tags only record a tracking
 * checkpoint via publishTrackingCheckpoint.
 */
class TrackingWebhookIT extends AbstractIntegrationIT {

    private static final String WEBHOOK_SECRET = "test-aftership-webhook-secret";

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderStatusHistoryRepository historyRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Order createOrder(User user, String trackingNumber, OrderStatus status) {
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(BigDecimal.TEN);
        order.setStatus(status);
        order.setTrackingNumber(trackingNumber);
        return orderRepository.save(order);
    }

    private byte[] trackingPayload(String trackingNumber, String tag) throws Exception {
        Map<String, Object> tracking = new LinkedHashMap<>();
        if (trackingNumber != null) tracking.put("tracking_number", trackingNumber);
        if (tag != null) tracking.put("tag", tag);
        tracking.put("checkpoints", List.of(Map.of("checkpoint_time", "2026-06-01T10:00:00Z")));

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("tracking", tracking);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("msg", msg);

        return objectMapper.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
    }

    private String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    // ── POST /webhooks/aftership ────────────────────────────────────────────────

    @Test
    void webhook_validSignature_unknownTrackingNumber_returns200() throws Exception {
        byte[] body = trackingPayload("UNKNOWN-TRACKING-NUMBER", "InTransit");

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("aftership-hmac-sha256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_invalidSignature_returns400() throws Exception {
        byte[] body = trackingPayload("SOME-TRACKING-NUMBER", "InTransit");

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("aftership-hmac-sha256", "0000000000000000000000000000000000000000000000000000000000000000")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_missingSignatureHeader_returns400() throws Exception {
        byte[] body = trackingPayload("SOME-TRACKING-NUMBER", "InTransit");

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_malformedJson_returns200() throws Exception {
        byte[] body = "not-valid-json{{{".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("aftership-hmac-sha256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_missingTrackingNumber_returns200() throws Exception {
        byte[] body = trackingPayload(null, "InTransit");

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("aftership-hmac-sha256", sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_nonDeliveredTag_recordsTrackingCheckpointHistory() throws Exception {
        User user = createActiveUser("track-checkpoint@example.com", "Password1!");
        Order order = createOrder(user, "TRACK-CHECKPOINT-1", OrderStatus.SHIPPED);

        byte[] body = trackingPayload("TRACK-CHECKPOINT-1", "InTransit");

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("aftership-hmac-sha256", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        List<OrderStatusHistory> history = historyRepository.findAllByOrderIdOrderByOccurredAtAsc(order.getId());
        assertThatHistoryContainsCheckpoint(history, "InTransit");

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.SHIPPED, reloaded.getStatus());
    }

    @Test
    void webhook_deliveredTag_marksOrderDelivered() throws Exception {
        User user = createActiveUser("track-delivered@example.com", "Password1!");
        Order order = createOrder(user, "TRACK-DELIVERED-1", OrderStatus.SHIPPED);

        byte[] body = trackingPayload("TRACK-DELIVERED-1", "Delivered");

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("aftership-hmac-sha256", sign(body))
                        .content(body))
                .andExpect(status().isOk());

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.DELIVERED, reloaded.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(reloaded.getDeliveredAt());
    }

    private void assertThatHistoryContainsCheckpoint(List<OrderStatusHistory> history, String note) {
        boolean found = history.stream().anyMatch(h ->
                h.getEventType() == OrderHistoryEventType.TRACKING_CHECKPOINT && note.equals(h.getNote()));
        org.junit.jupiter.api.Assertions.assertTrue(found,
                "Expected a TRACKING_CHECKPOINT history entry with note '" + note + "'");
    }
}
