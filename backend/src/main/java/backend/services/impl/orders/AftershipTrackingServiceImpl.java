package backend.services.impl.orders;

import backend.dtos.responses.order.TrackingEvent;
import backend.services.intf.orders.TrackingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AftershipTrackingServiceImpl implements TrackingService {

    private static final Logger log = LoggerFactory.getLogger(AftershipTrackingServiceImpl.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;

    public AftershipTrackingServiceImpl(
            RestTemplate aftershipRestTemplate,
            ObjectMapper objectMapper,
            @Value("${app.aftership.api-key:}") String apiKey,
            @Value("${app.aftership.base-url:https://api.aftership.com/v4}") String baseUrl) {
        this.restTemplate = aftershipRestTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public void registerTracking(UUID orderId, String trackingNumber, String carrier) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Aftership API key not configured — skipping tracking registration for order {}", orderId);
            return;
        }
        try {
            ObjectNode tracking = objectMapper.createObjectNode();
            tracking.put("tracking_number", trackingNumber);
            tracking.put("slug", toAftershipSlug(carrier));

            ObjectNode body = objectMapper.createObjectNode();
            body.set("tracking", tracking);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders());
            restTemplate.postForObject(baseUrl + "/trackings", request, String.class);
        } catch (Exception e) {
            log.warn("Aftership registration failed for order {} tracking {}: {}", orderId, trackingNumber, e.getMessage());
        }
    }

    @Override
    public List<TrackingEvent> getTrackingEvents(String trackingNumber, String carrier) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        try {
            String slug = toAftershipSlug(carrier);
            String url = baseUrl + "/trackings/" + slug + "/" + trackingNumber;
            String raw = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class).getBody();
            if (raw == null) return List.of();

            JsonNode root = objectMapper.readTree(raw);
            JsonNode checkpoints = root.path("data").path("tracking").path("checkpoints");
            if (!checkpoints.isArray()) return List.of();

            List<TrackingEvent> events = new ArrayList<>();
            for (JsonNode cp : checkpoints) {
                String status = cp.path("tag").asText(null);
                String message = cp.path("message").asText(null);
                String location = cp.path("city").asText(null);
                String occurredAtRaw = cp.path("checkpoint_time").asText(null);
                Instant occurredAt = occurredAtRaw != null ? Instant.parse(occurredAtRaw) : null;
                events.add(new TrackingEvent(status, message, location, occurredAt));
            }
            return events;
        } catch (Exception e) {
            log.warn("Aftership getTrackingEvents failed for {}: {}", trackingNumber, e.getMessage());
            return List.of();
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("aftership-api-key", apiKey);
        return headers;
    }

    private String toAftershipSlug(String carrier) {
        if (carrier == null) return "other";
        return switch (carrier.toUpperCase()) {
            case "UPS"    -> "ups";
            case "FEDEX"  -> "fedex";
            case "USPS"   -> "usps";
            case "DHL"    -> "dhl";
            case "CANADA_POST" -> "canada-post";
            case "AUSTRALIA_POST" -> "australia-post";
            default -> carrier.toLowerCase().replace("_", "-");
        };
    }
}
