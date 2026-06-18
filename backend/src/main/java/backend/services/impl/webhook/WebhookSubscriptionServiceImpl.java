package backend.services.impl.webhook;

import backend.configurations.application.WebhookSecretEncryptor;
import backend.configurations.application.WebhookUrlValidator;
import backend.dtos.requests.RegisterWebhookRequest;
import backend.dtos.responses.WebhookCreationResponse;
import backend.dtos.responses.WebhookDeliveryLogResponse;
import backend.dtos.responses.WebhookSubscriptionResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyWebhookSubscription;
import backend.models.core.WebhookDeliveryLog;
import backend.models.enums.CompanyCapability;
import backend.models.enums.WebhookSubscriptionStatus;
import backend.repositories.CompanyWebhookSubscriptionRepository;
import backend.repositories.WebhookDeliveryLogRepository;
import backend.services.intf.WebhookSubscriptionService;
import backend.services.intf.company.CompanyAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookSubscriptionServiceImpl implements WebhookSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSubscriptionServiceImpl.class);
    private static final int MAX_SUBSCRIPTIONS = 5;
    // Shared SecureRandom is thread-safe; constructing one per call wastes OS entropy.
    private static final SecureRandom RNG = new SecureRandom();

    private final CompanyWebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final CompanyAccessService companyAccessService;
    private final RestTemplate webhookRestTemplate;
    private final WebhookSecretEncryptor secretEncryptor;
    private final WebhookUrlValidator urlValidator;

    // Self-proxy: lets non-@Transactional outer methods call @Transactional helpers
    // across a real transaction boundary without re-entering the same transaction.
    // @Lazy breaks the self-referential startup cycle.
    private WebhookSubscriptionServiceImpl self;

    public WebhookSubscriptionServiceImpl(
            CompanyWebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryLogRepository deliveryLogRepository,
            CompanyAccessService companyAccessService,
            @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate,
            WebhookSecretEncryptor secretEncryptor,
            WebhookUrlValidator urlValidator) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.companyAccessService = companyAccessService;
        this.webhookRestTemplate = webhookRestTemplate;
        this.secretEncryptor = secretEncryptor;
        this.urlValidator = urlValidator;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@Lazy WebhookSubscriptionServiceImpl self) {
        this.self = self;
    }

    private WebhookSubscriptionServiceImpl self() {
        return self != null ? self : this;
    }

    /**
     * Registers a new webhook subscription.
     *
     * <p>Two-phase design:
     * <ol>
     *   <li>{@link #doRegisterPersist} — short @Transactional: validates cap under a
     *       pessimistic write lock, generates and encrypts the secret, persists the
     *       subscription as PENDING_VERIFICATION, and commits.</li>
     *   <li>{@link #attemptChallengeAndActivate} — non-transactional: issues the
     *       challenge GET <em>after</em> the transaction commits so the DB connection
     *       is never held across an external HTTP call.</li>
     * </ol>
     */
    @Override
    public WebhookCreationResponse register(UUID companyId, UUID userId, RegisterWebhookRequest request) {
        // SSRF guard runs before any DB work — rejects private/loopback IPs and non-HTTPS URLs
        urlValidator.validate(request.getUrl());

        Object[] saved = self().doRegisterPersist(companyId, userId, request);
        CompanyWebhookSubscription sub = (CompanyWebhookSubscription) saved[0];
        String rawSecret = (String) saved[1];

        // HTTP challenge fires OUTSIDE the transaction — the DB connection has already been released
        attemptChallengeAndActivate(sub);

        return new WebhookCreationResponse(toResponse(sub), rawSecret);
    }

    @Transactional
    public Object[] doRegisterPersist(UUID companyId, UUID userId, RegisterWebhookRequest request) {
        Company company = companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);

        // Pessimistic write lock serialises concurrent registrations, preventing two simultaneous
        // requests from both seeing count < 5 and both inserting a 6th subscription.
        long existing = subscriptionRepository.findAllByCompanyIdWithLock(companyId).size();
        if (existing >= MAX_SUBSCRIPTIONS) {
            throw new BadRequestException("Maximum of " + MAX_SUBSCRIPTIONS + " webhook endpoints allowed per company");
        }

        byte[] secretBytes = new byte[32];
        RNG.nextBytes(secretBytes);
        String rawSecret = HexFormat.of().formatHex(secretBytes);

        byte[] challengeBytes = new byte[16];
        RNG.nextBytes(challengeBytes);
        String challenge = HexFormat.of().formatHex(challengeBytes);

        CompanyWebhookSubscription sub = new CompanyWebhookSubscription();
        sub.setCompany(company);
        sub.setUrl(request.getUrl());
        sub.setSecretToken(secretEncryptor.encrypt(rawSecret));
        sub.setVerificationChallenge(challenge);
        sub.setEvents(request.getEvents());
        sub.setStatus(WebhookSubscriptionStatus.PENDING_VERIFICATION);

        sub = subscriptionRepository.save(sub);
        return new Object[]{sub, rawSecret};
    }

    private void attemptChallengeAndActivate(CompanyWebhookSubscription sub) {
        try {
            boolean passed = sendChallengeGet(sub.getUrl(), sub.getVerificationChallenge());
            if (passed) {
                self().activateSubscription(sub);
            }
        } catch (Exception e) {
            log.debug("Challenge GET for subscription {} failed during registration: {}", sub.getId(), e.getMessage());
        }
    }

    @Transactional
    public void activateSubscription(CompanyWebhookSubscription sub) {
        sub.setStatus(WebhookSubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);
    }

    /**
     * Manual re-verification of a subscription.
     *
     * <p>HTTP call happens <em>before</em> opening any transaction — no DB connection
     * is held during the network round-trip.  Only on success does a short
     * @Transactional write commit the ACTIVE status.
     */
    @Override
    public void verify(UUID subscriptionId, UUID companyId, UUID userId) {
        companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);

        CompanyWebhookSubscription sub = subscriptionRepository.findByIdAndCompanyId(subscriptionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook subscription not found"));

        // HTTP call is NOT inside a @Transactional — no DB connection held during network I/O
        boolean passed = sendChallengeGet(sub.getUrl(), sub.getVerificationChallenge());
        if (!passed) {
            throw new BadRequestException("Challenge response did not match — ensure your endpoint echoes the challenge query parameter in the response body");
        }

        self().doActivateById(subscriptionId, companyId);
    }

    @Transactional
    public void doActivateById(UUID subscriptionId, UUID companyId) {
        CompanyWebhookSubscription sub = subscriptionRepository.findByIdAndCompanyId(subscriptionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook subscription not found"));
        sub.setStatus(WebhookSubscriptionStatus.ACTIVE);
        subscriptionRepository.save(sub);
    }

    @Override
    @Transactional
    public void disable(UUID subscriptionId, UUID companyId, UUID userId) {
        companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);

        CompanyWebhookSubscription sub = subscriptionRepository.findByIdAndCompanyId(subscriptionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook subscription not found"));

        sub.setStatus(WebhookSubscriptionStatus.DISABLED);
        subscriptionRepository.save(sub);
    }

    @Override
    @Transactional
    public void deleteSubscription(UUID subscriptionId, UUID companyId, UUID userId) {
        companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);

        CompanyWebhookSubscription sub = subscriptionRepository.findByIdAndCompanyId(subscriptionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook subscription not found"));

        subscriptionRepository.delete(sub);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookSubscriptionResponse> listSubscriptions(UUID companyId, UUID userId) {
        companyAccessService.requireAnyAccess(companyId, userId);
        return subscriptionRepository.findAllByCompanyId(companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WebhookDeliveryLogResponse> getDeliveries(UUID subscriptionId, UUID companyId, UUID userId, Pageable pageable) {
        companyAccessService.requireAnyAccess(companyId, userId);
        subscriptionRepository.findByIdAndCompanyId(subscriptionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook subscription not found"));

        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        return deliveryLogRepository.findBySubscriptionIdSince(subscriptionId, since, pageable)
                .map(this::toLogResponse);
    }

    private boolean sendChallengeGet(String url, String challenge) {
        try {
            String challengeUrl = url + (url.contains("?") ? "&" : "?") + "challenge=" + challenge;
            ResponseEntity<String> response = webhookRestTemplate.getForEntity(challengeUrl, String.class);
            return response.getStatusCode().is2xxSuccessful()
                    && challenge.equals(response.getBody() != null ? response.getBody().trim() : null);
        } catch (Exception e) {
            log.debug("Challenge GET to {} failed: {}", url, e.getMessage());
            return false;
        }
    }

    private WebhookSubscriptionResponse toResponse(CompanyWebhookSubscription sub) {
        return new WebhookSubscriptionResponse(
                sub.getId(),
                sub.getUrl(),
                sub.getEvents(),
                sub.getStatus(),
                sub.getCreatedAt(),
                sub.getUpdatedAt()
        );
    }

    private WebhookDeliveryLogResponse toLogResponse(WebhookDeliveryLog log) {
        return new WebhookDeliveryLogResponse(
                log.getId(),
                log.getSubscription().getId(),
                log.getEventType(),
                log.getResponseStatus(),
                log.getAttemptCount(),
                log.getDeliveredAt(),
                log.getStatus(),
                log.getCreatedAt()
        );
    }
}
