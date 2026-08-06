package backend.controllers.impl.orders;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.dispute.DisputeCaseResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.return_.ReturnResponse;
import backend.exceptions.http.ConflictException;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.services.impl.orders.OrderSseService;
import backend.services.intf.CacheService;
import backend.services.intf.IdempotencyService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.orders.ReplacementOrderService;
import backend.services.intf.orders.TrackingService;
import backend.services.intf.payments.DisputeService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.VendorPayoutService;
import backend.services.intf.returns.ReturnService;
import backend.services.intf.subscriptions.SubscriptionService;
import backend.services.intf.vendors.VendorOnboardingService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID ORDER_ID = TestIds.uuid(2);
    private static final UUID RETURN_ID = TestIds.uuid(3);

    private OrderService orderService;
    private PaymentService paymentService;
    private ReturnService returnService;
    private ReplacementOrderService replacementOrderService;
    private SubscriptionService subscriptionService;
    private VendorPayoutService vendorPayoutService;
    private VendorOnboardingService vendorOnboardingService;
    private IdempotencyService idempotencyService;
    private CacheService cacheService;
    private backend.services.intf.RateLimitService rateLimitService;
    private DisputeService disputeService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        paymentService = mock(PaymentService.class);
        returnService = mock(ReturnService.class);
        replacementOrderService = mock(ReplacementOrderService.class);
        subscriptionService = mock(SubscriptionService.class);
        vendorPayoutService = mock(VendorPayoutService.class);
        vendorOnboardingService = mock(VendorOnboardingService.class);
        idempotencyService = mock(IdempotencyService.class);
        cacheService = mock(CacheService.class);
        rateLimitService = mock(backend.services.intf.RateLimitService.class);
        disputeService = mock(DisputeService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(
                        orderService,
                        paymentService,
                        returnService,
                        replacementOrderService,
                        subscriptionService,
                        vendorPayoutService,
                        vendorOnboardingService,
                        idempotencyService,
                        cacheService,
                        mock(TrackingService.class),
                        mock(OrderSseService.class),
                        new ObjectMapper().findAndRegisterModules(),
                        rateLimitService,
                        disputeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrder_createsNewOrderAndStoresIdempotencyRecord() throws Exception {
        authenticateAs(USER_ID);
        when(idempotencyService.lookup("order:create", USER_ID, "idem-1")).thenReturn(Optional.empty());
        when(idempotencyService.claim("order:create", USER_ID, "idem-1", 600L)).thenReturn(true);
        when(orderService.createOrder(eq(USER_ID), any())).thenReturn(orderResponse(OrderStatus.RESERVED.name()));

        mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", "idem-1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "productId", TestIds.uuid(20),
                                        "quantity", 2
                                )),
                                "currency", "USD"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"));

        verify(orderService).createOrder(eq(USER_ID), any());
        verify(idempotencyService).store("order:create", USER_ID, "idem-1", ORDER_ID);
    }

    @Test
    void createOrder_rateLimited_returns429AndDoesNotCreateOrder() throws Exception {
        authenticateAs(USER_ID);
        org.mockito.Mockito.doThrow(new backend.exceptions.http.TooManyRequestException("slow down"))
                .when(rateLimitService).enforce(eq("order:create"), eq(USER_ID.toString()),
                        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());

        mockMvc.perform(post("/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "productId", TestIds.uuid(20),
                                        "quantity", 1
                                ))
                        ))))
                .andExpect(status().isTooManyRequests());

        verify(orderService, never()).createOrder(eq(USER_ID), any());
    }

    @Test
    void createOrder_returnsPriorOrderWhenIdempotencyKeyExists() throws Exception {
        authenticateAs(USER_ID);
        when(idempotencyService.lookup("order:create", USER_ID, "idem-2")).thenReturn(Optional.of(ORDER_ID));
        when(orderService.getOrder(ORDER_ID, USER_ID)).thenReturn(orderResponse(OrderStatus.PAID.name()));

        mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", "idem-2")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "productId", TestIds.uuid(20),
                                        "quantity", 1
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(orderService, never()).createOrder(eq(USER_ID), any());
    }

    @Test
    void createOrder_conflictingInFlightClaimReturns409() throws Exception {
        authenticateAs(USER_ID);
        when(idempotencyService.lookup("order:create", USER_ID, "idem-3")).thenReturn(Optional.empty());
        when(idempotencyService.claim("order:create", USER_ID, "idem-3", 60L)).thenReturn(false);

        mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", "idem-3")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "productId", TestIds.uuid(20),
                                        "quantity", 1
                                ))
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void getOrders_returnsPagedOrders() throws Exception {
        authenticateAs(USER_ID);
        when(orderService.getOrders(USER_ID, OrderStatus.PAID, 1, 10, "createdAt", "asc"))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(orderResponse(OrderStatus.PAID.name())),
                        PageRequest.of(1, 10),
                        1
                )));

        mockMvc.perform(get("/orders")
                        .param("status", "PAID")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "createdAt")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ORDER_ID.toString()));
    }

    @Test
    void stripeWebhook_duplicateEventReturnsOkWithoutSideEffects() throws Exception {
        when(paymentService.constructWebhookEvent("{}", "sig"))
                .thenReturn(new PaymentService.WebhookEvent("evt_1", "payment_intent.succeeded", "pi_1", "payment_intent", Map.of()));
        when(cacheService.tryLock("stripe:event:evt_1", "1", 2678400L)).thenReturn(false);

        mockMvc.perform(post("/orders/webhook/stripe")
                        .header("Stripe-Signature", "sig")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(orderService, never()).handlePaymentSuccess(any());
    }

    @Test
    void stripeWebhook_refundEventDelegatesToReturnService() throws Exception {
        when(paymentService.constructWebhookEvent("{}", "sig"))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_2",
                        "charge.refunded",
                        "ch_1",
                        "charge",
                        Map.of(
                                "refundId", "re_1",
                                "refundStatus", "succeeded",
                                "refundAmountCents", "900"
                        )));
        when(cacheService.tryLock("stripe:event:evt_2", "1", 2678400L)).thenReturn(true);

        mockMvc.perform(post("/orders/webhook/stripe")
                        .header("Stripe-Signature", "sig")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(returnService).handleRefundWebhookEvent("re_1", "succeeded", 900L);
    }

    @Test
    void stripeWebhook_disputeCreatedDelegatesToDisputeService() throws Exception {
        when(paymentService.constructWebhookEvent("{}", "sig"))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_dp_1",
                        "charge.dispute.created",
                        "dp_1",
                        "charge",
                        Map.of(
                                "chargeId", "ch_9",
                                "paymentIntentId", "pi_9",
                                "amountCents", "2500",
                                "currency", "usd",
                                "disputeReason", "fraudulent",
                                "disputeStatus", "needs_response",
                                "evidenceDueBy", "1760000000",
                                "hasEvidence", "false"
                        )));
        when(cacheService.tryLock("stripe:event:evt_dp_1", "1", 2678400L)).thenReturn(true);

        mockMvc.perform(post("/orders/webhook/stripe")
                        .header("Stripe-Signature", "sig")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        ArgumentCaptor<DisputeService.StripeDispute> captor =
                ArgumentCaptor.forClass(DisputeService.StripeDispute.class);
        verify(disputeService).handleDisputeCreated(captor.capture());
        DisputeService.StripeDispute d = captor.getValue();
        assertEquals("dp_1", d.disputeId());
        assertEquals("ch_9", d.chargeId());
        assertEquals("pi_9", d.paymentIntentId());
        assertEquals(2500L, d.amountCents());
        assertEquals("fraudulent", d.reason());
        assertEquals("needs_response", d.stripeStatus());
        assertEquals(Instant.ofEpochSecond(1760000000L), d.evidenceDeadline());
        assertEquals(false, d.hasEvidence());
    }

    @Test
    void stripeWebhook_disputeUpdatedDelegatesToDisputeService() throws Exception {
        when(paymentService.constructWebhookEvent("{}", "sig"))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_dp_2", "charge.dispute.updated", "dp_2", "charge",
                        Map.of("disputeStatus", "under_review")));
        when(cacheService.tryLock("stripe:event:evt_dp_2", "1", 2678400L)).thenReturn(true);

        mockMvc.perform(post("/orders/webhook/stripe")
                        .header("Stripe-Signature", "sig")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(disputeService).handleDisputeUpdated(any());
        verify(disputeService, never()).handleDisputeCreated(any());
    }

    @Test
    void stripeWebhook_disputeClosedDelegatesToDisputeService() throws Exception {
        when(paymentService.constructWebhookEvent("{}", "sig"))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_dp_3", "charge.dispute.closed", "dp_3", "charge",
                        Map.of("disputeStatus", "won", "hasEvidence", "true")));
        when(cacheService.tryLock("stripe:event:evt_dp_3", "1", 2678400L)).thenReturn(true);

        mockMvc.perform(post("/orders/webhook/stripe")
                        .header("Stripe-Signature", "sig")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        ArgumentCaptor<DisputeService.StripeDispute> captor =
                ArgumentCaptor.forClass(DisputeService.StripeDispute.class);
        verify(disputeService).handleDisputeClosed(captor.capture());
        assertEquals("won", captor.getValue().stripeStatus());
        assertEquals(true, captor.getValue().hasEvidence());
    }

    /**
     * Malformed metadata must not produce a 500 — that would make Stripe retry the webhook for
     * 30 days over a field the handler does not even need.
     */
    @Test
    void stripeWebhook_disputeWithMalformedMetadataStillReturnsOk() throws Exception {
        when(paymentService.constructWebhookEvent("{}", "sig"))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_dp_4", "charge.dispute.created", "dp_4", "charge",
                        Map.of("amountCents", "not-a-number", "evidenceDueBy", "garbage")));
        when(cacheService.tryLock("stripe:event:evt_dp_4", "1", 2678400L)).thenReturn(true);

        mockMvc.perform(post("/orders/webhook/stripe")
                        .header("Stripe-Signature", "sig")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        ArgumentCaptor<DisputeService.StripeDispute> captor =
                ArgumentCaptor.forClass(DisputeService.StripeDispute.class);
        verify(disputeService).handleDisputeCreated(captor.capture());
        assertEquals(0L, captor.getValue().amountCents());
        assertNull(captor.getValue().evidenceDeadline());
    }

    @Test
    void getOrderDisputes_returnsDisputesForOrder() throws Exception {
        authenticateAs(USER_ID);
        DisputeCaseResponse dispute = new DisputeCaseResponse(
                TestIds.uuid(30), ORDER_ID, "dp_5", "ch_5", 2500L, "usd", "fraudulent",
                backend.models.enums.DisputeStatus.OPEN, backend.models.enums.DisputeOutcome.PENDING,
                "needs_response", Instant.parse("2026-09-01T00:00:00Z"), null, 3L,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));
        when(disputeService.getDisputesByOrder(ORDER_ID)).thenReturn(List.of(dispute));

        mockMvc.perform(get("/orders/" + ORDER_ID + "/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stripeDisputeId").value("dp_5"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].evidenceCount").value(3));
    }

    @Test
    void issuePartialRefund_delegatesToReturnService() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.issuePartialRefund(ORDER_ID, 500L, "goodwill", USER_ID))
                .thenReturn(returnResponse());

        mockMvc.perform(post("/orders/support/orders/" + ORDER_ID + "/partial-refund")
                        .param("amountCents", "500")
                        .param("reason", "goodwill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.refundStatus").value("PENDING"));
    }

    @Test
    void createReplacement_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(replacementOrderService.createReplacement(eq(ORDER_ID), any(), eq(USER_ID)))
                .thenReturn(orderResponse(OrderStatus.PAID.name()));

        mockMvc.perform(post("/orders/support/orders/" + ORDER_ID + "/replacement")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "variantId", TestIds.uuid(30),
                                        "quantity", 1
                                )),
                                "shippingAddress", "123 King St",
                                "shippingCity", "Toronto",
                                "shippingCountry", "CA",
                                "shippingPostalCode", "M5V1K4"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()));
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private OrderResponse orderResponse(String status) {
        return new OrderResponse(
                ORDER_ID,
                USER_ID,
                List.of(new OrderItemResponse(
                        TestIds.uuid(10),
                        TestIds.uuid(20),
                        "Desk",
                        null,
                        null,
                        null,
                        1,
                        new BigDecimal("19.99"),
                        null,
                        null,
                        FulfillmentStatus.PENDING,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        FulfillmentMethod.DELIVERY
                )),
                new BigDecimal("19.99"),
                "USD",
                status,
                "pi_1",
                "secret",
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "NONE",
                "DELIVERY",
                null,
                null,
                // scheduled delivery slot
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                null,
                // shipping rate selection (Feature 13)
                null, null, null, null, null, null, 0L, null,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }

    private ReturnResponse returnResponse() {
        return new ReturnResponse(
                RETURN_ID,
                ORDER_ID,
                USER_ID,
                "COMPLETED",
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                500L,
                "PENDING",
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z"),
                null,
                null
        );
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
