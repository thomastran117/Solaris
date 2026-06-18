package backend.kafka.consumers;

import backend.events.webhook.OutboundWebhookEvent;
import backend.models.core.CompanyWebhookSubscription;
import backend.models.core.WebhookDeliveryLog;
import backend.models.enums.WebhookDeliveryStatus;
import backend.repositories.CompanyWebhookSubscriptionRepository;
import backend.repositories.WebhookDeliveryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Component
public class OutboundWebhookConsumer {

    private static final Logger log = LoggerFactory.getLogger(OutboundWebhookConsumer.class);
    private static final int MAX_ATTEMPTS = 3;

    private final CompanyWebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final RestTemplate webhookRestTemplate;

    public OutboundWebhookConsumer(
            CompanyWebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryLogRepository deliveryLogRepository,
            @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.webhookRestTemplate = webhookRestTemplate;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.outbound-webhook-events}",
            groupId = "shopwave-outbound-webhook-consumer",
            containerFactory = "outboundWebhookKafkaListenerContainerFactory")
    public void onEvent(OutboundWebhookEvent event) {
        List<CompanyWebhookSubscription> subscriptions =
                subscriptionRepository.findActiveByCompanyIdAndEventType(event.companyId(), event.eventType());

        for (CompanyWebhookSubscription sub : subscriptions) {
            deliverToSubscription(event, sub);
        }
    }

    private void deliverToSubscription(OutboundWebhookEvent event, CompanyWebhookSubscription sub) {
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setSubscription(sub);
        deliveryLog.setEventType(event.eventType());
        deliveryLog.setPayloadJson(event.payloadJson());
        deliveryLog.setAttemptCount(0);
        deliveryLog.setStatus(WebhookDeliveryStatus.PENDING);
        deliveryLog = deliveryLogRepository.save(deliveryLog);

        String signature = computeSignature(sub.getSecretToken(), event.payloadJson());

        Integer lastResponseStatus = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Content-Type", "application/json");
                headers.set("X-ShopWave-Signature", "sha256=" + signature);
                headers.set("X-ShopWave-Event", event.eventType().name());

                HttpEntity<String> requestEntity = new HttpEntity<>(event.payloadJson(), headers);
                ResponseEntity<String> response = webhookRestTemplate.exchange(
                        sub.getUrl(), HttpMethod.POST, requestEntity, String.class);

                lastResponseStatus = response.getStatusCode().value();

                if (response.getStatusCode().is2xxSuccessful()) {
                    deliveryLog.setStatus(WebhookDeliveryStatus.DELIVERED);
                    deliveryLog.setResponseStatus(lastResponseStatus);
                    deliveryLog.setAttemptCount(attempt);
                    deliveryLog.setDeliveredAt(Instant.now());
                    deliveryLogRepository.save(deliveryLog);
                    return;
                }

                log.warn("Webhook delivery attempt {}/{} for sub={} returned status {}",
                        attempt, MAX_ATTEMPTS, sub.getId(), lastResponseStatus);

            } catch (Exception e) {
                log.warn("Webhook delivery attempt {}/{} for sub={} threw exception: {}",
                        attempt, MAX_ATTEMPTS, sub.getId(), e.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(1000L * (1L << (attempt - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All attempts exhausted — mark failed
        deliveryLog.setStatus(WebhookDeliveryStatus.FAILED);
        deliveryLog.setAttemptCount(MAX_ATTEMPTS);
        deliveryLog.setResponseStatus(lastResponseStatus);
        deliveryLogRepository.save(deliveryLog);

        log.warn("Webhook delivery permanently failed for sub={} eventType={} after {} attempts",
                sub.getId(), event.eventType(), MAX_ATTEMPTS);
    }

    private String computeSignature(String secretToken, String payloadJson) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}
