package backend.services.impl.returns;

import backend.configurations.environment.RiskProperties;
import backend.dtos.requests.return_.BuyerInitiateReturnRequest;
import backend.dtos.requests.return_.BuyerReturnItemRequest;
import backend.dtos.requests.return_.MerchantRejectReturnRequest;
import backend.dtos.responses.return_.ReturnResponse;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.exceptions.http.BadRequestException;
import backend.models.core.Company;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Return;
import backend.models.core.ReturnItem;
import backend.models.core.User;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.RefundStatus;
import backend.models.enums.ReturnReason;
import backend.models.enums.ReturnStatus;
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
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReturnServiceLifecycleTest {

    private ReturnRepository returnRepository;
    private ReturnItemRepository returnItemRepository;
    private OrderRepository orderRepository;
    private CompanyAccessService companyAccessService;
    private UserRepository userRepository;
    private LoyaltyService loyaltyService;
    private ActivityEventPublisher activityEventPublisher;
    private ReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        returnRepository = mock(ReturnRepository.class);
        returnItemRepository = mock(ReturnItemRepository.class);
        orderRepository = mock(OrderRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        userRepository = mock(UserRepository.class);
        loyaltyService = mock(LoyaltyService.class);
        activityEventPublisher = mock(ActivityEventPublisher.class);

        RiskProperties riskProperties = mock(RiskProperties.class);
        RiskProperties.ReturnPolicy returnPolicy = mock(RiskProperties.ReturnPolicy.class);
        when(returnPolicy.getWindowDays()).thenReturn(30);
        when(riskProperties.getReturnPolicy()).thenReturn(returnPolicy);

        service = new ReturnServiceImpl(
                returnRepository,
                returnItemRepository,
                orderRepository,
                mock(OrderCompensationRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                companyAccessService,
                mock(CompanyReturnLocationRepository.class),
                userRepository,
                mock(PaymentService.class),
                mock(RiskEngine.class),
                mock(RiskAssessmentRepository.class),
                riskProperties,
                activityEventPublisher,
                loyaltyService,
                mock(AuthAuditLogger.class)
        );
    }

    @Test
    void requestReturn_stripsHtmlAndPublishesActivity() {
        UUID buyerId = TestIds.uuid(1);
        UUID orderId = TestIds.uuid(2);
        UUID orderItemId = TestIds.uuid(3);
        UUID productId = TestIds.uuid(4);
        UUID marketplaceId = TestIds.uuid(5);

        User buyer = user(buyerId);
        Order order = order(orderId, buyer, OrderStatus.DELIVERED);
        OrderItem orderItem = orderItem(orderItemId, productId, TestIds.uuid(6), marketplaceId);
        order.setItems(List.of(orderItem));

        when(orderRepository.findByIdAndUserId(orderId, buyerId)).thenReturn(Optional.of(order));
        when(userRepository.getReferenceById(buyerId)).thenReturn(buyer);
        when(returnItemRepository.sumReturnedQuantityByOrderItemId(orderItemId)).thenReturn(0);
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> {
            Return ret = inv.getArgument(0);
            ret.setId(TestIds.uuid(20));
            ret.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            ret.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            ret.getItems().forEach(item -> item.setId(TestIds.uuid(21)));
            return ret;
        });

        ReturnResponse response = service.requestReturn(orderId, buyerId, new BuyerInitiateReturnRequest(
                List.of(new BuyerReturnItemRequest(orderItemId, 1)),
                ReturnReason.WRONG_ITEM,
                "<script>alert(1)</script> Wrong item",
                List.of("https://example.test/evidence.jpg")
        ));

        ArgumentCaptor<UserActivityEvent> eventCaptor = ArgumentCaptor.forClass(UserActivityEvent.class);
        verify(activityEventPublisher).publish(eventCaptor.capture());
        assertEquals("alert(1) Wrong item", response.buyerNote());
        assertEquals(ReturnStatus.REQUESTED.name(), response.status());
        assertEquals(ActivityType.RETURN, eventCaptor.getValue().activityType());
        assertEquals(productId, eventCaptor.getValue().productId());
        assertEquals(marketplaceId, eventCaptor.getValue().marketplaceId());
    }

    @Test
    void requestReturn_defectiveWithoutEvidenceThrowsBadRequest() {
        UUID buyerId = TestIds.uuid(1);
        UUID orderId = TestIds.uuid(2);
        UUID orderItemId = TestIds.uuid(3);

        User buyer = user(buyerId);
        Order order = order(orderId, buyer, OrderStatus.DELIVERED);
        order.setItems(List.of(orderItem(orderItemId, TestIds.uuid(4), TestIds.uuid(6), TestIds.uuid(5))));

        when(orderRepository.findByIdAndUserId(orderId, buyerId)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () -> service.requestReturn(orderId, buyerId, new BuyerInitiateReturnRequest(
                List.of(new BuyerReturnItemRequest(orderItemId, 1)),
                ReturnReason.DEFECTIVE,
                "Defective item",
                List.of()
        )));
    }

    @Test
    void getCompanyReturnsByOrder_filtersMixedCompanyReturns() {
        UUID companyId = TestIds.uuid(6);
        UUID ownerId = TestIds.uuid(7);
        UUID orderId = TestIds.uuid(8);

        Return scoped = returnRecord(TestIds.uuid(30), order(orderId, user(TestIds.uuid(1)), OrderStatus.DELIVERED));
        scoped.setItems(List.of(returnItem(TestIds.uuid(31), orderItem(TestIds.uuid(32), TestIds.uuid(33), companyId, TestIds.uuid(44)))));

        Return mixed = returnRecord(TestIds.uuid(40), order(orderId, user(TestIds.uuid(2)), OrderStatus.DELIVERED));
        mixed.setItems(List.of(
                returnItem(TestIds.uuid(41), orderItem(TestIds.uuid(42), TestIds.uuid(43), companyId, TestIds.uuid(44))),
                returnItem(TestIds.uuid(45), orderItem(TestIds.uuid(46), TestIds.uuid(47), TestIds.uuid(99), TestIds.uuid(44)))
        ));

        when(returnRepository.findAllByOrderIdAndCompanyId(orderId, companyId)).thenReturn(List.of(scoped, mixed));

        List<ReturnResponse> responses = service.getCompanyReturnsByOrder(orderId, companyId, ownerId);

        verify(companyAccessService).require(companyId, ownerId, backend.models.enums.CompanyCapability.FULFILL_ORDERS);
        assertEquals(1, responses.size());
        assertEquals(scoped.getId(), responses.get(0).id());
    }

    @Test
    void rejectReturn_setsRejectedStatusAndMerchantNote() {
        UUID companyId = TestIds.uuid(6);
        UUID ownerId = TestIds.uuid(7);
        UUID returnId = TestIds.uuid(8);

        Return ret = returnRecord(returnId, order(TestIds.uuid(9), user(TestIds.uuid(1)), OrderStatus.DELIVERED));
        ret.setStatus(ReturnStatus.REQUESTED);
        ret.setItems(List.of(returnItem(TestIds.uuid(10), orderItem(TestIds.uuid(11), TestIds.uuid(12), companyId, TestIds.uuid(13)))));

        when(returnRepository.findByIdAndCompanyId(returnId, companyId)).thenReturn(Optional.of(ret));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = service.rejectReturn(
                returnId,
                companyId,
                ownerId,
                new MerchantRejectReturnRequest("Not eligible")
        );

        assertEquals(ReturnStatus.REJECTED.name(), response.status());
        assertEquals("Not eligible", response.merchantNote());
    }

    @Test
    void handleRefundWebhookEvent_failedReversesProvisionalRefund() {
        UUID orderId = TestIds.uuid(50);
        Return ret = returnRecord(TestIds.uuid(51), order(orderId, user(TestIds.uuid(1)), OrderStatus.DELIVERED));
        ret.setStripeRefundId("re_123");
        ret.setRefundStatus(RefundStatus.PENDING);
        ret.setRefundedAmountCents(500L);
        ret.getOrder().setRefundedAmountCents(500L);

        when(returnRepository.findByStripeRefundId("re_123")).thenReturn(Optional.of(ret));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(ret.getOrder()));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleRefundWebhookEvent("re_123", "failed", 500L);

        verify(orderRepository).addRefundAmountDelta(orderId, -500L);
        assertEquals(RefundStatus.FAILED, ret.getRefundStatus());
        assertTrue(ret.getRefundFailureReason().contains("re_123"));
        assertEquals(OrderStatus.RETURNED, ret.getOrder().getStatus());
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user-" + id + "@example.com");
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    private Order order(UUID id, User user, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCurrency("USD");
        order.setRefundedAmountCents(0L);
        order.setItems(new ArrayList<>());
        return order;
    }

    private OrderItem orderItem(UUID orderItemId, UUID productId, UUID companyId, UUID marketplaceId) {
        Company company = new Company();
        company.setId(companyId);

        backend.models.core.Product product = new backend.models.core.Product();
        product.setId(productId);
        product.setCompany(company);
        product.setMarketplaceId(marketplaceId);
        product.setName("Product " + productId);

        OrderItem orderItem = new OrderItem();
        orderItem.setId(orderItemId);
        orderItem.setProduct(product);
        orderItem.setProductName(product.getName());
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(new BigDecimal("5.00"));
        orderItem.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        return orderItem;
    }

    private Return returnRecord(UUID returnId, Order order) {
        Return ret = new Return();
        ret.setId(returnId);
        ret.setOrder(order);
        ret.setStatus(ReturnStatus.REQUESTED);
        ret.setReason(ReturnReason.WRONG_ITEM);
        ret.setRefundStatus(RefundStatus.NONE);
        ret.setItems(new ArrayList<>());
        ret.setEvidenceUrls(new ArrayList<>());
        ret.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        ret.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return ret;
    }

    private ReturnItem returnItem(UUID returnItemId, OrderItem orderItem) {
        ReturnItem item = new ReturnItem();
        item.setId(returnItemId);
        item.setOrderItem(orderItem);
        item.setQuantityReturned(1);
        return item;
    }
}
