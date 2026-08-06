package backend.services.impl.payments;

import backend.annotations.retry.RetryOnConcurrency;
import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.requests.dispute.AddEvidenceRequest;
import backend.dtos.responses.dispute.DisputeCaseDetailResponse;
import backend.dtos.responses.dispute.DisputeCaseResponse;
import backend.dtos.responses.dispute.DisputeEvidenceResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.DisputeCase;
import backend.models.core.DisputeEvidence;
import backend.models.core.Order;
import backend.models.enums.DisputeOutcome;
import backend.models.enums.DisputeStatus;
import backend.repositories.DisputeCaseRepository;
import backend.repositories.DisputeEvidenceRepository;
import backend.repositories.OrderRepository;
import backend.repositories.projections.DisputeEvidenceCountProjection;
import backend.services.intf.payments.DisputeService;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chargeback case management driven by Stripe dispute webhooks.
 *
 * <p><b>Status mapping.</b> Stripe's dispute status is finer-grained than the support workflow
 * needs — "warning" variants are inquiries where no funds have moved yet, but the response
 * obligation is the same, so both collapse onto the same three states:
 *
 * <pre>
 *   needs_response | warning_needs_response  -> OPEN
 *   under_review   | warning_under_review    -> UNDER_REVIEW
 *   won | lost | warning_closed              -> CLOSED
 * </pre>
 *
 * <p><b>Outcome mapping.</b> Stripe has no "accepted" status: accepting a dispute simply closes it
 * as {@code lost}. The two are told apart by whether evidence was ever submitted.
 *
 * <pre>
 *   won                          -> WON
 *   lost, hasEvidence == true    -> LOST      (ruled against us)
 *   lost, hasEvidence == false   -> ACCEPTED  (we conceded)
 *   anything else                -> PENDING
 * </pre>
 */
