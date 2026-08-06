package backend.integration.payments;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.DisputeCase;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.DisputeOutcome;
import backend.models.enums.DisputeStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.DisputeCaseRepository;
import backend.repositories.DisputeEvidenceRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.services.intf.payments.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end chargeback handling against real PostgreSQL: webhook → case → evidence → admin API.
 *
 * <p>{@link PaymentService} is mocked so tests can hand the controller a crafted
 * {@code WebhookEvent} without a real Stripe signature; everything downstream of that is live.
 */
class DisputeIT extends AbstractIntegrationIT {

    @MockitoBean private PaymentService paymentService;

    @Autowired private DisputeCaseRepository disputeCaseRepository;
    @Autowired private DisputeEvidenceRepository disputeEvidenceRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;

    private static final String WEBHOOK = "/orders/webhook/stripe";
    private static final String ADMIN = "/admin/disputes";
    private static final long DEADLINE_EPOCH = 1_790_000_000L;

    // ── charge.dispute.created ────────────────────────────────────────────────

    @Test
    void shouldCreateCaseWithEvidenceWhenDisputeCreatedWebhookArrives() throws Exception {
        Order order = seedOrder("pi_dispute_1");

        postWebhook("evt_1", "charge.dispute.created", "dp_1", createdMetadata("pi_dispute_1"));

        DisputeCase saved = disputeCaseRepository.findByStripeDisputeId("dp_1").orElseThrow();
        assertEquals(order.getId(), saved.getOrder().getId());
        assertEquals(DisputeStatus.OPEN, saved.getStatus());
        assertEquals(DisputeOutcome.PENDING, saved.getOutcome());
        assertEquals(2500L, saved.getAmountCents());
        assertEquals("fraudulent", saved.getReason());
        assertEquals(Instant.ofEpochSecond(DEADLINE_EPOCH), saved.getEvidenceDeadline());

        // AC 2: items, shipping address and tracking checkpoints at minimum.
        String evidence = disputeEvidenceRepository
                .findAllByDisputeCaseIdOrderByCreatedAtAsc(saved.getId()).stream()
                .map(e -> e.getContent())
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(evidence.contains("Disputed Widget"), "order items");
        assertTrue(evidence.contains("Grace Hopper"), "shipping address");
        assertTrue(evidence.contains("1Z-IT-TRACK"), "tracking number");
    }

    /** AC 1: the support team is alerted on case creation. */
    @Test
    void shouldAlertSupportTeamWhenDisputeIsCreated() throws Exception {
        Order order = seedOrder("pi_dispute_2");

        postWebhook("evt_2", "charge.dispute.created", "dp_2", createdMetadata("pi_dispute_2"));

        verify(emailService).sendDisputeAlertEmail(
                anyString(), eq("dp_2"), eq(order.getId()), eq(2500L), eq("usd"), eq("fraudulent"),
                eq(Instant.ofEpochSecond(DEADLINE_EPOCH)));
    }

    /**
     * AC 5. The event id differs, so the controller's Redis dedup guard does not fire — this
     * proves idempotency comes from the unique index on {@code stripe_dispute_id}.
     */
    @Test
    void shouldNotCreateSecondCaseWhenDisputeCreatedIsRedeliveredUnderNewEventId() throws Exception {
        seedOrder("pi_dispute_3");

        postWebhook("evt_3a", "charge.dispute.created", "dp_3", createdMetadata("pi_dispute_3"));
        postWebhook("evt_3b", "charge.dispute.created", "dp_3", createdMetadata("pi_dispute_3"));

        assertEquals(1, disputeCaseRepository.findAll().size());
        DisputeCase saved = disputeCaseRepository.findByStripeDisputeId("dp_3").orElseThrow();
        // Evidence must not be duplicated either.
        long evidenceCount = disputeEvidenceRepository.countByDisputeCaseId(saved.getId());
        assertEquals(evidenceCount,
                disputeEvidenceRepository.findAllByDisputeCaseIdOrderByCreatedAtAsc(saved.getId()).size());
        verify(emailService).sendDisputeAlertEmail(
                anyString(), eq("dp_3"), any(), anyLong(), anyString(), anyString(), any());
    }

    /** An unmatched charge must still open a case — otherwise the deadline is lost silently. */
    @Test
    void shouldCreateCaseWithoutOrderWhenPaymentIntentMatchesNothing() throws Exception {
        postWebhook("evt_4", "charge.dispute.created", "dp_4", createdMetadata("pi_does_not_exist"));

        DisputeCase saved = disputeCaseRepository.findByStripeDisputeId("dp_4").orElseThrow();
        assertNotNull(saved.getId());
        assertEquals(null, saved.getOrder());
        assertEquals(0, disputeEvidenceRepository.countByDisputeCaseId(saved.getId()));
    }

