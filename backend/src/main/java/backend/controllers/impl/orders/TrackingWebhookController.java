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
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (!isValidSignature(rawBody, signature)) {
                throw new BadRequestException("Invalid Aftership webhook signature");
            }
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode tracking = root.path("msg").path("tracking");
            String trackingNumber = tracking.path("tracking_number").asText(null);
            String tag = tracking.path("tag").asText(null);

            if ("Delivered".equals(tag) && trackingNumber != null) {
                autoMarkDelivered(trackingNumber);
            }
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
