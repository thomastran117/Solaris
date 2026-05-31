package backend.controllers.impl.orders;

import backend.exceptions.http.BadRequestException;
import backend.services.intf.orders.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

@RestController
@RequestMapping("/webhooks/aftership")
public class TrackingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TrackingWebhookController.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public TrackingWebhookController(
            OrderService orderService,
            ObjectMapper objectMapper,
            @Value("${app.aftership.webhook-secret:}") String webhookSecret) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "aftership-hmac-sha256", required = false) String signature) {
        // Signature verification is mandatory — reject all requests if the secret is
        // not configured rather than silently accepting unauthenticated webhooks.
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("[Aftership] AFTERSHIP_WEBHOOK_SECRET is not configured — rejecting webhook to prevent unauthenticated abuse");
            throw new BadRequestException("Webhook signature verification not configured");
        }
        if (!isValidSignature(rawBody, signature)) {
            throw new BadRequestException("Invalid Aftership webhook signature");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode tracking = root.path("msg").path("tracking");
            String trackingNumber = tracking.path("tracking_number").asText(null);
            String tag = tracking.path("tag").asText(null);

            if (trackingNumber == null || tag == null) {
                return ResponseEntity.ok().build();
            }

            Instant checkpointTime = extractCheckpointTime(tracking);

            if ("Delivered".equals(tag)) {
                autoMarkDelivered(trackingNumber);
            }
            publishCheckpoint(trackingNumber, tag, checkpointTime);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Aftership webhook processing error: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    private void autoMarkDelivered(String trackingNumber) {
        try {
            orderService.autoMarkDeliveredByTracking(trackingNumber);
        } catch (Exception e) {
            log.warn("Auto-deliver by tracking {} failed: {}", trackingNumber, e.getMessage());
        }
    }

    private void publishCheckpoint(String trackingNumber, String tag, Instant checkpointTime) {
        try {
            orderService.publishTrackingCheckpoint(trackingNumber, tag, checkpointTime);
        } catch (Exception e) {
            log.warn("Tracking checkpoint publish failed for {}/{}: {}", trackingNumber, tag, e.getMessage());
        }
    }

    private Instant extractCheckpointTime(JsonNode tracking) {
        JsonNode checkpoints = tracking.path("checkpoints");
        if (checkpoints.isArray() && !checkpoints.isEmpty()) {
            String ts = checkpoints.get(checkpoints.size() - 1).path("checkpoint_time").asText(null);
            if (ts != null) {
                try { return Instant.parse(ts); } catch (Exception ignored) {}
            }
        }
        return Instant.now();
    }

    private boolean isValidSignature(byte[] body, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(body));
            return computed.equals(signature);
        } catch (Exception e) {
            log.warn("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }
}