    // ── charge.dispute.updated / closed ───────────────────────────────────────

    @Test
    void shouldSyncStatusWhenDisputeUpdatedWebhookArrives() throws Exception {
        seedOrder("pi_dispute_5");
        postWebhook("evt_5a", "charge.dispute.created", "dp_5", createdMetadata("pi_dispute_5"));

        postWebhook("evt_5b", "charge.dispute.updated", "dp_5",
                Map.of("disputeStatus", "under_review", "hasEvidence", "true"));

        DisputeCase saved = disputeCaseRepository.findByStripeDisputeId("dp_5").orElseThrow();
        assertEquals(DisputeStatus.UNDER_REVIEW, saved.getStatus());
        assertEquals(DisputeOutcome.PENDING, saved.getOutcome());
    }

    /** AC 4. */
    @Test
    void shouldRecordWonOutcomeWhenDisputeClosedWebhookArrives() throws Exception {
        seedOrder("pi_dispute_6");
        postWebhook("evt_6a", "charge.dispute.created", "dp_6", createdMetadata("pi_dispute_6"));

        postWebhook("evt_6b", "charge.dispute.closed", "dp_6",
                Map.of("disputeStatus", "won", "hasEvidence", "true"));

        DisputeCase saved = disputeCaseRepository.findByStripeDisputeId("dp_6").orElseThrow();
        assertEquals(DisputeStatus.CLOSED, saved.getStatus());
        assertEquals(DisputeOutcome.WON, saved.getOutcome());
        assertNotNull(saved.getClosedAt());
    }

    /** AC 4. */
    @Test
    void shouldRecordLostOutcomeWhenDisputeClosedAgainstUs() throws Exception {
        seedOrder("pi_dispute_7");
        postWebhook("evt_7a", "charge.dispute.created", "dp_7", createdMetadata("pi_dispute_7"));

        postWebhook("evt_7b", "charge.dispute.closed", "dp_7",
                Map.of("disputeStatus", "lost", "hasEvidence", "true"));

        assertEquals(DisputeOutcome.LOST,
                disputeCaseRepository.findByStripeDisputeId("dp_7").orElseThrow().getOutcome());
    }

    @Test
    void shouldRecordAcceptedOutcomeWhenClosedAsLostWithoutEvidence() throws Exception {
        seedOrder("pi_dispute_8");
        postWebhook("evt_8a", "charge.dispute.created", "dp_8", createdMetadata("pi_dispute_8"));

        postWebhook("evt_8b", "charge.dispute.closed", "dp_8",
                Map.of("disputeStatus", "lost", "hasEvidence", "false"));

        assertEquals(DisputeOutcome.ACCEPTED,
                disputeCaseRepository.findByStripeDisputeId("dp_8").orElseThrow().getOutcome());
    }

    @Test
    void shouldReturnOkAndCreateNothingWhenClosingAnUnknownDispute() throws Exception {
        postWebhook("evt_9", "charge.dispute.closed", "dp_unknown",
                Map.of("disputeStatus", "won", "hasEvidence", "true"));

        assertTrue(disputeCaseRepository.findAll().isEmpty());
    }

    // ── GET /admin/disputes ───────────────────────────────────────────────────

