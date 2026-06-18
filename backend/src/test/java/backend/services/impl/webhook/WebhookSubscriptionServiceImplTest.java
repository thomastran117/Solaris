package backend.services.impl.webhook;

import backend.configurations.application.WebhookSecretEncryptor;
import backend.configurations.application.WebhookUrlValidator;
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

import java.util.List;
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
    private WebhookSecretEncryptor secretEncryptor;
    private WebhookUrlValidator urlValidator;
    private WebhookSubscriptionServiceImpl service;

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID SUB_ID     = TestIds.uuid(3);

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(CompanyWebhookSubscriptionRepository.class);
        deliveryLogRepository  = mock(WebhookDeliveryLogRepository.class);
        companyAccessService   = mock(CompanyAccessService.class);
        webhookRestTemplate    = mock(RestTemplate.class);
        secretEncryptor        = mock(WebhookSecretEncryptor.class);
        urlValidator           = mock(WebhookUrlValidator.class);

        // Pass-through encryption in tests
        when(secretEncryptor.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(secretEncryptor.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        // SSRF check is a no-op in unit tests (would need real DNS)
        doNothing().when(urlValidator).validate(anyString());

        service = new WebhookSubscriptionServiceImpl(
                subscriptionRepository, deliveryLogRepository,
                companyAccessService, webhookRestTemplate,
                secretEncryptor, urlValidator);
        // Wire self-proxy to the real instance (no Spring context in unit tests)
        service.setSelf(service);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_happyPath_returnCreationResponseWithSecret() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(company);
        when(subscriptionRepository.findAllByCompanyIdWithLock(COMPANY_ID)).thenReturn(List.of());
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
        when(subscriptionRepository.findAllByCompanyIdWithLock(COMPANY_ID))
                .thenReturn(List.of(makeSub(TestIds.uuid(10), COMPANY_ID, "c1"),
                        makeSub(TestIds.uuid(11), COMPANY_ID, "c2"),
                        makeSub(TestIds.uuid(12), COMPANY_ID, "c3"),
                        makeSub(TestIds.uuid(13), COMPANY_ID, "c4"),
                        makeSub(TestIds.uuid(14), COMPANY_ID, "c5")));

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
        when(subscriptionRepository.findAllByCompanyIdWithLock(COMPANY_ID)).thenReturn(List.of());

        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            CompanyWebhookSubscription sub = inv.getArgument(0);
            sub.setId(SUB_ID);
            // Challenge GET returns the correct challenge token
            when(webhookRestTemplate.getForEntity(
                    contains("?challenge="), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(sub.getVerificationChallenge()));
            return sub;
        });

        service.register(COMPANY_ID, USER_ID,
                makeRequest("https://fast-endpoint.com/wh", Set.of(WebhookEventType.ORDER_SHIPPED)));

        // save called twice: initial persist + activate
        verify(subscriptionRepository, times(2)).save(any());
    }

    @Test
    void register_leavesStatusPending_whenChallengeGetFails() {
        Company company = makeCompany(COMPANY_ID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(company);
        when(subscriptionRepository.findAllByCompanyIdWithLock(COMPANY_ID)).thenReturn(List.of());
        when(webhookRestTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            CompanyWebhookSubscription sub = inv.getArgument(0);
            sub.setId(SUB_ID);
            return sub;
        });

        WebhookCreationResponse result = service.register(COMPANY_ID, USER_ID,
                makeRequest("https://offline.example.com/hook", Set.of(WebhookEventType.STOCK_LOW)));

        assertEquals(WebhookSubscriptionStatus.PENDING_VERIFICATION, result.subscription().status());
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

    @Test
    void register_callsUrlValidator_beforeAnyDbWork() {
        doThrow(new BadRequestException("SSRF blocked")).when(urlValidator).validate(anyString());

        assertThrows(BadRequestException.class,
                () -> service.register(COMPANY_ID, USER_ID,
                        makeRequest("https://192.168.1.1/evil", Set.of(WebhookEventType.ORDER_CREATED))));
        verify(subscriptionRepository, never()).findAllByCompanyIdWithLock(any());
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

    // ── disable ───────────────────────────────────────────────────────────────

    @Test
    void disable_setsStatusDisabled_whenOwnerRequests() {
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(makeCompany(COMPANY_ID));
        CompanyWebhookSubscription sub = makeSub(SUB_ID, COMPANY_ID, "ch");
        sub.setStatus(WebhookSubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndCompanyId(SUB_ID, COMPANY_ID))
                .thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.disable(SUB_ID, COMPANY_ID, USER_ID);

        verify(subscriptionRepository).save(argThat(s ->
                s.getStatus() == WebhookSubscriptionStatus.DISABLED));
    }

    @Test
    void disable_throwsResourceNotFound_whenSubMissing() {
        when(companyAccessService.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(makeCompany(COMPANY_ID));
        when(subscriptionRepository.findByIdAndCompanyId(SUB_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.disable(SUB_ID, COMPANY_ID, USER_ID));
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
