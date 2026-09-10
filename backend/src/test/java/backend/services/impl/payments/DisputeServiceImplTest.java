package backend.services.impl.payments;

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
import backend.models.enums.DisputeEvidenceType;
import backend.models.enums.DisputeOutcome;
import backend.models.enums.DisputeStatus;
import backend.repositories.DisputeCaseRepository;
import backend.repositories.DisputeEvidenceRepository;
import backend.repositories.OrderRepository;
import backend.services.intf.payments.DisputeService.StripeDispute;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisputeServiceImplTest {

    private static final UUID ORDER_ID = TestIds.uuid(1);
    private static final UUID CASE_ID = TestIds.uuid(2);
    private static final UUID STAFF_ID = TestIds.uuid(3);

    private static final String DISPUTE_ID = "dp_1";
    private static final String PAYMENT_INTENT_ID = "pi_1";
    private static final Instant DEADLINE = Instant.parse("2026-09-01T00:00:00Z");

    private DisputeCaseRepository disputeCaseRepository;
    private DisputeEvidenceRepository disputeEvidenceRepository;
    private OrderRepository orderRepository;
    private DisputeEvidenceBuilder evidenceBuilder;
    private EmailService emailService;
    private DisputeServiceImpl service;

    @BeforeEach
    void setUp() {
        disputeCaseRepository = mock(DisputeCaseRepository.class);
        disputeEvidenceRepository = mock(DisputeEvidenceRepository.class);
        orderRepository = mock(OrderRepository.class);
        evidenceBuilder = mock(DisputeEvidenceBuilder.class);
        emailService = mock(EmailService.class);

        EnvironmentSetting env = new EnvironmentSetting();
        env.getEmail().setSupportTeamEmail("chargebacks@shopwave.com");

        service = new DisputeServiceImpl(disputeCaseRepository, disputeEvidenceRepository,
                orderRepository, evidenceBuilder, emailService, env);

        // Default: nothing exists yet, saves echo the entity back with an id.
        when(disputeCaseRepository.findByStripeDisputeId(anyString())).thenReturn(Optional.empty());
        when(disputeCaseRepository.saveAndFlush(any(DisputeCase.class))).thenAnswer(inv -> {
            DisputeCase c = inv.getArgument(0);
            c.setId(CASE_ID);
            return c;
        });
        when(disputeCaseRepository.save(any(DisputeCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(evidenceBuilder.buildEvidence(any(), any())).thenReturn(List.of());
        when(disputeEvidenceRepository.countByDisputeCaseIds(anyList())).thenReturn(List.of());
    }

    // ── handleDisputeCreated ──────────────────────────────────────────────────

    @Test
    void shouldCreateCaseAndAlertSupportWhenDisputeIsNew() {
        Order order = order();
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order));
        when(evidenceBuilder.buildEvidence(any(), eq(order))).thenReturn(List.of(evidence(), evidence()));

        DisputeCaseResponse response = service.handleDisputeCreated(dispute("needs_response", false));

        assertEquals(DISPUTE_ID, response.getStripeDisputeId());
        assertEquals(ORDER_ID, response.getOrderId());
        assertEquals(DisputeStatus.OPEN, response.getStatus());
        assertEquals(DisputeOutcome.PENDING, response.getOutcome());
        assertEquals(2500L, response.getAmountCents());
        assertEquals(DEADLINE, response.getEvidenceDeadline());
        assertEquals(2L, response.getEvidenceCount());

        verify(disputeEvidenceRepository).saveAll(anyList());
        verify(emailService).sendDisputeAlertEmail(
                eq("chargebacks@shopwave.com"), eq(DISPUTE_ID), eq(ORDER_ID),
                eq(2500L), eq("usd"), eq("fraudulent"), eq(DEADLINE));
    }

    @Test
    void shouldNotCreateSecondCaseWhenDisputeAlreadyExists() {
        DisputeCase existing = existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));
        when(disputeEvidenceRepository.countByDisputeCaseId(CASE_ID)).thenReturn(4L);

        DisputeCaseResponse response = service.handleDisputeCreated(dispute("needs_response", false));

        assertEquals(CASE_ID, response.getId());
        assertEquals(4L, response.getEvidenceCount());
        verify(disputeCaseRepository, never()).saveAndFlush(any());
        verify(emailService, never()).sendDisputeAlertEmail(
                anyString(), anyString(), any(), anyLong(), anyString(), anyString(), any());
    }

    /**
     * Two first-deliveries racing: the unique index rejects the loser, whose re-read runs after
     * its own transaction has rolled back and picks up the winner's committed row.
     */
    @Test
    void shouldReturnCommittedCaseWhenUniqueIndexRejectsConcurrentCreate() {
        DisputeCase committed = existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING);
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order()));
        when(disputeCaseRepository.saveAndFlush(any(DisputeCase.class)))
                .thenThrow(new DataIntegrityViolationException("uq_dispute_stripe_id"));
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID))
                .thenReturn(Optional.empty())        // first lookup, before the save
                .thenReturn(Optional.of(committed)); // re-read after the constraint fires

        DisputeCaseResponse response = service.handleDisputeCreated(dispute("needs_response", false));

        assertEquals(CASE_ID, response.getId());
        verify(disputeEvidenceRepository, never()).saveAll(anyList());
        // The loser must not double-alert support for a dispute the winner already reported.
        verify(emailService, never()).sendDisputeAlertEmail(
                anyString(), anyString(), any(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void shouldRethrowWhenConstraintFiresButNoCaseIsFound() {
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order()));
        when(disputeCaseRepository.saveAndFlush(any(DisputeCase.class)))
                .thenThrow(new DataIntegrityViolationException("some other constraint"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.handleDisputeCreated(dispute("needs_response", false)));
    }

    /** Losing the case loses the deadline, so an unmatched charge still opens a case. */
    @Test
    void shouldStillCreateCaseWhenPaymentIntentResolvesToNoOrder() {
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.empty());

        DisputeCaseResponse response = service.handleDisputeCreated(dispute("needs_response", false));

        assertNull(response.getOrderId());
        verify(evidenceBuilder).buildEvidence(any(), eq((Order) null));
        verify(emailService).sendDisputeAlertEmail(
                anyString(), eq(DISPUTE_ID), eq((UUID) null), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void shouldStillCreateCaseWhenDisputeCarriesNoPaymentIntent() {
        StripeDispute d = new StripeDispute(DISPUTE_ID, "ch_1", null, 2500L, "usd",
                "fraudulent", "needs_response", DEADLINE, false);

        DisputeCaseResponse response = service.handleDisputeCreated(d);

        assertNull(response.getOrderId());
        verify(orderRepository, never()).findByPaymentIntentId(anyString());
    }

    @Test
    void shouldSkipEvidencePersistWhenBuilderProducesNothing() {
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order()));
        when(evidenceBuilder.buildEvidence(any(), any())).thenReturn(List.of());

        service.handleDisputeCreated(dispute("needs_response", false));

        verify(disputeEvidenceRepository, never()).saveAll(anyList());
    }

    // ── handleDisputeUpdated ──────────────────────────────────────────────────

    @Test
    void shouldSyncStatusWhenDisputeIsUpdated() {
        DisputeCase existing = existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeUpdated(dispute("under_review", false));

        assertEquals(DisputeStatus.UNDER_REVIEW, existing.getStatus());
        assertEquals("under_review", existing.getStripeStatus());
        assertNull(existing.getClosedAt());
        verify(disputeCaseRepository).save(existing);
    }

    @Test
    void shouldStampClosedAtWhenUpdateArrivesWithTerminalStatus() {
        DisputeCase existing = existingCase(DisputeStatus.UNDER_REVIEW, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeUpdated(dispute("won", true));

        assertEquals(DisputeStatus.CLOSED, existing.getStatus());
        assertEquals(DisputeOutcome.WON, existing.getOutcome());
        assertTrue(existing.getClosedAt() != null);
    }

    /** Stripe can extend a deadline, but a payload without one must not wipe the stored value. */
    @Test
    void shouldKeepExistingDeadlineWhenUpdateOmitsIt() {
        DisputeCase existing = existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING);
        existing.setEvidenceDeadline(DEADLINE);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeUpdated(new StripeDispute(DISPUTE_ID, "ch_1", PAYMENT_INTENT_ID,
                2500L, "usd", "fraudulent", "under_review", null, false));

        assertEquals(DEADLINE, existing.getEvidenceDeadline());
    }

    /**
     * Stripe does not guarantee delivery order, so a stale `.updated` can land after `.closed`.
     * Letting it through would put a settled dispute back in the support queue and the company's
     * open count permanently.
     */
    @Test
    void shouldIgnoreStaleUpdateWhenDisputeIsAlreadyClosed() {
        Instant closedAt = Instant.parse("2026-08-01T12:00:00Z");
        DisputeCase existing = existingCase(DisputeStatus.CLOSED, DisputeOutcome.WON);
        existing.setStripeStatus("won");
        existing.setClosedAt(closedAt);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeUpdated(dispute("under_review", true));

        assertEquals(DisputeStatus.CLOSED, existing.getStatus());
        assertEquals(DisputeOutcome.WON, existing.getOutcome());
        assertEquals("won", existing.getStripeStatus());
        assertEquals(closedAt, existing.getClosedAt());
        verify(disputeCaseRepository, never()).save(any());
    }

    /**
     * {@code warning_closed} maps to CLOSED, so it clears the status guard — but it carries no
     * classifiable outcome, and letting it through wiped a decided WON back to PENDING.
     */
    @Test
    void shouldIgnoreTerminalUpdateThatWouldWipeADecidedOutcome() {
        DisputeCase existing = existingCase(DisputeStatus.CLOSED, DisputeOutcome.WON);
        existing.setStripeStatus("won");
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeUpdated(dispute("warning_closed", true));

        assertEquals(DisputeOutcome.WON, existing.getOutcome());
        assertEquals(DisputeStatus.CLOSED, existing.getStatus());
        assertEquals("won", existing.getStripeStatus());
        verify(disputeCaseRepository, never()).save(any());
    }

    /** A terminal-to-terminal correction (won → lost) is still applied. */
    @Test
    void shouldApplyUpdateWhenClosedDisputeReceivesAnotherTerminalStatus() {
        DisputeCase existing = existingCase(DisputeStatus.CLOSED, DisputeOutcome.WON);
        existing.setClosedAt(Instant.parse("2026-08-01T12:00:00Z"));
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeUpdated(dispute("lost", true));

        assertEquals(DisputeStatus.CLOSED, existing.getStatus());
        assertEquals(DisputeOutcome.LOST, existing.getOutcome());
        verify(disputeCaseRepository).save(existing);
    }

    @Test
    void shouldIgnoreUpdateWhenDisputeIsUnknown() {
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.empty());

        service.handleDisputeUpdated(dispute("under_review", false));

        verify(disputeCaseRepository, never()).save(any());
    }

    // ── handleDisputeClosed ───────────────────────────────────────────────────

    @Test
    void shouldRecordWonOutcomeWhenDisputeIsClosedInOurFavour() {
        DisputeCase existing = existingCase(DisputeStatus.UNDER_REVIEW, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeClosed(dispute("won", true));

        assertEquals(DisputeStatus.CLOSED, existing.getStatus());
        assertEquals(DisputeOutcome.WON, existing.getOutcome());
        assertTrue(existing.getClosedAt() != null);
    }

    @Test
    void shouldRecordLostOutcomeWhenClosedAgainstUsAfterEvidenceWasSubmitted() {
        DisputeCase existing = existingCase(DisputeStatus.UNDER_REVIEW, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeClosed(dispute("lost", true));

        assertEquals(DisputeOutcome.LOST, existing.getOutcome());
    }

    /**
     * A missed deadline also closes as {@code lost} with no evidence, so acceptance must never be
     * inferred — that would relabel the exact failure this feature exists to catch.
     */
    @Test
    void shouldRecordLostNotAcceptedWhenClosedAsLostWithNoEvidenceSubmitted() {
        DisputeCase existing = existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeClosed(dispute("lost", false));

        assertEquals(DisputeOutcome.LOST, existing.getOutcome());
    }

    @Test
    void shouldNeverInferAcceptedOutcomeFromAnyProviderStatus() {
        for (String status : List.of("won", "lost", "warning_closed", "needs_response", "under_review")) {
            assertNotEquals(DisputeOutcome.ACCEPTED, DisputeServiceImpl.mapOutcome(status),
                    "ACCEPTED must come from an explicit local action, never from " + status);
        }
    }

    @Test
    void shouldPreserveOriginalClosedAtWhenCloseIsRedelivered() {
        Instant firstClose = Instant.parse("2026-08-01T12:00:00Z");
        DisputeCase existing = existingCase(DisputeStatus.CLOSED, DisputeOutcome.WON);
        existing.setClosedAt(firstClose);
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.of(existing));

        service.handleDisputeClosed(dispute("won", true));

        assertEquals(firstClose, existing.getClosedAt());
    }

    @Test
    void shouldIgnoreCloseWhenDisputeIsUnknown() {
        when(disputeCaseRepository.findByStripeDisputeId(DISPUTE_ID)).thenReturn(Optional.empty());

        service.handleDisputeClosed(dispute("won", true));

        verify(disputeCaseRepository, never()).save(any());
    }

    // ── Status / outcome mapping ──────────────────────────────────────────────

    @Test
    void shouldMapWarningStatusesOntoTheSameThreeStates() {
        assertEquals(DisputeStatus.OPEN, DisputeServiceImpl.mapStatus("warning_needs_response"));
        assertEquals(DisputeStatus.OPEN, DisputeServiceImpl.mapStatus("needs_response"));
        assertEquals(DisputeStatus.UNDER_REVIEW, DisputeServiceImpl.mapStatus("warning_under_review"));
        assertEquals(DisputeStatus.UNDER_REVIEW, DisputeServiceImpl.mapStatus("under_review"));
        assertEquals(DisputeStatus.CLOSED, DisputeServiceImpl.mapStatus("warning_closed"));
        assertEquals(DisputeStatus.CLOSED, DisputeServiceImpl.mapStatus("won"));
        assertEquals(DisputeStatus.CLOSED, DisputeServiceImpl.mapStatus("lost"));
    }

    /** An unrecognised status must keep the case in the queue, not silently close it. */
    @Test
    void shouldTreatUnknownOrNullStatusAsOpen() {
        assertEquals(DisputeStatus.OPEN, DisputeServiceImpl.mapStatus("something_new"));
        assertEquals(DisputeStatus.OPEN, DisputeServiceImpl.mapStatus(null));
        assertEquals(DisputeOutcome.PENDING, DisputeServiceImpl.mapOutcome("something_new"));
        assertEquals(DisputeOutcome.PENDING, DisputeServiceImpl.mapOutcome(null));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Test
    void shouldReturnOnlyNonClosedDisputesWhenListingOpenCases() {
        Pageable pageable = PageRequest.of(0, 20);
        DisputeCase open = existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findOpenByDeadline(DisputeStatus.CLOSED, pageable))
                .thenReturn(new PageImpl<>(List.of(open), pageable, 1));

        PagedResponse<DisputeCaseResponse> page = service.getOpenDisputes(pageable);

        assertEquals(1, page.getItems().size());
        assertEquals(DISPUTE_ID, page.getItems().get(0).getStripeDisputeId());
        verify(disputeCaseRepository).findOpenByDeadline(DisputeStatus.CLOSED, pageable);
    }

    /** Evidence counts come from one grouped query, not a count per row. */
    @Test
    void shouldCountEvidenceInOneQueryWhenListingOpenCases() {
        Pageable pageable = PageRequest.of(0, 20);
        when(disputeCaseRepository.findOpenByDeadline(DisputeStatus.CLOSED, pageable))
                .thenReturn(new PageImpl<>(List.of(existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING)),
                        pageable, 1));
        when(disputeEvidenceRepository.countByDisputeCaseIds(List.of(CASE_ID)))
                .thenReturn(List.of(evidenceCount(CASE_ID, 5L)));

        PagedResponse<DisputeCaseResponse> page = service.getOpenDisputes(pageable);

        assertEquals(5L, page.getItems().get(0).getEvidenceCount());
        verify(disputeEvidenceRepository, never()).countByDisputeCaseId(any());
    }

    @Test
    void shouldReturnCaseWithEvidenceWhenFetchingDetail() {
        DisputeCase existing = existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(existing));
        when(disputeEvidenceRepository.findAllByDisputeCaseIdOrderByCreatedAtAsc(CASE_ID))
                .thenReturn(List.of(evidence()));

        DisputeCaseDetailResponse detail = service.getDispute(CASE_ID);

        assertEquals(DISPUTE_ID, detail.getDispute().getStripeDisputeId());
        assertEquals(1, detail.getEvidence().size());
        assertEquals(1L, detail.getDispute().getEvidenceCount());
    }

    @Test
    void shouldThrowNotFoundWhenDisputeDoesNotExist() {
        when(disputeCaseRepository.findById(CASE_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getDispute(CASE_ID));
    }

    @Test
    void shouldReturnDisputesForOrderWhenQueriedByOrderId() {
        when(disputeCaseRepository.findAllByOrderIdOrderByCreatedAtDesc(ORDER_ID))
                .thenReturn(List.of(existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING)));

        List<DisputeCaseResponse> disputes = service.getDisputesByOrder(ORDER_ID);

        assertEquals(1, disputes.size());
        assertEquals(DISPUTE_ID, disputes.get(0).getStripeDisputeId());
    }

    @Test
    void shouldReturnEmptyListWhenOrderHasNoDisputes() {
        when(disputeCaseRepository.findAllByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(List.of());

        assertTrue(service.getDisputesByOrder(ORDER_ID).isEmpty());
        verify(disputeEvidenceRepository, never()).countByDisputeCaseIds(anyList());
    }

    // ── Manual evidence ───────────────────────────────────────────────────────

    @Test
    void shouldAttributeManualEvidenceToStaffWhenAdded() {
        DisputeCase existing = existingCase(DisputeStatus.OPEN, DisputeOutcome.PENDING);
        when(disputeCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(existing));
        when(disputeEvidenceRepository.save(any(DisputeEvidence.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AddEvidenceRequest request = new AddEvidenceRequest();
        request.setEvidenceType(DisputeEvidenceType.OTHER);
        request.setContent("Customer confirmed receipt by phone on 3 Aug.");
        request.setAttachmentUrl("https://files.example.com/call-log.pdf");

        DisputeEvidenceResponse response = service.addManualEvidence(CASE_ID, request, STAFF_ID);

        assertEquals(DisputeEvidenceType.OTHER, response.getEvidenceType());
        assertEquals("Customer confirmed receipt by phone on 3 Aug.", response.getContent());
        assertEquals("https://files.example.com/call-log.pdf", response.getAttachmentUrl());
        assertEquals(STAFF_ID, response.getCreatedById());
    }

    @Test
    void shouldThrowNotFoundWhenAddingEvidenceToUnknownDispute() {
        when(disputeCaseRepository.findById(CASE_ID)).thenReturn(Optional.empty());
        AddEvidenceRequest request = new AddEvidenceRequest();
        request.setEvidenceType(DisputeEvidenceType.OTHER);
        request.setContent("orphan");

        assertThrows(ResourceNotFoundException.class,
                () -> service.addManualEvidence(CASE_ID, request, STAFF_ID));
        verify(disputeEvidenceRepository, never()).save(any());
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private StripeDispute dispute(String stripeStatus, boolean hasEvidence) {
        return new StripeDispute(DISPUTE_ID, "ch_1", PAYMENT_INTENT_ID, 2500L, "usd",
                "fraudulent", stripeStatus, DEADLINE, hasEvidence);
    }

    private Order order() {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setPaymentIntentId(PAYMENT_INTENT_ID);
        return o;
    }

    private DisputeCase existingCase(DisputeStatus status, DisputeOutcome outcome) {
        DisputeCase c = new DisputeCase();
        c.setId(CASE_ID);
        c.setStripeDisputeId(DISPUTE_ID);
        c.setStripeChargeId("ch_1");
        c.setStripePaymentIntentId(PAYMENT_INTENT_ID);
        c.setAmountCents(2500L);
        c.setCurrency("usd");
        c.setReason("fraudulent");
        c.setStatus(status);
        c.setOutcome(outcome);
        return c;
    }

    private DisputeEvidence evidence() {
        DisputeEvidence e = new DisputeEvidence();
        e.setId(TestIds.uuid(9));
        e.setEvidenceType(DisputeEvidenceType.ORDER_DETAILS);
        e.setContent("ORDER …");
        return e;
    }

    private backend.repositories.projections.DisputeEvidenceCountProjection evidenceCount(UUID id, long count) {
        return new backend.repositories.projections.DisputeEvidenceCountProjection() {
            @Override public UUID getDisputeCaseId() { return id; }
            @Override public Long getCount() { return count; }
        };
    }
}
