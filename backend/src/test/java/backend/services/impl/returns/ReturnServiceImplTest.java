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

import backend.models.core.BundleItem;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.ProductBundle;
import backend.models.core.ProductVariant;
import backend.dtos.requests.return_.MerchantInitiateReturnRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

        // requestReturn now locks the order, and inspectReturn now locks the return. Make the
        // pessimistic-lock finders delegate to whatever the non-locking finders are stubbed to
        // return, so existing per-test stubs keep working (lenient: not every test uses them).
        org.mockito.Mockito.lenient().when(orderRepository.findByIdAndUserIdForUpdate(any(), any()))
                .thenAnswer(inv -> orderRepository.findByIdAndUserId(inv.getArgument(0), inv.getArgument(1)));
        org.mockito.Mockito.lenient().when(returnRepository.findByIdAndCompanyIdForUpdate(any(), any()))
                .thenAnswer(inv -> returnRepository.findByIdAndCompanyId(inv.getArgument(0), inv.getArgument(1)));
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

        // Start from the pre-approval state so we can prove a rejected return doesn't flip it.
        ret.getItems().forEach(ri -> ri.getOrderItem().setFulfillmentStatus(FulfillmentStatus.DELIVERED));

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
        // A risk-rejected return must NOT have flipped its order items to RETURNED.
        ret.getItems().forEach(ri -> org.junit.jupiter.api.Assertions.assertNotEquals(
                backend.models.enums.FulfillmentStatus.RETURNED, ri.getOrderItem().getFulfillmentStatus()));
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
    void approveReturn_autoRefundCappedAtRemainingOrderTotal() {
        // Order-level loyalty/premium discounts are not prorated onto line unit prices, so a full
        // return's summed line value can exceed what was charged. The auto-calculated refund must be
        // capped at (orderTotal - alreadyRefunded) so we never refund more than collected.
        riskProperties.setMode(RiskMode.SHADOW);

        Company company = company();
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company);

        Order order = deliveredOrder();
        order.setPaymentIntentId("pi_test");
        order.setTotalAmount(new BigDecimal("30.00")); // charged total after a big loyalty discount
        order.setRefundedAmountCents(0L);
        User buyer = user(BUYER_ID, UserRole.USER);
        buyer.setEmail("b@b.com");
        buyer.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));
        order.setUser(buyer);

        OrderItem item = orderItem();
        item.setUnitPrice(new BigDecimal("50.00")); // line value (5000c) exceeds remaining (3000c)
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
                .thenReturn(new PaymentService.RefundResult("re_1", 3000L, "usd", "pending", "pi_test"));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approveReturn(RETURN_ID, COMPANY_ID, USER_ID,
                new MerchantApproveReturnRequest("approved", null, null));

        // Capped at remaining order total (3000c), not the summed line value (5000c).
        verify(paymentService).refundPayment("pi_test", 3000L);
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

    // =========================================================================
    // findOwningCompanyId (private — pure calculation)
    // =========================================================================

    @Test
    void findOwningCompanyId_itemWithProduct_returnsProductCompanyId() {
        OrderItem item = new OrderItem();
        item.setProduct(product());

        UUID result = ReflectionTestUtils.invokeMethod(service, "findOwningCompanyId", item);

        assertEquals(COMPANY_ID, result);
    }

    @Test
    void findOwningCompanyId_itemWithBundle_returnsBundleCompanyId() {
        UUID bundleCompanyId = TestIds.uuid(50);
        Company bundleCompany = new Company();
        bundleCompany.setId(bundleCompanyId);
        ProductBundle bundle = new ProductBundle();
        bundle.setCompany(bundleCompany);

        OrderItem item = new OrderItem();
        item.setBundle(bundle);

        UUID result = ReflectionTestUtils.invokeMethod(service, "findOwningCompanyId", item);

        assertEquals(bundleCompanyId, result);
    }

    @Test
    void findOwningCompanyId_noProductNoBundle_returnsNull() {
        OrderItem item = new OrderItem();

        UUID result = ReflectionTestUtils.invokeMethod(service, "findOwningCompanyId", item);

        assertNull(result);
    }

    // =========================================================================
    // resolveOwningCompanyId (private — throws on null)
    // =========================================================================

    @Test
    void resolveOwningCompanyId_itemWithProduct_returnsCompanyId() {
        OrderItem item = new OrderItem();
        item.setId(ITEM_ID);
        item.setProduct(product());

        UUID result = ReflectionTestUtils.invokeMethod(service, "resolveOwningCompanyId", item);

        assertEquals(COMPANY_ID, result);
    }

    @Test
    void resolveOwningCompanyId_itemWithNoOwner_throwsBadRequest() {
        OrderItem item = new OrderItem();
        item.setId(ITEM_ID);

        assertThrows(BadRequestException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "resolveOwningCompanyId", item));
    }

    // =========================================================================
    // resolveReturnLocation (private)
    // =========================================================================

    @Test
    void resolveReturnLocation_withExplicitLocationId_loadsFromRepository() {
        UUID locId = TestIds.uuid(30);
        Company c = new Company(); c.setId(COMPANY_ID);
        CompanyReturnLocation loc = returnLocation();
        when(returnLocationRepository.findByIdAndCompanyId(locId, COMPANY_ID))
                .thenReturn(Optional.of(loc));

        CompanyReturnLocation result =
                ReflectionTestUtils.invokeMethod(service, "resolveReturnLocation", c, locId);

        assertEquals(loc.getId(), result.getId());
    }

    @Test
    void resolveReturnLocation_withExplicitLocationId_notFound_throwsResourceNotFound() {
        UUID locId = TestIds.uuid(31);
        Company c = new Company(); c.setId(COMPANY_ID);
        when(returnLocationRepository.findByIdAndCompanyId(locId, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "resolveReturnLocation", c, locId));
    }

    @Test
    void resolveReturnLocation_withNullLocationId_fallsBackToPrimaryLocation() {
        Company c = new Company(); c.setId(COMPANY_ID);
        CompanyReturnLocation loc = returnLocation();
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(loc));

        CompanyReturnLocation result =
                ReflectionTestUtils.invokeMethod(service, "resolveReturnLocation", c, (UUID) null);

        assertEquals(loc.getId(), result.getId());
    }

    @Test
    void resolveReturnLocation_withNullLocationId_noLocations_throwsConflict() {
        Company c = new Company(); c.setId(COMPANY_ID);
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "resolveReturnLocation", c, (UUID) null));
    }

    // =========================================================================
    // getCompanyReturn
    // =========================================================================

    @Test
    void getCompanyReturn_happyPath_returnsReturnResponse() {
        Company company = company();
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(company);

        Order order = deliveredOrder();
        Return ret = scopedReturn(order);

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID))
                .thenReturn(Optional.of(ret));

        ReturnResponse result = service.getCompanyReturn(RETURN_ID, COMPANY_ID, USER_ID);

        assertNotNull(result);
        assertEquals(RETURN_ID, result.id());
    }

    @Test
    void getCompanyReturn_notFound_throwsResourceNotFound() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(company());
        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCompanyReturn(RETURN_ID, COMPANY_ID, USER_ID));
    }

    // =========================================================================
    // orderTotalCents (private static) — null totalAmount → 0L
    // =========================================================================

    @Test
    void orderTotalCents_withNullTotalAmount_returnsZero() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setTotalAmount(null);

        Long result = ReflectionTestUtils.invokeMethod(service, "orderTotalCents", order);

        assertEquals(0L, result);
    }

    // =========================================================================
    // resolveRefundAmount (private) — negative override
    // =========================================================================

    @Test
    void resolveRefundAmount_negativeOverride_throwsBadRequest() {
        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("100.00"));
        Return ret = minimalReturn(order);

        assertThrows(BadRequestException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "resolveRefundAmount", ret, Long.valueOf(-1L)));
    }

    // =========================================================================
    // serializeRiskSignals (private) — exception path returns "{}"
    // =========================================================================

    @Test
    void serializeRiskSignals_throwsException_returnsEmptyJson() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mockMapper =
                mock(com.fasterxml.jackson.databind.ObjectMapper.class);
        ReflectionTestUtils.setField(service, "objectMapper", mockMapper);
        when(mockMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("simulated") {});

        RiskAssessmentResult result = RiskAssessmentResult.allow(0, List.of(), List.of());
        String json = ReflectionTestUtils.invokeMethod(service, "serializeRiskSignals", result);

        assertEquals("{}", json);
    }

    // =========================================================================
    // getCompanyReturnsByOrder
    // =========================================================================

    @Test
    void getCompanyReturnsByOrder_happyPath_returnsMatchingResponses() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        Return ret = scopedReturn(order); // items owned by COMPANY_ID

        when(returnRepository.findAllByOrderIdAndCompanyId(ORDER_ID, COMPANY_ID))
                .thenReturn(List.of(ret));

        List<ReturnResponse> result = service.getCompanyReturnsByOrder(ORDER_ID, COMPANY_ID, USER_ID);

        assertEquals(1, result.size());
    }

    // =========================================================================
    // merchantInitiateReturn
    // =========================================================================

    @Test
    void merchantInitiateReturn_orderNotFound_throwsResourceNotFound() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                        new backend.dtos.requests.return_.MerchantInitiateReturnRequest(
                                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                                ReturnReason.WRONG_ITEM, "note", false, 0L, null)));
    }

    @Test
    void merchantInitiateReturn_wrongOrderStatus_throwsConflictException() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(OrderStatus.PAID);
        order.setItems(new ArrayList<>());
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(order));

        assertThrows(backend.exceptions.http.ConflictException.class, () ->
                service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                        new backend.dtos.requests.return_.MerchantInitiateReturnRequest(
                                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                                ReturnReason.WRONG_ITEM, "note", false, 0L, null)));
    }

    @Test
    void merchantInitiateReturn_riskBlocksInEnforceMode_throwsConflictException() {
        riskProperties.setMode(RiskMode.ENFORCE);

        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setItems(new ArrayList<>(List.of(orderItem())));
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(order));

        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(returnLocation()));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);

        RiskSignal blockSignal = RiskSignal.high(
                backend.models.enums.RiskSignalType.RETURN_PATTERN, 80, "High return rate");
        when(riskEngine.assess(any()))
                .thenReturn(RiskAssessmentResult.block(80, List.of(blockSignal), List.of()));

        assertThrows(backend.exceptions.http.ConflictException.class, () ->
                service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                        new backend.dtos.requests.return_.MerchantInitiateReturnRequest(
                                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                                ReturnReason.WRONG_ITEM, "note", false, null, null)));
    }

    @Test
    void merchantInitiateReturn_happyPath_waivedRefund_completesReturn() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>(List.of(orderItem())));
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(order));

        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(returnLocation()));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));

        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                new backend.dtos.requests.return_.MerchantInitiateReturnRequest(
                        List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                        ReturnReason.WRONG_ITEM, "wrong item sent", false, 0L, null));

        assertNotNull(resp);
        assertEquals(ReturnStatus.COMPLETED.name(), resp.status());
        verify(paymentService, never()).refundPayment(any(), anyLong());
    }

    @Test
    void merchantInitiateReturn_withRefund_callsPaymentService() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setPaymentIntentId("pi_merchant");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>(List.of(orderItem())));
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(order));

        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(returnLocation()));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        when(paymentService.refundPayment(eq("pi_merchant"), eq(5000L)))
                .thenReturn(new PaymentService.RefundResult("re_m1", 5000L, "usd", "pending", "pi_merchant"));

        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                new backend.dtos.requests.return_.MerchantInitiateReturnRequest(
                        List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                        ReturnReason.WRONG_ITEM, "wrong item", false, 5000L, null));

        assertEquals(ReturnStatus.COMPLETED.name(), resp.status());
        verify(paymentService).refundPayment(eq("pi_merchant"), eq(5000L));
    }

    // =========================================================================
    // computeOrderStatusAfterReturn (private)
    // =========================================================================

    @Test
    void computeOrderStatusAfterReturn_allReturnedFullyRefunded_setsRefunded() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(10000L); // 100.00 * 100 = 10000 cents = fully refunded

        OrderItem item = new OrderItem();
        item.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        order.setItems(List.of(item));

        ReflectionTestUtils.invokeMethod(service, "computeOrderStatusAfterReturn", order);

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
    }

    @Test
    void computeOrderStatusAfterReturn_allReturnedNotFullyRefunded_setsReturned() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(5000L); // only partially refunded

        OrderItem item = new OrderItem();
        item.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        order.setItems(List.of(item));

        ReflectionTestUtils.invokeMethod(service, "computeOrderStatusAfterReturn", order);

        assertEquals(OrderStatus.RETURNED, order.getStatus());
    }

    @Test
    void computeOrderStatusAfterReturn_partialReturn_doesNotChangeStatus() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(OrderStatus.DELIVERED);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);

        OrderItem returned = new OrderItem();
        returned.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        OrderItem stillDelivered = new OrderItem();
        stillDelivered.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        order.setItems(List.of(returned, stillDelivered));

        ReflectionTestUtils.invokeMethod(service, "computeOrderStatusAfterReturn", order);

        assertEquals(OrderStatus.DELIVERED, order.getStatus()); // unchanged
    }

    // =========================================================================
    // resolveRefundAmount (private) — auto-calculate from items
    // =========================================================================

    @Test
    void resolveRefundAmount_nullOverride_autoCalculatesFromItems() {
        Order order = deliveredOrder();
        Return ret = minimalReturn(order);

        OrderItem oi = new OrderItem();
        oi.setUnitPrice(new BigDecimal("25.00"));
        ReturnItem ri = new ReturnItem();
        ri.setOrderItem(oi);
        ri.setQuantityReturned(2);
        ret.setItems(List.of(ri));

        Long result = ReflectionTestUtils.invokeMethod(service, "resolveRefundAmount", ret, (Long) null);

        assertEquals(5000L, result); // 25.00 * 2 * 100 = 5000 cents
    }

    // =========================================================================
    // stripHtml (private static)
    // =========================================================================

    @Test
    void stripHtml_nullInput_returnsNull() {
        String result = ReflectionTestUtils.invokeMethod(service, "stripHtml", (String) null);
        assertNull(result);
    }

    @Test
    void stripHtml_htmlContent_stripsToPlainText() {
        String result = ReflectionTestUtils.invokeMethod(service, "stripHtml", "<b>bold</b> text");
        assertEquals("bold text", result);
    }

    // =========================================================================
    // requireCompanyScopedReturn — item belongs to different company
    // =========================================================================

    @Test
    void getCompanyReturn_returnBelongsToDifferentCompany_throwsResourceNotFound() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        UUID otherCompanyId = TestIds.uuid(99);
        Company otherCompany = new Company();
        otherCompany.setId(otherCompanyId);

        Product otherProduct = new Product();
        otherProduct.setId(TestIds.uuid(100));
        otherProduct.setCompany(otherCompany);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setProduct(otherProduct);

        Return ret = new Return();
        ret.setId(RETURN_ID);
        ret.setOrder(deliveredOrder());
        ret.setStatus(ReturnStatus.REQUESTED);
        ReturnItem ri = new ReturnItem();
        ri.setOrderItem(oi);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCompanyReturn(RETURN_ID, COMPANY_ID, USER_ID));
    }

    @Test
    void getCompanyReturn_emptyItems_throwsResourceNotFound() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Return ret = minimalReturn(deliveredOrder()); // empty items
        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCompanyReturn(RETURN_ID, COMPANY_ID, USER_ID));
    }

    // =========================================================================
    // handleRefundWebhookEvent — idempotency and null provisional
    // =========================================================================

    @Test
    void handleRefundWebhookEvent_succeeded_alreadyApplied_skipsAddDelta() {
        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStripeRefundId("re_dup");
        ret.setRefundedAmountCents(5000L); // already shows 5000 cents applied

        when(returnRepository.findByStripeRefundId("re_dup")).thenReturn(Optional.of(ret));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Same amount as already applied → idempotency guard fires
        service.handleRefundWebhookEvent("re_dup", "succeeded", 5000L);

        verify(orderRepository, never()).addRefundAmountDelta(any(), anyLong());
    }

    @Test
    void handleRefundWebhookEvent_failed_nullProvisional_noDeltaDeducted() {
        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStripeRefundId("re_fail_null");
        ret.setRefundedAmountCents(null); // no provisional amount

        when(returnRepository.findByStripeRefundId("re_fail_null")).thenReturn(Optional.of(ret));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleRefundWebhookEvent("re_fail_null", "failed", 0L);

        verify(orderRepository, never()).addRefundAmountDelta(any(), anyLong());
        verify(compensationRepository).save(any());
    }

    // =========================================================================
    // inspectReturn with restock=true — covers restoreReturnedItemStock
    // =========================================================================

    @Test
    void inspectReturn_withRestockTrue_restoresProductStock() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        UUID riId = TestIds.uuid(11);
        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setUnitPrice(new BigDecimal("50.00"));
        oi.setQuantity(1);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setProduct(product());
        // no variant, no fulfillment location → product-only restore path
        ret.setItems(List.of(ri));
        ri.setOrderItem(oi);
        ri.setQuantityReturned(1);

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.RESELLABLE, true)), null);

        ReturnResponse resp = service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req);

        assertEquals(ReturnStatus.COMPLETED.name(), resp.status());
        verify(productRepository).restoreStock(eq(PRODUCT_ID), eq(1));
        verify(adjustmentRepository).save(any());
    }

    @Test
    void inspectReturn_restockFails_throwsConflictException() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        UUID riId = TestIds.uuid(12);
        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setUnitPrice(new BigDecimal("50.00"));
        oi.setQuantity(1);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setProduct(product());
        ri.setOrderItem(oi);
        ri.setQuantityReturned(1);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        // Make restoreStock throw so anyRestockFailed = true
        org.mockito.Mockito.doThrow(new RuntimeException("stock error"))
                .when(productRepository).restoreStock(any(), anyInt());

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.DAMAGED, true)), null);

        assertThrows(backend.exceptions.http.ConflictException.class, () ->
                service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req));
    }

    // =========================================================================
    // issuePartialRefund — null reason uses default note
    // =========================================================================

    @Test
    void issuePartialRefund_nullReason_usesDefaultNote() {
        User staff = user(USER_ID, UserRole.SUPPORT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(staff));

        Order order = deliveredOrder();
        order.setPaymentIntentId("pi_null_reason");
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setItems(new ArrayList<>());
        order.setRefundedAmountCents(0L);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        when(paymentService.refundPayment(eq("pi_null_reason"), eq(500L)))
                .thenReturn(new PaymentService.RefundResult("re_nr", 500L, "usd", "pending", "pi_null_reason"));
        ArgumentCaptor<Return> captor = ArgumentCaptor.forClass(Return.class);
        when(returnRepository.save(captor.capture())).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            r.setItems(new ArrayList<>());
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.issuePartialRefund(ORDER_ID, 500L, null, USER_ID); // null reason

        assertTrue(captor.getValue().getMerchantNote().contains("support staff"));
    }

    // =========================================================================
    // approveReturn — refundAmountCents > 0 but no payment intent
    // =========================================================================

    @Test
    void approveReturn_noPaymentIntent_setsApprovedWithoutRefund() {
        riskProperties.setMode(RiskMode.SHADOW);

        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setPaymentIntentId(null); // no payment intent
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setRefundedAmountCents(0L);
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

        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(returnLocation()));
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.approveReturn(RETURN_ID, COMPANY_ID, USER_ID,
                new MerchantApproveReturnRequest("approved", null, null));

        assertEquals(ReturnStatus.APPROVED.name(), resp.status());
        verify(paymentService, never()).refundPayment(any(), anyLong());
    }

    // =========================================================================
    // buildReturnItems — item not DELIVERED or SHIPPED
    // =========================================================================

    @Test
    void requestReturn_itemNotDeliveredOrShipped_throwsBadRequest() {
        Order order = deliveredOrder();
        order.setDeliveredAt(Instant.now().minus(3, ChronoUnit.DAYS));

        OrderItem item = orderItem();
        item.setFulfillmentStatus(FulfillmentStatus.CANCELLED);
        order.setItems(List.of(item));

        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);

        assertThrows(BadRequestException.class, () ->
                service.requestReturn(ORDER_ID, BUYER_ID,
                        new BuyerInitiateReturnRequest(
                                List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                                ReturnReason.CHANGED_MIND, "note", null)));
    }

    // =========================================================================
    // buildReturnItems — multi-merchant items (no expectedCompanyId) — buyer path
    // =========================================================================

    @Test
    void requestReturn_multiMerchantItems_throwsBadRequest() {
        Order order = deliveredOrder();
        order.setDeliveredAt(Instant.now().minus(3, ChronoUnit.DAYS));

        Company otherCompany = new Company();
        otherCompany.setId(TestIds.uuid(77));
        Product otherProduct = new Product();
        otherProduct.setId(TestIds.uuid(78));
        otherProduct.setCompany(otherCompany);

        UUID item2Id = TestIds.uuid(79);
        OrderItem item2 = new OrderItem();
        item2.setId(item2Id);
        item2.setQuantity(1);
        item2.setUnitPrice(new BigDecimal("30.00"));
        item2.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        item2.setProduct(otherProduct);

        order.setItems(new ArrayList<>(List.of(orderItem(), item2)));

        when(orderRepository.findByIdAndUserId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(item2Id)).thenReturn(0);

        assertThrows(BadRequestException.class, () ->
                service.requestReturn(ORDER_ID, BUYER_ID,
                        new BuyerInitiateReturnRequest(
                                List.of(
                                        new BuyerReturnItemRequest(ITEM_ID, 1),
                                        new BuyerReturnItemRequest(item2Id, 1)),
                                ReturnReason.CHANGED_MIND, "note", null)));
    }

    // =========================================================================
    // restoreReturnedItemStock — variant path
    // =========================================================================

    @Test
    void inspectReturn_withRestockTrue_restoresVariantStock() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        UUID riId = TestIds.uuid(15);
        UUID variantId = TestIds.uuid(16);
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);

        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);
        ri.setQuantityReturned(2);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setUnitPrice(new BigDecimal("50.00"));
        oi.setQuantity(2);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setProduct(product());
        oi.setVariant(variant);
        ri.setOrderItem(oi);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.RESELLABLE, true)), null);

        ReturnResponse resp = service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req);

        assertEquals(ReturnStatus.COMPLETED.name(), resp.status());
        verify(variantRepository).restoreStock(eq(variantId), eq(2));
    }

    // =========================================================================
    // restoreReturnedItemStock — bundle path (product component, no variant)
    // =========================================================================

    @Test
    void inspectReturn_withRestockTrue_bundleItem_restoresBundleComponents() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        BundleItem bi = new BundleItem();
        bi.setProduct(product());
        bi.setVariant(null);
        bi.setQuantity(2);

        ProductBundle bundle = new ProductBundle();
        bundle.setId(TestIds.uuid(18));
        bundle.setCompany(company());
        bundle.setItems(new ArrayList<>(List.of(bi)));

        UUID riId = TestIds.uuid(17);
        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);
        ri.setQuantityReturned(1);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setUnitPrice(new BigDecimal("80.00"));
        oi.setQuantity(1);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setBundle(bundle);
        ri.setOrderItem(oi);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.RESELLABLE, true)), null);

        service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req);

        verify(productRepository).restoreStock(eq(PRODUCT_ID), eq(2)); // qty=1 * bi.quantity=2
        verify(adjustmentRepository).save(any());
    }

    // =========================================================================
    // issueRefundAndFinalize — refund fails → FAILED status (merchantInitiateReturn)
    // =========================================================================

    @Test
    void merchantInitiateReturn_refundFails_setsFailedStatus() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setPaymentIntentId("pi_fail");
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>(List.of(orderItem())));
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(order));

        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(returnLocation()));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        when(paymentService.refundPayment(any(), anyLong()))
                .thenThrow(new RuntimeException("Stripe down"));

        ArgumentCaptor<Return> captor = ArgumentCaptor.forClass(Return.class);
        when(returnRepository.save(captor.capture())).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                new MerchantInitiateReturnRequest(
                        List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                        ReturnReason.WRONG_ITEM, "wrong item", false, null, null));

        assertEquals(ReturnStatus.FAILED.name(), resp.status());
        verify(compensationRepository).save(any());
    }

    // =========================================================================
    // handleRefundWebhookEvent — loyalty clawback throws → does not propagate
    // =========================================================================

    @Test
    void handleRefundWebhookEvent_loyaltyClawbackThrows_doesNotPropagate() {
        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());

        Return ret = minimalReturn(order);
        ret.setStripeRefundId("re_clawback_fail");
        ret.setRefundedAmountCents(0L);

        when(returnRepository.findByStripeRefundId("re_clawback_fail")).thenReturn(Optional.of(ret));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new RuntimeException("loyalty service down"))
                .when(loyaltyService).clawbackEarnedPoints(any(), anyLong(), anyLong());

        assertDoesNotThrow(() ->
                service.handleRefundWebhookEvent("re_clawback_fail", "succeeded", 3000L));

        verify(orderRepository).addRefundAmountDelta(eq(ORDER_ID), eq(3000L));
    }

    // =========================================================================
    // processReturnItems — restockItems=true (from merchantInitiateReturn)
    // =========================================================================

    @Test
    void merchantInitiateReturn_withRestockTrue_restoresProductStock() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>(List.of(orderItem())));
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(order));

        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(returnLocation()));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));

        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                new MerchantInitiateReturnRequest(
                        List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                        ReturnReason.WRONG_ITEM, "wrong item", true, 0L, null));

        assertEquals(ReturnStatus.COMPLETED.name(), resp.status());
        verify(productRepository).restoreStock(eq(PRODUCT_ID), eq(1));
    }

    // =========================================================================
    // restoreReturnedItemStock — bundle item with variant
    // =========================================================================

    @Test
    void inspectReturn_bundleItemWithVariant_restoresBundleVariantStock() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        UUID variantId = TestIds.uuid(20);
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);

        BundleItem bi = new BundleItem();
        bi.setVariant(variant);
        bi.setQuantity(3);

        Product bundleProduct = new Product();
        bundleProduct.setId(TestIds.uuid(21));
        bi.setProduct(bundleProduct);

        ProductBundle bundle = new ProductBundle();
        bundle.setId(TestIds.uuid(22));
        bundle.setItems(List.of(bi));
        bundle.setCompany(company());

        Order order = deliveredOrder();
        order.setRefundedAmountCents(0L);

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        UUID riId = TestIds.uuid(23);
        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);
        ri.setQuantityReturned(2);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setUnitPrice(new BigDecimal("60.00"));
        oi.setQuantity(2);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setBundle(bundle);
        ri.setOrderItem(oi);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.RESELLABLE, true)), null);

        service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req);

        verify(variantRepository).restoreStock(eq(variantId), eq(6)); // qty=2 * bi.quantity=3
    }

    // =========================================================================
    // restoreReturnedItemStock — fulfillmentLocation present → location stock restored
    // =========================================================================

    @Test
    void inspectReturn_withFulfillmentLocation_restoresLocationStock() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        UUID locationId = TestIds.uuid(30);
        UUID locationStockId = TestIds.uuid(31);

        InventoryLocation location = new InventoryLocation();
        location.setId(locationId);

        LocationStock ls = new LocationStock();
        ls.setId(locationStockId);

        Order order = deliveredOrder();
        order.setRefundedAmountCents(0L);

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        UUID riId = TestIds.uuid(32);
        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);
        ri.setQuantityReturned(1);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setUnitPrice(new BigDecimal("50.00"));
        oi.setQuantity(1);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setProduct(product());
        oi.setFulfillmentLocation(location);
        ri.setOrderItem(oi);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(
                eq(locationId), eq(PRODUCT_ID), eq(null)))
                .thenReturn(Optional.of(ls));

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.RESELLABLE, true)), null);

        service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req);

        verify(productRepository).restoreStock(eq(PRODUCT_ID), eq(1));
        verify(locationStockRepository).restoreStock(eq(locationStockId), eq(1));
        verify(adjustmentRepository).save(any());
    }

    // =========================================================================
    // restoreReturnedItemStock — location restore fails → rollback product stock
    // =========================================================================

    @Test
    void inspectReturn_locationRestoreFails_rollbackProductStockAndSetsRestockFailed() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        UUID locationId = TestIds.uuid(40);
        UUID locationStockId = TestIds.uuid(41);

        InventoryLocation location = new InventoryLocation();
        location.setId(locationId);

        LocationStock ls = new LocationStock();
        ls.setId(locationStockId);

        Order order = deliveredOrder();
        order.setRefundedAmountCents(0L);

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        UUID riId = TestIds.uuid(42);
        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);
        ri.setQuantityReturned(1);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setUnitPrice(new BigDecimal("50.00"));
        oi.setQuantity(1);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setProduct(product());
        oi.setFulfillmentLocation(location);
        ri.setOrderItem(oi);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(locationStockRepository.findByLocationIdAndProductIdAndVariantRef(
                eq(locationId), eq(PRODUCT_ID), eq(null)))
                .thenReturn(Optional.of(ls));
        doThrow(new RuntimeException("location DB error"))
                .when(locationStockRepository).restoreStock(eq(locationStockId), anyInt());

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.RESELLABLE, true)), null);

        assertThrows(backend.exceptions.http.ConflictException.class,
                () -> service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req));

        verify(productRepository).restoreStock(eq(PRODUCT_ID), eq(1));
        verify(productRepository).decrementStock(eq(PRODUCT_ID), eq(1));
    }

    // =========================================================================
    // assessReturn — riskAssessmentRepository.save throws → doesNotPropagate
    // =========================================================================

    @Test
    void merchantInitiateReturn_riskSaveThrows_continuesGracefully() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setPaymentIntentId(null);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>(List.of(orderItem())));
        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(order));

        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(returnLocation()));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        doThrow(new RuntimeException("DB down")).when(riskAssessmentRepository).save(any());

        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                new MerchantInitiateReturnRequest(
                        List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                        ReturnReason.WRONG_ITEM, "wrong item", false, 0L, null));

        assertNotNull(resp);
    }

    // =========================================================================
    // recordReturnAdjustment — adjustmentRepository.save throws → doesNotPropagate
    // =========================================================================

    @Test
    void inspectReturn_adjustmentSaveThrows_doesNotPropagate() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setRefundedAmountCents(0L);

        Return ret = minimalReturn(order);
        ret.setStatus(ReturnStatus.APPROVED);

        UUID riId = TestIds.uuid(50);
        ReturnItem ri = new ReturnItem();
        ri.setId(riId);
        ri.setReturnRequest(ret);
        ri.setQuantityReturned(1);

        OrderItem oi = new OrderItem();
        oi.setId(ITEM_ID);
        oi.setUnitPrice(new BigDecimal("50.00"));
        oi.setQuantity(1);
        oi.setFulfillmentStatus(FulfillmentStatus.RETURNED);
        oi.setProduct(product());
        ri.setOrderItem(oi);
        ret.setItems(List.of(ri));

        when(returnRepository.findByIdAndCompanyId(RETURN_ID, COMPANY_ID)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("adj DB error")).when(adjustmentRepository).save(any());

        InspectReturnRequest req = new InspectReturnRequest(List.of(
                new InspectReturnItemRequest(riId, ReturnItemCondition.RESELLABLE, true)), null);

        assertDoesNotThrow(() -> service.inspectReturn(RETURN_ID, COMPANY_ID, USER_ID, req));
        verify(productRepository).restoreStock(eq(PRODUCT_ID), eq(1));
    }

    // =========================================================================
    // issueRefundAndFinalize — refundAmountCents > 0 but paymentIntentId == null
    // =========================================================================

    @Test
    void merchantInitiateReturn_withAmountButNoPaymentIntent_completesWithoutRefund() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company());

        Order order = deliveredOrder();
        order.setPaymentIntentId(null);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setRefundedAmountCents(0L);

        OrderItem item = orderItem();
        item.setUnitPrice(new BigDecimal("50.00"));
        order.setItems(new ArrayList<>(List.of(item)));

        when(orderRepository.findByIdAndProductCompanyIdForUpdate(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(order));
        when(returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(COMPANY_ID))
                .thenReturn(Optional.of(returnLocation()));
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(ITEM_ID)).thenReturn(0);
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> {
            Return r = inv.getArgument(0);
            r.setId(RETURN_ID);
            return r;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse resp = service.merchantInitiateReturn(ORDER_ID, COMPANY_ID, USER_ID,
                new MerchantInitiateReturnRequest(
                        List.of(new BuyerReturnItemRequest(ITEM_ID, 1)),
                        ReturnReason.WRONG_ITEM, "wrong", false, null, null));

        assertEquals(ReturnStatus.COMPLETED.name(), resp.status());
        verify(paymentService, never()).refundPayment(any(), anyLong());
    }
}
