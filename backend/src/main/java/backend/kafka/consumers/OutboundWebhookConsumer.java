package backend.kafka.consumers;

import backend.configurations.application.WebhookSecretEncryptor;
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
    private final WebhookSecretEncryptor secretEncryptor;

    public OutboundWebhookConsumer(
            CompanyWebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryLogRepository deliveryLogRepository,
            @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate,
            WebhookSecretEncryptor secretEncryptor) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.webhookRestTemplate = webhookRestTemplate;
        this.secretEncryptor = secretEncryptor;
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
        // Idempotency guard: if this event was already delivered (or permanently failed) to this
        // subscription, skip. This protects against Kafka at-least-once redelivery creating
        // duplicate delivery attempts when the consumer restarts before committing the offset.
        if (deliveryLogRepository.existsBySubscriptionIdAndEventId(sub.getId(), event.eventId())) {
            log.debug("Skipping already-processed event {} for subscription {}", event.eventId(), sub.getId());
            return;
        }

        String signature = computeSignature(secretEncryptor.decrypt(sub.getSecretToken()), event.payloadJson());

        Integer lastResponseStatus = null;
        int finalAttemptCount = 0;
        WebhookDeliveryStatus finalStatus = WebhookDeliveryStatus.FAILED;
        Instant deliveredAt = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            finalAttemptCount = attempt;
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
                    finalStatus = WebhookDeliveryStatus.DELIVERED;
                    deliveredAt = Instant.now();
                    break;
                }

                log.warn("Webhook delivery attempt {}/{} for sub={} returned status {}",
                        attempt, MAX_ATTEMPTS, sub.getId(), lastResponseStatus);

            } catch (Exception e) {
                log.warn("Webhook delivery attempt {}/{} for sub={} threw exception: {}",
                        attempt, MAX_ATTEMPTS, sub.getId(), e.getMessage());
            }
        }

        // Persist the outcome once with the final status.
        // Saving only at the end (not as PENDING first) prevents log rows from being
        // orphaned in PENDING if the process is killed mid-delivery.
        // DB exceptions here propagate to the Kafka container, which routes the message
        // to the DLQ after exhausting container-level retries.
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setSubscription(sub);
        deliveryLog.setEventId(event.eventId());
        deliveryLog.setEventType(event.eventType());
        deliveryLog.setPayloadJson(event.payloadJson());
        deliveryLog.setAttemptCount(finalAttemptCount);
        deliveryLog.setStatus(finalStatus);
        deliveryLog.setResponseStatus(lastResponseStatus);
        deliveryLog.setDeliveredAt(deliveredAt);
        deliveryLogRepository.save(deliveryLog);

        if (finalStatus == WebhookDeliveryStatus.FAILED) {
            log.warn("Webhook delivery permanently failed for sub={} eventType={} after {} attempts",
                    sub.getId(), event.eventType(), MAX_ATTEMPTS);
        }
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
