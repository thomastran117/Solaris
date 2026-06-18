package backend.services.impl.webhook;

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

    private final CompanyWebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final CompanyAccessService companyAccessService;
    private final RestTemplate webhookRestTemplate;

    public WebhookSubscriptionServiceImpl(
            CompanyWebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryLogRepository deliveryLogRepository,
            CompanyAccessService companyAccessService,
            @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.companyAccessService = companyAccessService;
        this.webhookRestTemplate = webhookRestTemplate;
    }

    @Override
    @Transactional
    public WebhookCreationResponse register(UUID companyId, UUID userId, RegisterWebhookRequest request) {
        Company company = companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);

        long existing = subscriptionRepository.countByCompanyId(companyId);
        if (existing >= MAX_SUBSCRIPTIONS) {
            throw new BadRequestException("Maximum of " + MAX_SUBSCRIPTIONS + " webhook endpoints allowed per company");
        }

        SecureRandom rng = new SecureRandom();
        byte[] secretBytes = new byte[32];
        rng.nextBytes(secretBytes);
        String rawSecret = HexFormat.of().formatHex(secretBytes);

        byte[] challengeBytes = new byte[16];
        rng.nextBytes(challengeBytes);
        String challenge = HexFormat.of().formatHex(challengeBytes);

        CompanyWebhookSubscription sub = new CompanyWebhookSubscription();
        sub.setCompany(company);
        sub.setUrl(request.getUrl());
        sub.setSecretToken(rawSecret);
        sub.setVerificationChallenge(challenge);
        sub.setEvents(request.getEvents());
        sub.setStatus(WebhookSubscriptionStatus.PENDING_VERIFICATION);

        sub = subscriptionRepository.save(sub);

        // Attempt immediate verification challenge — activate inline if the endpoint responds correctly
        attemptChallenge(sub);

        return new WebhookCreationResponse(toResponse(sub), rawSecret);
    }

    @Override
    @Transactional
    public void verify(UUID subscriptionId, UUID companyId, UUID userId) {
        companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);

        CompanyWebhookSubscription sub = subscriptionRepository.findByIdAndCompanyId(subscriptionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook subscription not found"));

        boolean passed = sendChallengeGet(sub.getUrl(), sub.getVerificationChallenge());
        if (!passed) {
            throw new BadRequestException("Challenge response did not match — ensure your endpoint echoes the challenge query parameter in the response body");
        }
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

    private void attemptChallenge(CompanyWebhookSubscription sub) {
        try {
            boolean passed = sendChallengeGet(sub.getUrl(), sub.getVerificationChallenge());
            if (passed) {
                sub.setStatus(WebhookSubscriptionStatus.ACTIVE);
                subscriptionRepository.save(sub);
            }
        } catch (Exception e) {
            log.debug("Challenge GET for subscription {} failed during registration: {}", sub.getId(), e.getMessage());
        }
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
