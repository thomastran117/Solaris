package backend.kafka.consumers;

import backend.configurations.application.WebhookSecretEncryptor;
import backend.configurations.application.WebhookUrlValidator;
import backend.events.webhook.OutboundWebhookEvent;
import backend.exceptions.http.BadRequestException;
import backend.models.core.CompanyWebhookSubscription;
import backend.models.core.WebhookDeliveryLog;
import backend.models.enums.WebhookDeliveryStatus;
import backend.repositories.CompanyWebhookSubscriptionRepository;
import backend.repositories.WebhookDeliveryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final WebhookUrlValidator urlValidator;
    /** Base delay between inner retry attempts; doubled each attempt (e.g. 1s, 2s). 0 disables sleeping. */
    private final long retryBackoffBaseMs;

    public OutboundWebhookConsumer(
            CompanyWebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryLogRepository deliveryLogRepository,
            @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate,
            WebhookSecretEncryptor secretEncryptor,
            WebhookUrlValidator urlValidator,
            @Value("${app.webhook.retry-backoff-base-ms:1000}") long retryBackoffBaseMs) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.webhookRestTemplate = webhookRestTemplate;
        this.secretEncryptor = secretEncryptor;
        this.urlValidator = urlValidator;
        this.retryBackoffBaseMs = retryBackoffBaseMs;
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

        // Re-validate the URL at delivery time, not just at registration. The host is resolved again
        // here so a DNS-rebinding attack — registering a public IP, then repointing the domain to an
        // internal address before delivery — is blocked. A blocked URL is recorded as FAILED (no HTTP).
        try {
            urlValidator.validate(sub.getUrl());
        } catch (BadRequestException ex) {
            log.warn("Blocking webhook delivery for sub={}: URL failed SSRF re-validation at delivery time: {}",
                    sub.getId(), ex.getMessage());
            saveDeliveryLog(sub, event, 0, WebhookDeliveryStatus.FAILED, null, null);
            return;
        }

        String signature = computeSignature(secretEncryptor.decrypt(sub.getSecretToken()), event.payloadJson());

        Integer lastResponseStatus = null;
        int finalAttemptCount = 0;
        WebhookDeliveryStatus finalStatus = WebhookDeliveryStatus.FAILED;
        Instant deliveredAt = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            finalAttemptCount = attempt;
            // Exponential backoff between attempts (not before the first) so a failing endpoint isn't
            // hammered. Returns early if the thread is interrupted during the wait.
            if (attempt > 1 && !backoffBeforeRetry(attempt)) {
                break;
            }
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
        saveDeliveryLog(sub, event, finalAttemptCount, finalStatus, lastResponseStatus, deliveredAt);

        if (finalStatus == WebhookDeliveryStatus.FAILED) {
            log.warn("Webhook delivery permanently failed for sub={} eventType={} after {} attempts",
                    sub.getId(), event.eventType(), MAX_ATTEMPTS);
        }
    }

    private void saveDeliveryLog(CompanyWebhookSubscription sub, OutboundWebhookEvent event,
                                 int attemptCount, WebhookDeliveryStatus status,
                                 Integer responseStatus, Instant deliveredAt) {
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setSubscription(sub);
        deliveryLog.setEventId(event.eventId());
        deliveryLog.setEventType(event.eventType());
        deliveryLog.setPayloadJson(event.payloadJson());
        deliveryLog.setAttemptCount(attemptCount);
        deliveryLog.setStatus(status);
        deliveryLog.setResponseStatus(responseStatus);
        deliveryLog.setDeliveredAt(deliveredAt);
        try {
            deliveryLogRepository.save(deliveryLog);
        } catch (DataIntegrityViolationException e) {
            // The (subscription_id, event_id) unique constraint is the authoritative idempotency
            // guard: under at-least-once redelivery (or a concurrent consumer) another execution
            // already recorded this delivery. Swallow the duplicate so the message isn't sent to the
            // DLQ. NOTE: at-least-once semantics mean the remote endpoint can still be hit more than
            // once in this rare window — subscribers must treat deliveries as idempotent (the
            // X-ShopWave-Signature + eventId let them dedupe).
            log.debug("Delivery log for event {} sub {} already exists; skipping duplicate save",
                    event.eventId(), sub.getId());
        }
    }

    /**
     * Sleeps with exponential backoff before retry {@code attempt} (attempt 2 → base, attempt 3 →
     * 2×base, …). Returns {@code false} if interrupted (caller should stop retrying).
     */
    private boolean backoffBeforeRetry(int attempt) {
        if (retryBackoffBaseMs <= 0) return true;
        long delay = retryBackoffBaseMs << (attempt - 2);
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
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