@Service
public class DisputeServiceImpl implements DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeServiceImpl.class);

    private final DisputeCaseRepository disputeCaseRepository;
    private final DisputeEvidenceRepository disputeEvidenceRepository;
    private final OrderRepository orderRepository;
    private final DisputeEvidenceBuilder evidenceBuilder;
    private final EmailService emailService;
    private final EnvironmentSetting environmentSetting;

    private DisputeServiceImpl self;

    /**
     * Injects this bean's own proxy so {@code handleDisputeCreated} can call its transactional
     * helpers across distinct transactions. {@code @Lazy} breaks the self-referential cycle at
     * startup.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy DisputeServiceImpl self) {
        this.self = self;
    }

    /**
     * This bean's transactional proxy, falling back to {@code this} when no proxy was injected
     * (plain unit tests with no Spring context), where the helpers simply run inline.
     */
    private DisputeServiceImpl self() {
        return self != null ? self : this;
    }

    public DisputeServiceImpl(DisputeCaseRepository disputeCaseRepository,
                              DisputeEvidenceRepository disputeEvidenceRepository,
                              OrderRepository orderRepository,
                              DisputeEvidenceBuilder evidenceBuilder,
                              EmailService emailService,
                              EnvironmentSetting environmentSetting) {
        this.disputeCaseRepository = disputeCaseRepository;
        this.disputeEvidenceRepository = disputeEvidenceRepository;
        this.orderRepository = orderRepository;
        this.evidenceBuilder = evidenceBuilder;
        this.emailService = emailService;
        this.environmentSetting = environmentSetting;
    }

    // -------------------------------------------------------------------------
    // Webhook handlers
    // -------------------------------------------------------------------------

    /**
     * Deliberately <b>not</b> {@code @Transactional}. The unique index on
     * {@code stripe_dispute_id} is the real idempotency guarantee, and a constraint violation
     * marks its transaction rollback-only — so the losing writer's re-read has to happen after
     * that transaction has ended, not inside it. {@link #createCase} owns the transaction;
     * this method owns the race handling around it.
     */
    @Override
    public DisputeCaseResponse handleDisputeCreated(StripeDispute dispute) {
        // Idempotency (1/2): the common case — a redelivery of an already-handled dispute.
        // Cheap read that avoids the insert, the evidence build, and a second support alert.
        var existing = self().findExistingResponse(dispute.disputeId());
        if (existing.isPresent()) {
            log.info("charge.dispute.created ignored — case already exists for dispute {}",
                    dispute.disputeId());
            return existing.get();
        }

        try {
            return self().createCase(dispute);
        } catch (DataIntegrityViolationException e) {
            // Idempotency (2/2): two first-deliveries raced past the lookup above and the unique
            // index rejected the loser. Its transaction is already rolled back, so this re-read
            // runs cleanly in a new one.
            log.info("Concurrent create for dispute {} — returning the case the other writer committed",
                    dispute.disputeId());
            return self().findExistingResponse(dispute.disputeId()).orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<DisputeCaseResponse> findExistingResponse(String stripeDisputeId) {
        return disputeCaseRepository.findByStripeDisputeId(stripeDisputeId).map(this::toResponse);
    }

    @Transactional
    public DisputeCaseResponse createCase(StripeDispute dispute) {
        Order order = resolveOrder(dispute);

        DisputeCase disputeCase = new DisputeCase();
        disputeCase.setOrder(order);
        disputeCase.setStripeDisputeId(dispute.disputeId());
        disputeCase.setStripeChargeId(dispute.chargeId());
        disputeCase.setStripePaymentIntentId(dispute.paymentIntentId());
        disputeCase.setAmountCents(dispute.amountCents());
        disputeCase.setCurrency(dispute.currency() != null ? dispute.currency() : "usd");
        disputeCase.setReason(dispute.reason());
        disputeCase.setStripeStatus(dispute.stripeStatus());
        disputeCase.setStatus(mapStatus(dispute.stripeStatus()));
        disputeCase.setOutcome(mapOutcome(dispute.stripeStatus(), dispute.hasEvidence()));
        disputeCase.setEvidenceDeadline(dispute.evidenceDeadline());

        // saveAndFlush, not save: the unique-index violation must surface here rather than at
        // commit time, so the caller can tell a duplicate apart from any other failure.
        disputeCase = disputeCaseRepository.saveAndFlush(disputeCase);

        List<DisputeEvidence> evidence = evidenceBuilder.buildEvidence(disputeCase, order);
        if (!evidence.isEmpty()) {
            disputeEvidenceRepository.saveAll(evidence);
        }

        // EmailServiceImpl defers the Kafka publish to afterCommit, so this never fires on rollback.
        emailService.sendDisputeAlertEmail(
                environmentSetting.getEmail().getSupportTeamEmail(),
                disputeCase.getStripeDisputeId(),
                order != null ? order.getId() : null,
                disputeCase.getAmountCents(),
                disputeCase.getCurrency(),
                disputeCase.getReason(),
                disputeCase.getEvidenceDeadline());

        log.info("Opened dispute case {} for stripe dispute {} (order={}, {} evidence entries)",
                disputeCase.getId(), disputeCase.getStripeDisputeId(),
                order != null ? order.getId() : "unresolved", evidence.size());

        return DisputeCaseResponse.from(disputeCase, evidence.size());
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handleDisputeUpdated(StripeDispute dispute) {
        disputeCaseRepository.findByStripeDisputeId(dispute.disputeId()).ifPresentOrElse(c -> {
            c.setStripeStatus(dispute.stripeStatus());
            c.setStatus(mapStatus(dispute.stripeStatus()));
            c.setOutcome(mapOutcome(dispute.stripeStatus(), dispute.hasEvidence()));
            // Stripe can extend a deadline; only overwrite when it actually sent one.
            if (dispute.evidenceDeadline() != null) c.setEvidenceDeadline(dispute.evidenceDeadline());
            if (c.getStatus() == DisputeStatus.CLOSED && c.getClosedAt() == null) {
                c.setClosedAt(Instant.now());
            }
            disputeCaseRepository.save(c);
        }, () -> log.warn("charge.dispute.updated for unknown dispute {} — ignoring", dispute.disputeId()));
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handleDisputeClosed(StripeDispute dispute) {
        disputeCaseRepository.findByStripeDisputeId(dispute.disputeId()).ifPresentOrElse(c -> {
            c.setStripeStatus(dispute.stripeStatus());
            c.setStatus(DisputeStatus.CLOSED);
            c.setOutcome(mapOutcome(dispute.stripeStatus(), dispute.hasEvidence()));
            if (c.getClosedAt() == null) c.setClosedAt(Instant.now());
            disputeCaseRepository.save(c);
            log.info("Dispute {} closed with outcome {}", dispute.disputeId(), c.getOutcome());
        }, () -> log.warn("charge.dispute.closed for unknown dispute {} — ignoring", dispute.disputeId()));
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<DisputeCaseResponse> getOpenDisputes(Pageable pageable) {
        Page<DisputeCase> page =
                disputeCaseRepository.findAllByStatusNotOrderByCreatedAtDesc(DisputeStatus.CLOSED, pageable);

        // One grouped count for the whole page rather than a count per row.
        Map<UUID, Long> counts = evidenceCounts(page.getContent());

        return new PagedResponse<>(page.map(c ->
                DisputeCaseResponse.from(c, counts.getOrDefault(c.getId(), 0L))));
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeCaseDetailResponse getDispute(UUID disputeCaseId) {
        DisputeCase c = loadCase(disputeCaseId);
        return DisputeCaseDetailResponse.from(
                c, disputeEvidenceRepository.findAllByDisputeCaseIdOrderByCreatedAtAsc(disputeCaseId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeCaseResponse> getDisputesByOrder(UUID orderId) {
        List<DisputeCase> cases = disputeCaseRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId);
        Map<UUID, Long> counts = evidenceCounts(cases);
        return cases.stream()
                .map(c -> DisputeCaseResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public DisputeEvidenceResponse addManualEvidence(UUID disputeCaseId, AddEvidenceRequest request,
                                                     UUID staffUserId) {
        DisputeCase c = loadCase(disputeCaseId);

        DisputeEvidence e = new DisputeEvidence();
        e.setDisputeCase(c);
        e.setEvidenceType(request.getEvidenceType());
        e.setContent(request.getContent());
        e.setAttachmentUrl(request.getAttachmentUrl());
        e.setCreatedById(staffUserId);

        return DisputeEvidenceResponse.from(disputeEvidenceRepository.save(e));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Finds the order behind the disputed charge. Returns null — rather than throwing — when the
     * payment intent does not resolve: the case must still be opened so the deadline is not lost,
     * and support reconciles the charge by hand.
     */
    private Order resolveOrder(StripeDispute dispute) {
        if (dispute.paymentIntentId() == null || dispute.paymentIntentId().isBlank()) {
            log.warn("Dispute {} carries no payment intent — case will have no order linkage",
                    dispute.disputeId());
            return null;
        }
        return orderRepository.findByPaymentIntentId(dispute.paymentIntentId()).orElseGet(() -> {
            log.warn("Dispute {} references paymentIntent {} with no matching order",
                    dispute.disputeId(), dispute.paymentIntentId());
            return null;
        });
    }

    private DisputeCase loadCase(UUID disputeCaseId) {
        return disputeCaseRepository.findById(disputeCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found with id: " + disputeCaseId));
    }

    private Map<UUID, Long> evidenceCounts(List<DisputeCase> cases) {
        if (cases.isEmpty()) return Map.of();
        List<UUID> ids = cases.stream().map(DisputeCase::getId).toList();
        return disputeEvidenceRepository.countByDisputeCaseIds(ids).stream()
                .collect(Collectors.toMap(DisputeEvidenceCountProjection::getDisputeCaseId,
                        DisputeEvidenceCountProjection::getCount,
                        (a, b) -> a));
    }

    private DisputeCaseResponse toResponse(DisputeCase c) {
        return DisputeCaseResponse.from(c, disputeEvidenceRepository.countByDisputeCaseId(c.getId()));
    }

    static DisputeStatus mapStatus(String stripeStatus) {
        if (stripeStatus == null) return DisputeStatus.OPEN;
        return switch (stripeStatus.toLowerCase(Locale.ROOT)) {
            case "under_review", "warning_under_review" -> DisputeStatus.UNDER_REVIEW;
            case "won", "lost", "warning_closed"        -> DisputeStatus.CLOSED;
            // needs_response, warning_needs_response, and anything Stripe adds later: assume the
            // clock is running. Treating an unknown status as closed would drop it off the queue.
            default -> DisputeStatus.OPEN;
        };
    }

    static DisputeOutcome mapOutcome(String stripeStatus, boolean hasEvidence) {
        if (stripeStatus == null) return DisputeOutcome.PENDING;
        return switch (stripeStatus.toLowerCase(Locale.ROOT)) {
            case "won"  -> DisputeOutcome.WON;
            case "lost" -> hasEvidence ? DisputeOutcome.LOST : DisputeOutcome.ACCEPTED;
            default     -> DisputeOutcome.PENDING;
        };
    }
}
