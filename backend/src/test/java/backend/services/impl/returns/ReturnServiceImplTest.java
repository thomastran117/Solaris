package backend.services.impl.returns;

import backend.annotations.retry.RetryOnConcurrency;
import backend.configurations.environment.RiskProperties;
import backend.dtos.requests.return_.BuyerInitiateReturnRequest;
import backend.dtos.requests.return_.BuyerReturnItemRequest;
import backend.dtos.requests.return_.InspectReturnItemRequest;
import backend.dtos.requests.return_.InspectReturnRequest;
import backend.dtos.requests.return_.MerchantApproveReturnRequest;
import backend.dtos.requests.return_.MerchantRejectReturnRequest;
import backend.dtos.responses.return_.ReturnResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyReturnLocation;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.Return;
import backend.models.core.ReturnItem;
import backend.models.core.User;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.RefundStatus;
import backend.models.enums.ReturnItemCondition;
import backend.models.enums.ReturnReason;
import backend.models.enums.ReturnStatus;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskMode;
import backend.models.enums.UserRole;
import backend.repositories.CompanyReturnLocationRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.ReturnItemRepository;
import backend.repositories.ReturnRepository;
import backend.repositories.RiskAssessmentRepository;
import backend.repositories.UserRepository;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.AuthAuditLogger;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.pricing.RiskEngine;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.risk.RiskAssessmentResult;
import backend.services.risk.RiskSignal;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReturnServiceImplTest {

    private static final UUID ORDER_ID   = TestIds.uuid(1);
    private static final UUID BUYER_ID   = TestIds.uuid(2);
    private static final UUID ITEM_ID    = TestIds.uuid(3);
    private static final UUID PRODUCT_ID = TestIds.uuid(4);
    private static final UUID COMPANY_ID = TestIds.uuid(5);
    private static final UUID RETURN_ID  = TestIds.uuid(6);
    private static final UUID USER_ID    = TestIds.uuid(7);

    private ReturnRepository returnRepository;
    private ReturnItemRepository returnItemRepository;
    private OrderRepository orderRepository;
    private OrderCompensationRepository compensationRepository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private LocationStockRepository locationStockRepository;
    private InventoryAdjustmentRepository adjustmentRepository;
    private CompanyAccessService companyAccessService;
    private CompanyReturnLocationRepository returnLocationRepository;
    private UserRepository userRepository;
    private PaymentService paymentService;
    private RiskEngine riskEngine;
    private RiskAssessmentRepository riskAssessmentRepository;
    private RiskProperties riskProperties;
    private ActivityEventPublisher activityEventPublisher;
    private LoyaltyService loyaltyService;
    private AuthAuditLogger auditLogger;

    private ReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        returnRepository         = mock(ReturnRepository.class);
        returnItemRepository     = mock(ReturnItemRepository.class);
        orderRepository          = mock(OrderRepository.class);
        compensationRepository   = mock(OrderCompensationRepository.class);
        productRepository        = mock(ProductRepository.class);
        variantRepository        = mock(ProductVariantRepository.class);
        locationStockRepository  = mock(LocationStockRepository.class);
        adjustmentRepository     = mock(InventoryAdjustmentRepository.class);
        companyAccessService     = mock(CompanyAccessService.class);
        returnLocationRepository = mock(CompanyReturnLocationRepository.class);
        userRepository           = mock(UserRepository.class);
        paymentService           = mock(PaymentService.class);
        riskEngine               = mock(RiskEngine.class);
        riskAssessmentRepository = mock(RiskAssessmentRepository.class);
        riskProperties           = new RiskProperties();  // real instance — defaults: SHADOW mode, 14-day window
        activityEventPublisher   = mock(ActivityEventPublisher.class);
        loyaltyService           = mock(LoyaltyService.class);
        auditLogger              = mock(AuthAuditLogger.class);

        service = new ReturnServiceImpl(
                returnRepository, returnItemRepository, orderRepository,
                compensationRepository, productRepository, variantRepository,
                locationStockRepository, adjustmentRepository,
                companyAccessService, returnLocationRepository, userRepository,
                paymentService, riskEngine, riskAssessmentRepository,
                riskProperties, activityEventPublisher, loyaltyService, auditLogger);
    }

    // ─── requestReturn ────────────────────────────────────────────────────────

    @Test
    void requestReturn_happyPath_savesAndReturnsResponse() {
        Order order = deliveredOrder();
        order.setDeliveredAt(Instant.now().minus(3, ChronoUnit.DAYS));

        User buyer = user(BUYER_ID, UserRole.USER);
        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(userRepository.getReferenceById(BUYER_ID)).thenReturn(buyer);
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            r.setItems(new ArrayList<>());
            return r;
        });

        BuyerInitiateReturnRequest req = new BuyerInitiateReturnRequest(
                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                ReturnReason.CHANGED_MIND, "Just changed my mind", null);

        ReturnResponse resp = service.requestReturn(ORDER_ID, BUYER_ID, req);

        assertNotNull(resp);
        assertEquals(RETURN_ID, resp.id());
        verify(returnRepository).save(any(Return.class));
    }

    @Test
    void requestReturn_orderNotDelivered_throwsConflictException() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(OrderStatus.PAID);
        order.setItems(new ArrayList<>());
        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () ->
                service.requestReturn(ORDER_ID, BUYER_ID, simpleReturnRequest(ReturnReason.CHANGED_MIND)));
    }

    @Test
    void requestReturn_returnWindowExpired_throwsConflictException() {
        Order order = deliveredOrder();
        order.setDeliveredAt(Instant.now().minus(40, ChronoUnit.DAYS)); // default window is 14 days
        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () ->
                service.requestReturn(ORDER_ID, BUYER_ID, simpleReturnRequest(ReturnReason.CHANGED_MIND)));
    }

    @Test
    void requestReturn_evidenceRequiredReasonWithoutUrls_throwsBadRequestException() {
        Order order = deliveredOrder();
        order.setDeliveredAt(Instant.now().minus(3, ChronoUnit.DAYS));
        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));

        BuyerInitiateReturnRequest req = new BuyerInitiateReturnRequest(
                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                ReturnReason.DEFECTIVE, "broken", null); // DEFECTIVE requires evidence

        assertThrows(BadRequestException.class, () ->
                service.requestReturn(ORDER_ID, BUYER_ID, req));
    }

    @Test
    void requestReturn_htmlInBuyerNote_stripsTagsBeforeSave() {
        Order order = deliveredOrder();
        order.setDeliveredAt(Instant.now().minus(3, ChronoUnit.DAYS));

        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(userRepository.getReferenceById(BUYER_ID)).thenReturn(user(BUYER_ID, UserRole.USER));

        ArgumentCaptor<Return> captor = ArgumentCaptor.forClass(Return.class);
        when(returnRepository.save(captor.capture())).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            r.setItems(new ArrayList<>());
            return r;
        });

        BuyerInitiateReturnRequest req = new BuyerInitiateReturnRequest(
                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                ReturnReason.CHANGED_MIND, "<script>alert('xss')</script>note", null);

        service.requestReturn(ORDER_ID, BUYER_ID, req);

        String savedNote = captor.getValue().getBuyerNote();
        assertTrue(savedNote != null && !savedNote.contains("<script>"), "HTML must be stripped");
        assertTrue(savedNote.contains("note"));
    }

    @Test
    void requestReturn_orderNotFound_throwsResourceNotFoundException() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.requestReturn(ORDER_ID, BUYER_ID, simpleReturnRequest(ReturnReason.CHANGED_MIND)));
    }

    // ─── getReturnsByOrder ────────────────────────────────────────────────────

    @Test
    void getReturnsByOrder_orderNotFound_throwsResourceNotFoundException() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.getReturnsByOrder(ORDER_ID, BUYER_ID));
    }

    @Test
    void getReturnsByOrder_happyPath_returnsResponses() {
        Order order = deliveredOrder();
        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));

        Return ret = minimalReturn(order);
        when(returnRepository.findAllByOrderId(ORDER_ID)).thenReturn(List.of(ret));

        List<ReturnResponse> result = service.getReturnsByOrder(ORDER_ID, BUYER_ID);

        assertEquals(1, result.size());
    }

    // ─── approveReturn ────────────────────────────────────────────────────────

    @Test
    void approveReturn_notInRequestedStatus_throwsConflictException() {
        Company company = company();
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);

        Return ret = scopedReturn(deliveredOrder());
        ret.setStatus(ReturnStatus.APPROVED);
        when(returnRepository.findByIdAndCompanyIdForUpdate(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        assertThrows(ConflictException.class, () ->
                service.approveReturn(RETURN_ID, COMPANY_ID, USER_ID,
                        new MerchantApproveReturnRequest("note", null, null)));
    }

    @Test
    void approveReturn_riskBlocksInEnforceMode_savesAsRejected() {
        riskProperties.setMode(RiskMode.ENFORCE);

        Company company = company();
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);

        Order order = deliveredOrder();
        order.setPaymentIntentId("pi_test");
        User buyer = user(BUYER_ID, UserRole.USER);
        buyer.setEmail("buyer@example.com");
        buyer.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        order.setUser(buyer);

        Return ret = scopedReturn(order);
        ret.setRequestedBy(buyer);
        when(returnRepository.findByIdAndCompanyIdForUpdate(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        CompanyReturnLocation location = returnLocation();
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(location));

        RiskSignal blockSignal = RiskSignal.high(
                backend.models.enums.RiskSignalType.RETURN_PATTERN, 80, "High return rate");
        RiskAssessmentResult blockResult = RiskAssessmentResult.block(80, List.of(blockSignal), List.of());
        when(riskEngine.assess(any())).thenReturn(blockResult);

        ArgumentCaptor<Return> captor = ArgumentCaptor.forClass(Return.class);
        when(returnRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.approveReturn(RETURN_ID, COMPANY_ID, USER_ID,
                new MerchantApproveReturnRequest("note", null, null));

        assertEquals(ReturnStatus.REJECTED, captor.getValue().getStatus());
        verify(paymentService, never()).refundPayment(any(), anyLong());
    }

    @Test
    void approveReturn_refundSucceeds_setsApproved() {
        riskProperties.setMode(RiskMode.SHADOW); // default: do not enforce

        Company company = company();
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);

        Order order = deliveredOrder();
        order.setPaymentIntentId("pi_test");
        order.setTotalAmount(new BigDecimal("100.00"));
        User buyer = user(BUYER_ID, UserRole.USER);
        buyer.setEmail("b@b.com");
        buyer.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));
        order.setUser(buyer);

        OrderItem item = orderItem();
        item.setUnitPrice(new BigDecimal("50.00"));
        order.setItems(List.of(item));

        Return ret = minimalReturn(order);
        ret.setRequestedBy(buyer);
        ret.setItems(List.of(returnItem(ret, item)));
        when(returnRepository.findByIdAndCompanyIdForUpdate(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        CompanyReturnLocation location = returnLocation();
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(location));

        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        when(paymentService.refundPayment(eq("pi_test"), anyLong()))
                .thenReturn(new PaymentService.RefundResult("re_1", 5000L, "usd", "pending", "pi_test"));

        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.approveReturn(RETURN_ID, COMPANY_ID, USER_ID,
                new MerchantApproveReturnRequest("approved", null, null));

        assertEquals(ReturnStatus.APPROVED.name(), resp.status());
        verify(paymentService).refundPayment(eq("pi_test"), anyLong());
    }

    @Test
    void approveReturn_refundThrows_setsFailedAndRecordsCompensation() {
        riskProperties.setMode(RiskMode.SHADOW);

        Company company = company();
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);

        Order order = deliveredOrder();
        order.setPaymentIntentId("pi_test");
        order.setTotalAmount(new BigDecimal("50.00"));
        User buyer = user(BUYER_ID, UserRole.USER);
        buyer.setEmail("b@b.com");
        buyer.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));
        order.setUser(buyer);

        OrderItem item = orderItem();
        item.setUnitPrice(new BigDecimal("50.00"));
        order.setItems(List.of(item));

        Return ret = minimalReturn(order);
        ret.setRequestedBy(buyer);
        ret.setItems(List.of(returnItem(ret, item)));
        when(returnRepository.findByIdAndCompanyIdForUpdate(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        CompanyReturnLocation location = returnLocation();
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(location));

        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        when(paymentService.refundPayment(any(), anyLong())).thenThrow(new RuntimeException("Stripe down"));

        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.approveReturn(RETURN_ID, COMPANY_ID, USER_ID,
                new MerchantApproveReturnRequest("note", null, null));

        assertEquals(ReturnStatus.FAILED.name(), resp.status());
        verify(compensationRepository).save(any());
    }

    @Test
    void approveReturn_zeroAmountOverride_setsApprovedWithRefundStatusNone() {
        riskProperties.setMode(RiskMode.SHADOW);

        Company company = company();
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);

        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("50.00"));
        User buyer = user(BUYER_ID, UserRole.USER);
        buyer.setEmail("b@b.com");
        buyer.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));
        order.setUser(buyer);
        order.setItems(new ArrayList<>());

        Return ret = scopedReturn(order);
        ret.setRequestedBy(buyer);
        when(returnRepository.findByIdAndCompanyIdForUpdate(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        CompanyReturnLocation location = returnLocation();
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(location));

        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.approveReturn(RETURN_ID, COMPANY_ID, USER_ID,
                new MerchantApproveReturnRequest("waiving refund", 0L, null)); // 0 = intentionally waived

        assertEquals(ReturnStatus.APPROVED.name(), resp.status());
        assertEquals(RefundStatus.NONE.name(), resp.refundStatus());
        verify(paymentService, never()).refundPayment(any(), anyLong());
    }

    // ─── inspectReturn ────────────────────────────────────────────────────────

    @Test
    void inspectReturn_wrongStatus_throwsConflictException() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Return ret = scopedReturn(deliveredOrder());
        ret.setStatus(ReturnStatus.REQUESTED); // must be APPROVED to inspect
        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(RETURN_ID, ReturnItemCondition.RESELLABLE, false)), null);

        assertThrows(ConflictException.class, () ->
                service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req));
    }

    @Test
    void inspectReturn_happyPath_setsCompletedAndStockRestored() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        UUID riId = TestIds.uuid(10);
        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);
        OrderItem oi = orderItem();
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setProduct(product());
        ri.setOrderItem(oi);
        ri.setQuantityReturned(1);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.RESELLABLE, false)), null);

        ReturnResponse resp = service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req);

        assertEquals(ReturnStatus.COMPLETED.name(), resp.status());
    }

    // ─── rejectReturn ─────────────────────────────────────────────────────────

    @Test
    void rejectReturn_happyPath_setsRejectedWithNote() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Return ret = scopedReturn(deliveredOrder());
        ret.setStatus(ReturnStatus.REQUESTED);
        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        ArgumentCaptor<Return> captor = ArgumentCaptor.forClass(Return.class);
        when(returnRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.rejectReturn(RETURN_ID, COMPANY_ID, USER_ID, new MerchantRejectReturnRequest("Policy violation"));

        assertEquals(ReturnStatus.REJECTED, captor.getValue().getStatus());
        assertEquals("Policy violation", captor.getValue().getMerchantNote());
    }

    @Test
    void rejectReturn_notInRequestedStatus_throwsConflictException() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Return ret = scopedReturn(deliveredOrder());
        ret.setStatus(ReturnStatus.APPROVED);
        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        assertThrows(ConflictException.class, () ->
                service.rejectReturn(RETURN_ID, COMPANY_ID, USER_ID, new MerchantRejectReturnRequest("reason")));
    }

    // ─── issuePartialRefund ───────────────────────────────────────────────────

    @Test
    void issuePartialRefund_nonStaffUser_throwsForbiddenException() {
        User normalUser = user(USER_ID, UserRole.USER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(normalUser));

        assertThrows(ForbiddenException.class, () ->
                service.issuePartialRefund(ORDER_ID, 1000L, "reason", USER_ID));
    }

    @Test
    void issuePartialRefund_amountNotPositive_throwsBadRequestException() {
        User staff = user(USER_ID, UserRole.SUPPORT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(staff));

        Order order = deliveredOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                service.issuePartialRefund(ORDER_ID, 0L, "reason", USER_ID));
    }

    @Test
    void issuePartialRefund_happyPath_callsAuditLogger() {
        User staff = user(USER_ID, UserRole.SUPPORT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(staff));

        Order order = deliveredOrder();
        order.setPaymentIntentId("pi_test");
        order.setTotalAmount(new BigDecimal("200.00"));
        order.setItems(new ArrayList<>());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        when(paymentService.refundPayment(eq("pi_test"), eq(1000L)))
                .thenReturn(new PaymentService.RefundResult("re_1", 1000L, "usd", "pending", "pi_test"));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            r.setItems(new ArrayList<>());
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.issuePartialRefund(ORDER_ID, 1000L, "Manual refund", USER_ID);

        verify(auditLogger).log(eq(AuthAuditLogger.Event.REFUND_ISSUED), eq(USER_ID.toString()), any(String.class));
    }

    // ─── handleRefundWebhookEvent ─────────────────────────────────────────────

    @Test
    void handleRefundWebhookEvent_noMatchingReturn_doesNothing() {
        when(returnRepository.findByStripeRefundId("re_unknown")).thenReturn(Optional.empty());

        service.handleRefundWebhookEvent("re_unknown", "succeeded", 5000L);

        verify(orderRepository, never()).addRefundAmountDelta(any(), anyLong());
    }

    @Test
    void handleRefundWebhookEvent_succeeded_addsDeltaAndCallsLoyaltyClawback() {
        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStripeRefundId("re_1");
        ret.setRefundedAmountCents(0L);
        when(returnRepository.findByStripeRefundId("re_1")).thenReturn(Optional.of(ret));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleRefundWebhookEvent("re_1", "succeeded", 5000L);

        verify(orderRepository).addRefundAmountDelta(eq(ORDER_ID), eq(5000L));
        verify(loyaltyService).clawbackEarnedPoints(eq(ORDER_ID), anyLong(), anyLong());
    }

    @Test
    void handleRefundWebhookEvent_failed_rollsBackAndRecordsCompensation() {
        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStripeRefundId("re_1");
        ret.setRefundedAmountCents(5000L); // provisional amount to roll back
        when(returnRepository.findByStripeRefundId("re_1")).thenReturn(Optional.of(ret));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleRefundWebhookEvent("re_1", "failed", 5000L);

        verify(orderRepository).addRefundAmountDelta(eq(ORDER_ID), eq(-5000L));
        verify(compensationRepository).save(any());
    }

    // ─── resolveRefundAmount overflow guard ───────────────────────────────────

    @Test
    void approveReturn_overrideExceedsRefundableAmount_throwsBadRequestException() {
        riskProperties.setMode(RiskMode.SHADOW);

        Company company = company();
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);

        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("50.00")); // 5000 cents total
        User buyer = user(BUYER_ID, UserRole.USER);
        buyer.setEmail("b@b.com");
        buyer.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        order.setUser(buyer);
        order.setItems(new ArrayList<>());

        Return ret = scopedReturn(order);
        ret.setRequestedBy(buyer);
        when(returnRepository.findByIdAndCompanyIdForUpdate(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        CompanyReturnLocation location = returnLocation();
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(location));
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));

        assertThrows(BadRequestException.class, () ->
                service.approveReturn(RETURN_ID, COMPANY_ID, USER_ID,
                        new MerchantApproveReturnRequest("note", 9999L, null))); // 9999 > 5000 cents
    }

    // ─── buildReturnItems guards ───────────────────────────────────────────────

    @Test
    void requestReturn_quantityExceedsReturnable_throwsBadRequestException() {
        Order order = deliveredOrder();
        order.setDeliveredAt(Instant.now().minus(3, ChronoUnit.DAYS));

        OrderItem item = orderItem();
        item.setQuantity(2);
        order.setItems(List.of(item));

        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(2); // all already returned

        BuyerInitiateReturnRequest req = new BuyerInitiateReturnRequest(
                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                ReturnReason.CHANGED_MIND, "note", null);

        assertThrows(BadRequestException.class, () ->
                service.requestReturn(ORDER_ID, BUYER_ID, req));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Order deliveredOrder() {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setStatus(OrderStatus.DELIVERED);
        o.setTotalAmount(new BigDecimal("100.00"));

        User u = user(BUYER_ID, UserRole.USER);
        o.setUser(u);

        OrderItem item = orderItem();
        o.setItems(new ArrayList<>(List.of(item)));
        return o;
    }

    private OrderItem orderItem() {
        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setQuantity(2);
        oi.setUnitPrice(new BigDecimal("50.00"));
        oi.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        oi.setProduct(product());
        return oi;
    }

    private Product product() {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setMarketplaceId(null); // no marketplace — skips activity publish
        Company co = company();
        p.setCompany(co);
        return p;
    }

    private Company company() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        return c;
    }

    private User user(UUID id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail(id + "@example.com");
        u.setRole(role);
        u.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        return u;
    }

    /** Return with no items — only for tests that never call requireCompanyScopedReturn. */
    private Return minimalReturn(Order order) {
        Return r = new Return();
        r.setId(RETURN_ID);
        r.setOrder(order);
        r.setStatus(ReturnStatus.REQUESTED);
        r.setItems(new ArrayList<>());
        return r;
    }

    /** Return pre-populated with one item owned by COMPANY_ID so requireCompanyScopedReturn passes. */
    private Return scopedReturn(Order order) {
        Return r = minimalReturn(order);
        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setProduct(product());
        oi.setUnitPrice(new BigDecimal("50.00"));
        oi.setQuantity(1);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        ReturnItem ri = new ReturnItem();
        ri.setId(TestIds.uuid(20));
        ri.setReturnRequest(r);
        ri.setOrderItem(oi);
        ri.setQuantityReturned(1);
        r.setItems(new ArrayList<>(List.of(ri)));
        return r;
    }

    private ReturnItem returnItem(Return ret, OrderItem oi) {
        ReturnItem ri = new ReturnItem();
        ri.setId(TestIds.uuid(20));
        ri.setReturnRequest(ret);
        ri.setOrderItem(oi);
        ri.setQuantityReturned(1);
        return ri;
    }

    private CompanyReturnLocation returnLocation() {
        CompanyReturnLocation loc = new CompanyReturnLocation();
        loc.setId(TestIds.uuid(30));
        loc.setAddress("123 Warehouse Rd");
        loc.setCity("Toronto");
        loc.setCountry("CA");
        loc.setPostalCode("M5V1A1");
        return loc;
    }

    private BuyerInitiateReturnRequest simpleReturnRequest(ReturnReason reason) {
        return new BuyerInitiateReturnRequest(
                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                reason, "note", null);
    }
}
