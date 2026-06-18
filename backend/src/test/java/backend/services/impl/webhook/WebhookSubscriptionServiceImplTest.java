package backend.services.impl.webhook;

import backend.dtos.requests.RegisterWebhookRequest;
import backend.dtos.responses.WebhookCreationResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyWebhookSubscription;
import backend.models.enums.CompanyCapability;
import backend.models.enums.WebhookEventType;
import backend.models.enums.WebhookSubscriptionStatus;
import backend.repositories.CompanyWebhookSubscriptionRepository;
import backend.repositories.WebhookDeliveryLogRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookSubscriptionServiceImplTest {

    private CompanyWebhookSubscriptionRepository subscriptionRepository;
    private WebhookDeliveryLogRepository deliveryLogRepository;
    private CompanyAccessService companyAccessService;
    private RestTemplate webhookRestTemplate;
    private WebhookSubscriptionServiceImpl service;

    private static final UUID USER_ID       = TestIds.uuid(1);
    private static final UUID COMPANY_ID    = TestIds.uuid(2);
    private static final UUID SUB_ID        = TestIds.uuid(3);

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(CompanyWebhookSubscriptionRepository.class);
        deliveryLogRepository  = mock(WebhookDeliveryLogRepository.class);
        companyAccessService   = mock(CompanyAccessService.class);
        webhookRestTemplate    = mock(RestTemplate.class);
        service = new WebhookSubscriptionServiceImpl(
                subscriptionRepository, deliveryLogRepository,
                companyAccessService, webhookRestTemplate);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_happyPath_returnCreationResponseWithSecret() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(company);
        when(subscriptionRepository.countByCompanyId(COMPANY_ID)).thenReturn(0L);
        // Challenge GET fails → stays PENDING
        when(webhookRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            CompanyWebhookSubscription sub = inv.getArgument(0);
            sub.setId(SUB_ID);
            return sub;
        });

        RegisterWebhookRequest req = makeRequest("https://example.com/hook",
                Set.of(WebhookEventType.ORDER_CREATED));
        WebhookCreationResponse result = service.register(COMPANY_ID, USER_ID, req);

        assertNotNull(result.secret());
        assertEquals(64, result.secret().length()); // 32 bytes → 64 hex chars
        assertEquals(SUB_ID, result.subscription().id());
        assertEquals("https://example.com/hook", result.subscription().url());
    }

    @Test
    void register_throwsBadRequest_whenAtFiveSubLimit() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(company);
        when(subscriptionRepository.countByCompanyId(COMPANY_ID)).thenReturn(5L);

        assertThrows(BadRequestException.class,
                () -> service.register(COMPANY_ID, USER_ID,
                        makeRequest("https://example.com/hook", Set.of(WebhookEventType.ORDER_PAID))));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void register_marksActiveImmediately_whenChallengeGetPasses() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(company);
        when(subscriptionRepository.countByCompanyId(COMPANY_ID)).thenReturn(0L);

        // Capture the subscription so we can return the right challenge in the mock
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            CompanyWebhookSubscription sub = inv.getArgument(0);
            sub.setId(SUB_ID);
            // Mock challenge GET to return the correct challenge token
            when(webhookRestTemplate.getForEntity(
                    contains("?challenge="), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(sub.getVerificationChallenge()));
            return sub;
        });

        RegisterWebhookRequest req = makeRequest("https://fast-endpoint.com/wh",
                Set.of(WebhookEventType.ORDER_SHIPPED));
        service.register(COMPANY_ID, USER_ID, req);

        // save called twice: once for initial persist, once when ACTIVE is set
        verify(subscriptionRepository, times(2)).save(any());
    }

    @Test
    void register_leavesStatusPending_whenChallengeGetFails() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(company);
        when(subscriptionRepository.countByCompanyId(COMPANY_ID)).thenReturn(2L);
        when(webhookRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            CompanyWebhookSubscription sub = inv.getArgument(0);
            sub.setId(SUB_ID);
            return sub;
        });

        RegisterWebhookRequest req = makeRequest("https://offline.example.com/hook",
                Set.of(WebhookEventType.STOCK_LOW));
        WebhookCreationResponse result = service.register(COMPANY_ID, USER_ID, req);

        assertEquals(WebhookSubscriptionStatus.PENDING_VERIFICATION, result.subscription().status());
        // only the initial save — no second save to mark ACTIVE
        verify(subscriptionRepository, times(1)).save(any());
    }

    @Test
    void register_throwsForbidden_whenNonOwnerCalls() {
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenThrow(new ForbiddenException("Not authorised"));

        assertThrows(ForbiddenException.class,
                () -> service.register(COMPANY_ID, USER_ID,
                        makeRequest("https://example.com/hook", Set.of(WebhookEventType.ORDER_CREATED))));
        verify(subscriptionRepository, never()).save(any());
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Test
    void verify_marksActive_whenChallengeResponseMatches() {
        companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY);
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, "abc123");
        when(subscriptionRepository.findByIdAndCompanyId(SUB_ID, COMPANY_ID))
                .thenReturn(Optional.of(sub));
        when(webhookRestTemplate.getForEntity(
                contains("challenge=abc123"), eq(String.class)))
                .thenReturn(ResponseEntity.ok("abc123"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.verify(SUB_ID, COMPANY_ID, USER_ID);

        verify(subscriptionRepository).save(argThat(s ->
                s.getStatus() == WebhookSubscriptionStatus.ACTIVE));
    }

    @Test
    void verify_throwsBadRequest_whenChallengeResponseMismatch() {
        companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY);
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, "correctChallenge");
        when(subscriptionRepository.findByIdAndCompanyId(SUB_ID, COMPANY_ID))
                .thenReturn(Optional.of(sub));
        when(webhookRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("wrongResponse"));

        assertThrows(BadRequestException.class,
                () -> service.verify(SUB_ID, COMPANY_ID, USER_ID));
    }

    @Test
    void verify_throwsResourceNotFound_whenSubDoesNotExist() {
        companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY);
        when(subscriptionRepository.findByIdAndCompanyId(SUB_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.verify(SUB_ID, COMPANY_ID, USER_ID));
    }

    // ── deleteSubscription ────────────────────────────────────────────────────

    @Test
    void deleteSubscription_deletesEntity_whenOwnerRequests() {
        companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY);
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, "ch");
        when(subscriptionRepository.findByIdAndCompanyId(SUB_ID, COMPANY_ID))
                .thenReturn(Optional.of(sub));

        service.deleteSubscription(SUB_ID, COMPANY_ID, USER_ID);

        verify(subscriptionRepository).delete(sub);
    }

    @Test
    void deleteSubscription_throwsResourceNotFound_whenSubMissing() {
        companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY);
        when(subscriptionRepository.findByIdAndCompanyId(SUB_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteSubscription(SUB_ID, COMPANY_ID, USER_ID));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Company makeCompany(UUID id) {
        Company c = new Company();
        c.setId(id);
        c.setName("Test Company");
        return c;
    }

    private CompanyWebhookSubscription makeSub(UUID id, UUID companyId, String challenge) {
        Company company = makeCompany(companyId);
        CompanyWebhookSubscription sub = new CompanyWebhookSubscription();
        sub.setId(id);
        sub.setCompany(company);
        sub.setUrl("https://example.com/hook");
        sub.setSecretToken("a".repeat(64));
        sub.setVerificationChallenge(challenge);
        sub.setEvents(Set.of(WebhookEventType.ORDER_CREATED));
        sub.setStatus(WebhookSubscriptionStatus.PENDING_VERIFICATION);
        return sub;
    }

    private RegisterWebhookRequest makeRequest(String url, Set<WebhookEventType> events) {
        RegisterWebhookRequest req = new RegisterWebhookRequest();
        req.setUrl(url);
        req.setEvents(events);
        return req;
    }
}