    /** AC 3. */
    @Test
    void shouldReturnOnlyOpenDisputesWhenListingAsStaff() throws Exception {
        seedOrder("pi_list_1");
        postWebhook("evt_l1", "charge.dispute.created", "dp_open", createdMetadata("pi_list_1"));
        seedOrder("pi_list_2");
        postWebhook("evt_l2", "charge.dispute.created", "dp_closed", createdMetadata("pi_list_2"));
        postWebhook("evt_l3", "charge.dispute.closed", "dp_closed",
                Map.of("disputeStatus", "won", "hasEvidence", "true"));

        String token = accessTokenFor(createStaffUser("dispute-list@example.com"));

        mockMvc.perform(get(ADMIN).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].stripeDisputeId").value("dp_open"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void shouldPaginateWhenMoreDisputesExistThanPageSize() throws Exception {
        for (int i = 0; i < 3; i++) {
            seedOrder("pi_page_" + i);
            postWebhook("evt_p" + i, "charge.dispute.created", "dp_page_" + i,
                    createdMetadata("pi_page_" + i));
        }
        String token = accessTokenFor(createStaffUser("dispute-page@example.com"));

        mockMvc.perform(get(ADMIN).param("page", "0").param("size", "2")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void shouldReturnForbiddenWhenPlainUserListsDisputes() throws Exception {
        String token = accessTokenFor(createActiveUser("dispute-nosy@example.com", "Password1!"));

        mockMvc.perform(get(ADMIN).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenListingDisputesAnonymously() throws Exception {
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
    }

    // ── GET /admin/disputes/{id} ──────────────────────────────────────────────

    @Test
    void shouldReturnCaseWithEvidenceWhenFetchingDetailAsStaff() throws Exception {
        seedOrder("pi_detail_1");
        postWebhook("evt_d1", "charge.dispute.created", "dp_detail", createdMetadata("pi_detail_1"));
        UUID caseId = disputeCaseRepository.findByStripeDisputeId("dp_detail").orElseThrow().getId();
        String token = accessTokenFor(createStaffUser("dispute-detail@example.com"));

        mockMvc.perform(get(ADMIN + "/" + caseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dispute.stripeDisputeId").value("dp_detail"))
                .andExpect(jsonPath("$.data.evidence[0].evidenceType").value("ORDER_DETAILS"));
    }

    @Test
    void shouldReturnNotFoundWhenFetchingUnknownDispute() throws Exception {
        String token = accessTokenFor(createStaffUser("dispute-404@example.com"));

        mockMvc.perform(get(ADMIN + "/" + UUID.randomUUID()).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    // ── POST /admin/disputes/{id}/evidence ────────────────────────────────────

    /** AC 7. */
    @Test
    void shouldAppendManualEvidenceWhenStaffAddsAnEntry() throws Exception {
        seedOrder("pi_evidence_1");
        postWebhook("evt_e1", "charge.dispute.created", "dp_evidence", createdMetadata("pi_evidence_1"));
        DisputeCase saved = disputeCaseRepository.findByStripeDisputeId("dp_evidence").orElseThrow();
        long before = disputeEvidenceRepository.countByDisputeCaseId(saved.getId());
        User staff = createStaffUser("dispute-evidence@example.com");

        mockMvc.perform(post(ADMIN + "/" + saved.getId() + "/evidence")
                        .header("Authorization", bearer(accessTokenFor(staff)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evidenceType", "OTHER",
                                "content", "Customer confirmed receipt by phone.\nCall recorded 3 Aug."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evidenceType").value("OTHER"))
                .andExpect(jsonPath("$.data.createdById").value(staff.getId().toString()));

        assertEquals(before + 1, disputeEvidenceRepository.countByDisputeCaseId(saved.getId()));
    }

    @Test
    void shouldReturnBadRequestWhenManualEvidenceContentIsBlank() throws Exception {
        seedOrder("pi_evidence_2");
        postWebhook("evt_e2", "charge.dispute.created", "dp_evidence2", createdMetadata("pi_evidence_2"));
        UUID caseId = disputeCaseRepository.findByStripeDisputeId("dp_evidence2").orElseThrow().getId();
        String token = accessTokenFor(createStaffUser("dispute-blank@example.com"));

        mockMvc.perform(post(ADMIN + "/" + caseId + "/evidence")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evidenceType", "OTHER", "content", "   "))))
                .andExpect(status().isBadRequest());
    }

    /**
     * The value is rendered back as an {@code <a href>}, so the scheme is enforced server-side —
     * a {@code javascript:} URL must not be persistable by calling the API directly.
     */
    @Test
    void shouldReturnBadRequestWhenAttachmentUrlIsNotHttps() throws Exception {
        seedOrder("pi_evidence_4");
        postWebhook("evt_e4", "charge.dispute.created", "dp_evidence4", createdMetadata("pi_evidence_4"));
        DisputeCase saved = disputeCaseRepository.findByStripeDisputeId("dp_evidence4").orElseThrow();
        String token = accessTokenFor(createStaffUser("dispute-badurl@example.com"));

        for (String badUrl : List.of("javascript:alert(1)", "http://files.example.com/x.pdf", "not-a-url")) {
            mockMvc.perform(post(ADMIN + "/" + saved.getId() + "/evidence")
                            .header("Authorization", bearer(token))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "evidenceType", "OTHER",
                                    "content", "proof",
                                    "attachmentUrl", badUrl))))
                    .andExpect(status().isBadRequest());
        }

        assertEquals(0, disputeEvidenceRepository.findAllByDisputeCaseIdOrderByCreatedAtAsc(saved.getId())
                .stream().filter(e -> e.getAttachmentUrl() != null).count());
    }

    @Test
    void shouldAcceptHttpsAttachmentUrlWhenAddingEvidence() throws Exception {
        seedOrder("pi_evidence_5");
        postWebhook("evt_e5", "charge.dispute.created", "dp_evidence5", createdMetadata("pi_evidence_5"));
        DisputeCase saved = disputeCaseRepository.findByStripeDisputeId("dp_evidence5").orElseThrow();
        String token = accessTokenFor(createStaffUser("dispute-goodurl@example.com"));

        mockMvc.perform(post(ADMIN + "/" + saved.getId() + "/evidence")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evidenceType", "OTHER",
                                "content", "Signed POD",
                                "attachmentUrl", "https://files.example.com/pod.pdf"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attachmentUrl").value("https://files.example.com/pod.pdf"));
    }

    @Test
    void shouldReturnForbiddenWhenPlainUserAddsEvidence() throws Exception {
        seedOrder("pi_evidence_3");
        postWebhook("evt_e3", "charge.dispute.created", "dp_evidence3", createdMetadata("pi_evidence_3"));
        UUID caseId = disputeCaseRepository.findByStripeDisputeId("dp_evidence3").orElseThrow().getId();
        String token = accessTokenFor(createActiveUser("dispute-noevidence@example.com", "Password1!"));

        mockMvc.perform(post(ADMIN + "/" + caseId + "/evidence")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evidenceType", "OTHER", "content", "let me in"))))
                .andExpect(status().isForbidden());
    }

    // ── GET /orders/{orderId}/disputes ────────────────────────────────────────

    @Test
    void shouldReturnDisputesForOrderWhenQueriedByStaff() throws Exception {
        Order order = seedOrder("pi_order_disputes");
        postWebhook("evt_o1", "charge.dispute.created", "dp_order", createdMetadata("pi_order_disputes"));
        String token = accessTokenFor(createStaffUser("dispute-byorder@example.com"));

        mockMvc.perform(get("/orders/" + order.getId() + "/disputes")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].stripeDisputeId").value("dp_order"));
    }

    @Test
    void shouldReturnEmptyListWhenOrderHasNoDisputes() throws Exception {
        Order order = seedOrder("pi_clean_order");
        String token = accessTokenFor(createStaffUser("dispute-clean@example.com"));

        mockMvc.perform(get("/orders/" + order.getId() + "/disputes")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    /** Customer-facing dispute status is out of scope — the order's own owner must not see it. */
    @Test
    void shouldReturnForbiddenWhenOrderOwnerQueriesTheirOwnDisputes() throws Exception {
        Order order = seedOrder("pi_owner_blocked");
        String token = accessTokenFor(userRepository.findById(order.getUser().getId()).orElseThrow());

        mockMvc.perform(get("/orders/" + order.getId() + "/disputes")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void postWebhook(String eventId, String type, String disputeId,
                             Map<String, String> metadata) throws Exception {
        when(paymentService.constructWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(eventId, type, disputeId, "charge", metadata));

        mockMvc.perform(post(WEBHOOK)
                        .header("Stripe-Signature", "sig")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private Map<String, String> createdMetadata(String paymentIntentId) {
        return Map.of(
                "chargeId", "ch_" + paymentIntentId,
                "paymentIntentId", paymentIntentId,
                "amountCents", "2500",
                "currency", "usd",
                "disputeReason", "fraudulent",
                "disputeStatus", "needs_response",
                "evidenceDueBy", String.valueOf(DEADLINE_EPOCH),
                "hasEvidence", "false");
    }

    /** A PAID, shipped order with one item and a full shipping snapshot — enough for every section. */
    private Order seedOrder(String paymentIntentId) {
        User customer = createActiveUser("buyer-" + UUID.randomUUID() + "@example.com", "Password1!");
        User merchant = createActiveUser("merchant-" + UUID.randomUUID() + "@example.com", "Password1!");

        Company company = new Company();
        company.setOwner(merchant);
        company.setName("Dispute Co " + UUID.randomUUID());
        company = companyRepository.save(company);

        Product product = new Product();
        product.setCompany(company);
        product.setName("Disputed Widget");
        product.setPrice(BigDecimal.valueOf(24.99));
        product.setStatus(ProductStatus.ACTIVE);
        product.setPurchasable(true);
        product.setListed(true);
        product = productRepository.save(product);

        Order order = new Order();
        order.setUser(customer);
        order.setStatus(OrderStatus.PAID);
        order.setCurrency("USD");
        order.setTotalAmount(BigDecimal.valueOf(24.99));
        order.setPaymentIntentId(paymentIntentId);
        order.setPaidAt(Instant.now());
        order.setShipRecipientName("Grace Hopper");
        order.setShipStreet("1 Compiler Lane");
        order.setShipCity("Arlington");
        order.setShipState("VA");
        order.setShipPostalCode("22201");
        order.setShipCountry("US");
        order.setCarrier("UPS");
        order.setTrackingNumber("1Z-IT-TRACK");
        order.setShippedAt(Instant.now());

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName("Disputed Widget");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(24.99));
        order.setItems(List.of(item));

        return orderRepository.save(order);
    }

    private User createStaffUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.SUPPORT);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
