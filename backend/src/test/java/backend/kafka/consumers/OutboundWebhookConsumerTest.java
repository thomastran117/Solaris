package backend.kafka.consumers;

import backend.configurations.application.WebhookSecretEncryptor;
import backend.configurations.application.WebhookUrlValidator;
import backend.exceptions.http.BadRequestException;
import backend.events.webhook.OutboundWebhookEvent;
import backend.models.core.Company;
import backend.models.core.CompanyWebhookSubscription;
import backend.models.core.WebhookDeliveryLog;
import backend.models.enums.WebhookDeliveryStatus;
import backend.models.enums.WebhookEventType;
import backend.repositories.CompanyWebhookSubscriptionRepository;
import backend.repositories.WebhookDeliveryLogRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboundWebhookConsumerTest {

    private CompanyWebhookSubscriptionRepository subscriptionRepository;
    private WebhookDeliveryLogRepository deliveryLogRepository;
    private RestTemplate webhookRestTemplate;
    private WebhookSecretEncryptor secretEncryptor;
    private WebhookUrlValidator urlValidator;
    private OutboundWebhookConsumer consumer;

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID ORDER_ID   = TestIds.uuid(2);
    private static final UUID SUB_ID     = TestIds.uuid(3);
    private static final String SECRET   = "a".repeat(64);
    private static final String PAYLOAD  = "{\"eventType\":\"ORDER_CREATED\"}";

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(CompanyWebhookSubscriptionRepository.class);
        deliveryLogRepository  = mock(WebhookDeliveryLogRepository.class);
        webhookRestTemplate    = mock(RestTemplate.class);
        secretEncryptor        = mock(WebhookSecretEncryptor.class);
        urlValidator           = mock(WebhookUrlValidator.class);
        // Pass-through: no encryption key in tests, decrypt returns the stored value unchanged
        when(secretEncryptor.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        // retryBackoffBaseMs = 0 → no real sleeps between retries, keeping the test fast
        consumer = new OutboundWebhookConsumer(
                subscriptionRepository, deliveryLogRepository, webhookRestTemplate, secretEncryptor,
                urlValidator, 0L);

        when(deliveryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Default: event not yet processed — allow delivery to proceed
        when(deliveryLogRepository.existsBySubscriptionIdAndEventId(any(), any())).thenReturn(false);
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void onEvent_deliversSuccessfully_onFirstAttempt() {
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, SECRET);
        when(subscriptionRepository.findActiveByCompanyIdAndEventType(COMPANY_ID, WebhookEventType.ORDER_CREATED))
                .thenReturn(List.of(sub));
        when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        consumer.onEvent(makeEvent(WebhookEventType.ORDER_CREATED));

        // Single save at the end — no initial PENDING save
        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository, times(1)).save(captor.capture());
        WebhookDeliveryLog finalLog = captor.getValue();
        assertEquals(WebhookDeliveryStatus.DELIVERED, finalLog.getStatus());
        assertEquals(1, finalLog.getAttemptCount());
        assertEquals(200, finalLog.getResponseStatus());
        assertNotNull(finalLog.getDeliveredAt());
    }

    @Test
    void onEvent_noOp_whenNoActiveSubscriptions() {
        when(subscriptionRepository.findActiveByCompanyIdAndEventType(any(), any()))
                .thenReturn(List.of());

        consumer.onEvent(makeEvent(WebhookEventType.ORDER_PAID));

        verify(webhookRestTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        verify(deliveryLogRepository, never()).save(any());
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void onEvent_skipsDelivery_whenEventAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, SECRET);
        when(subscriptionRepository.findActiveByCompanyIdAndEventType(COMPANY_ID, WebhookEventType.ORDER_CREATED))
                .thenReturn(List.of(sub));
        // Simulate Kafka re-delivery: log already exists for this subscription+event
        when(deliveryLogRepository.existsBySubscriptionIdAndEventId(SUB_ID, eventId)).thenReturn(true);

        consumer.onEvent(makeEvent(eventId, WebhookEventType.ORDER_CREATED));

        verify(webhookRestTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        verify(deliveryLogRepository, never()).save(any());
    }

    // ── SSRF re-validation at delivery time ─────────────────────────────────────

    @Test
    void onEvent_blocksDelivery_whenUrlFailsSsrfRevalidationAtDeliveryTime() {
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, SECRET);
        when(subscriptionRepository.findActiveByCompanyIdAndEventType(COMPANY_ID, WebhookEventType.ORDER_CREATED))
                .thenReturn(List.of(sub));
        // Simulate DNS rebinding: the URL now resolves to a blocked address at delivery time.
        doThrow(new BadRequestException("Webhook URL must resolve to a publicly accessible address"))
                .when(urlValidator).validate(sub.getUrl());

        consumer.onEvent(makeEvent(WebhookEventType.ORDER_CREATED));

        // No HTTP call is made, and the attempt is recorded as FAILED with zero attempts.
        verify(webhookRestTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository, times(1)).save(captor.capture());
        assertEquals(WebhookDeliveryStatus.FAILED, captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getAttemptCount());
    }

    // ── retry logic ───────────────────────────────────────────────────────────

    @Test
    void onEvent_retriesOnNon2xx_andMarksFailedAfterThreeAttempts() {
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, SECRET);
        when(subscriptionRepository.findActiveByCompanyIdAndEventType(COMPANY_ID, WebhookEventType.STOCK_LOW))
                .thenReturn(List.of(sub));
        // All three attempts return 500
        when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.internalServerError().body("error"));

        consumer.onEvent(makeEvent(WebhookEventType.STOCK_LOW));

        // exchange called 3 times (MAX_ATTEMPTS)
        verify(webhookRestTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));

        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository, times(1)).save(captor.capture());
        WebhookDeliveryLog last = captor.getValue();
        assertEquals(WebhookDeliveryStatus.FAILED, last.getStatus());
        assertEquals(3, last.getAttemptCount());
    }

    @Test
    void onEvent_retriesOnException_andMarksFailedAfterThreeAttempts() {
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, SECRET);
        when(subscriptionRepository.findActiveByCompanyIdAndEventType(COMPANY_ID, WebhookEventType.ORDER_CANCELLED))
                .thenReturn(List.of(sub));
        when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        consumer.onEvent(makeEvent(WebhookEventType.ORDER_CANCELLED));

        verify(webhookRestTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository, times(1)).save(captor.capture());
        assertEquals(WebhookDeliveryStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void onEvent_succeedsOnSecondAttempt_afterInitialFailure() {
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, SECRET);
        when(subscriptionRepository.findActiveByCompanyIdAndEventType(COMPANY_ID, WebhookEventType.ORDER_DELIVERED))
                .thenReturn(List.of(sub));
        when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.internalServerError().body("err"))
                .thenReturn(ResponseEntity.ok("OK"));

        consumer.onEvent(makeEvent(WebhookEventType.ORDER_DELIVERED));

        verify(webhookRestTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository, times(1)).save(captor.capture());
        assertEquals(WebhookDeliveryStatus.DELIVERED, captor.getValue().getStatus());
        assertEquals(2, captor.getValue().getAttemptCount());
    }

    // ── HMAC signature ────────────────────────────────────────────────────────

    @Test
    void onEvent_signsPayloadCorrectly_inXShopWaveSignatureHeader() throws Exception {
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, SECRET);
        when(subscriptionRepository.findActiveByCompanyIdAndEventType(COMPANY_ID, WebhookEventType.ORDER_CREATED))
                .thenReturn(List.of(sub));
        when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        consumer.onEvent(makeEvent(WebhookEventType.ORDER_CREATED));

        String expectedHmac = computeExpectedHmac(SECRET, PAYLOAD);
        verify(webhookRestTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                argThat(entity -> {
                    String sig = entity.getHeaders().getFirst("X-ShopWave-Signature");
                    return ("sha256=" + expectedHmac).equals(sig);
                }),
                eq(String.class));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private OutboundWebhookEvent makeEvent(WebhookEventType type) {
        return makeEvent(UUID.randomUUID(), type);
    }

    private OutboundWebhookEvent makeEvent(UUID eventId, WebhookEventType type) {
        return new OutboundWebhookEvent(eventId, type, COMPANY_ID, ORDER_ID, null, PAYLOAD, Instant.now());
    }

    private CompanyWebhookSubscription makeSub(UUID id, UUID companyId, String secret) {
        Company company = new Company();
        company.setId(companyId);

        CompanyWebhookSubscription sub = new CompanyWebhookSubscription();
        sub.setId(id);
        sub.setCompany(company);
        sub.setUrl("https://example.com/webhooks");
        sub.setSecretToken(secret);
        sub.setVerificationChallenge("challenge-token");
        return sub;
    }

    private String computeExpectedHmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
