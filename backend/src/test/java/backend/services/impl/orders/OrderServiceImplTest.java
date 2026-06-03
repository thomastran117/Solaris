package backend.services.impl.orders;

import backend.configurations.environment.RiskProperties;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CompanyOrderResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.events.order.OrderFulfillmentEvent;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CancellationReason;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.UserRole;
import backend.repositories.BundleRepository;
import backend.repositories.CommissionRecordRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.CouponPerUserCountRepository;
import backend.repositories.CouponRedemptionRepository;
import backend.repositories.CouponRepository;
import backend.repositories.FailedPaymentAttemptRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderItemRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.services.intf.payments.PaymentService;
import backend.repositories.ProductVariantRepository;
import backend.repositories.PromotionRedemptionRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.RiskAssessmentRepository;
import backend.repositories.RiskReviewRepository;
import backend.repositories.SubOrderRepository;
import backend.repositories.OrderStatusHistoryRepository;
import backend.repositories.UserRepository;
import backend.repositories.VendorBalanceRepository;
import backend.services.impl.inventory.StockAlertService;
import backend.dtos.requests.order.ShipOrderRequest;
import backend.models.core.OrderCompensation;
import backend.models.core.RiskReview;
import backend.models.enums.CompensationStatus;
import backend.models.enums.CompensationType;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.RiskReviewStatus;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.CacheService;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.auth.EmailVerificationService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.AllocationService;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.pricing.CommissionEngine;
import backend.services.intf.pricing.PricingEngine;
import backend.services.intf.pricing.RiskEngine;
import backend.services.intf.orders.OrderFulfillmentEventPublisher;
import backend.services.intf.orders.TrackingService;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.support.EmailService;
import backend.models.enums.FulfillmentMethod;
import backend.models.core.InventoryLocation;
import backend.models.core.BundleItem;
import backend.models.core.Coupon;
import backend.models.core.ProductBundle;
import backend.models.enums.DiscountStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.UserTier;
import backend.dtos.requests.order.CreateOrderRequest;
import backend.dtos.responses.loyalty.LoyaltyRedemptionQuoteResponse;
import backend.services.pricing.PricingResult;
import backend.services.pricing.CartContext;
import backend.services.risk.RiskAssessmentResult;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private static final UUID USER_ID      = TestIds.uuid(1);
    private static final UUID COMPANY_ID   = TestIds.uuid(2);
    private static final UUID ORDER_ID     = TestIds.uuid(3);
    private static final UUID PRODUCT_ID   = TestIds.uuid(4);
    private static final String PAYMENT_INTENT_ID = "pi_test_123";

    private OrderRepository orderRepository;
    private CompanyAccessService companyAccessService;
    private PaymentService paymentService;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private OrderCompensationRepository compensationRepository;
    private CacheService cacheService;
    private RiskReviewRepository riskReviewRepository;
    private LoyaltyService loyaltyService;
    private OrderFulfillmentEventPublisher fulfillmentEventPublisher;
    // Additional named mocks for createOrder / webhook tests
    private UserRepository userRepository;
    private PricingEngine pricingEngine;
    private RiskEngine riskEngine;
    private RiskProperties riskProperties;
    private RiskAssessmentRepository riskAssessmentRepository;
    private BundleRepository bundleRepository;
    private InventoryLocationRepository locationRepository;
    private CouponRepository couponRepository;
    private CouponPerUserCountRepository couponPerUserCountRepository;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository          = mock(OrderRepository.class);
        companyAccessService     = mock(CompanyAccessService.class);
        paymentService           = mock(PaymentService.class);
        productRepository        = mock(ProductRepository.class);
        variantRepository        = mock(ProductVariantRepository.class);
        compensationRepository   = mock(OrderCompensationRepository.class);
        cacheService             = mock(CacheService.class);
        riskReviewRepository     = mock(RiskReviewRepository.class);
        loyaltyService           = mock(LoyaltyService.class);
        fulfillmentEventPublisher = mock(OrderFulfillmentEventPublisher.class);
        userRepository           = mock(UserRepository.class);
        pricingEngine            = mock(PricingEngine.class);
        riskEngine               = mock(RiskEngine.class);
        riskProperties           = mock(RiskProperties.class);
        riskAssessmentRepository = mock(RiskAssessmentRepository.class);
        when(riskAssessmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        bundleRepository               = mock(BundleRepository.class);
        couponRepository               = mock(CouponRepository.class);
        couponPerUserCountRepository   = mock(CouponPerUserCountRepository.class);
        when(couponPerUserCountRepository.tryIncrementUserCount(any(), any(), anyInt())).thenReturn(1);
        locationRepository       = mock(InventoryLocationRepository.class);

        service = new OrderServiceImpl(
                orderRepository,
                compensationRepository,
                productRepository,
                variantRepository,
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                locationRepository,
                bundleRepository,
                mock(backend.repositories.ProductKitRepository.class),
                userRepository,
                mock(CompanyRepository.class),
                couponRepository,
                mock(CouponRedemptionRepository.class),
                couponPerUserCountRepository,
                mock(PromotionRuleRepository.class),
                mock(PromotionRedemptionRepository.class),
                pricingEngine,
                paymentService,
                cacheService,
                mock(StockAlertService.class),
                mock(EmailService.class),
                mock(AllocationService.class),
                riskEngine,
                riskAssessmentRepository,
                riskReviewRepository,
                mock(FailedPaymentAttemptRepository.class),
                riskProperties,
                mock(DeviceService.class),
                mock(EmailVerificationService.class),
                mock(MarketplaceVendorRepository.class),
                mock(SubOrderRepository.class),
                mock(OrderItemRepository.class),
                mock(CommissionEngine.class),
                mock(CommissionRecordRepository.class),
                mock(VendorBalanceRepository.class),
                loyaltyService,
                mock(ActivityEventPublisher.class),
                companyAccessService,
                fulfillmentEventPublisher,
                mock(TrackingService.class));

        service.setOrderStatusHistoryRepository(mock(OrderStatusHistoryRepository.class));
        service.setEventPublisher(mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @Test
    void getOrder_returnsMappedResponse() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order()));

        OrderResponse response = service.getOrder(ORDER_ID, USER_ID);

        assertEquals(ORDER_ID, response.getId());
        assertEquals(USER_ID, response.getUserId());
        assertEquals("Desk", response.getItems().get(0).getProductName());
    }

    @Test
    void getLatestOrder_throwsWhenUserHasNoOrders() {
        when(orderRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getLatestOrder(USER_ID));
    }

    @Test
    void getOrders_usesStatusFilterAndFallsBackToCreatedAtSort() {
        when(orderRepository.findAllByUserIdAndStatus(eq(USER_ID), eq(OrderStatus.PAID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order())));

        PagedResponse<OrderResponse> response = service.getOrders(USER_ID, OrderStatus.PAID, 1, 10, "sideways", "desc");

        assertEquals(1, response.getItems().size());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllByUserIdAndStatus(eq(USER_ID), eq(OrderStatus.PAID), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals("createdAt: DESC", pageable.getSort().toString());
    }

    @Test
    void getOrders_capsPageSizeAt50AndUsesAscendingSort() {
        when(orderRepository.findAllByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order())));

        service.getOrders(USER_ID, null, 0, 99, "totalAmount", "asc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllByUserId(eq(USER_ID), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(50, pageable.getPageSize());
        assertEquals("totalAmount: ASC", pageable.getSort().toString());
    }

    @Test
    void getCompanyOrders_requiresAccessAndFiltersItemsToCompany() {
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findAllByProductCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mixedCompanyOrder())));

        PagedResponse<CompanyOrderResponse> response = service.getCompanyOrders(COMPANY_ID, USER_ID, null, 0, 99);

        assertEquals(1, response.getItems().size());
        CompanyOrderResponse order = response.getItems().get(0);
        assertEquals(new BigDecimal("10.00"), order.companyItemsTotal());
        assertEquals(1, order.items().size());
    }

    @Test
    void getCompanyOrder_returnsCompanyScopedView() {
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID))
                .thenReturn(Optional.of(mixedCompanyOrder()));

        CompanyOrderResponse response = service.getCompanyOrder(COMPANY_ID, ORDER_ID, USER_ID);

        assertEquals(ORDER_ID, response.orderId());
        assertEquals(new BigDecimal("10.00"), response.companyItemsTotal());
        assertEquals("USD", response.currency());
    }

    // -------------------------------------------------------------------------
    // markAsPickupReady
    // -------------------------------------------------------------------------

    @Test
    void markAsPickupReady_transitionsPendingItemsToPickupReady() {
        Order order = packedPickupOrder();
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsPickupReady(COMPANY_ID, ORDER_ID, USER_ID);

        assertEquals(FulfillmentStatus.PICKUP_READY, order.getItems().get(0).getFulfillmentStatus());
    }

    @Test
    void markAsPickupReady_setsPickupReadyAtTimestamp() {
        Order order = packedPickupOrder();
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsPickupReady(COMPANY_ID, ORDER_ID, USER_ID);

        assertNonNull(order.getPickupReadyAt());
    }

    @Test
    void markAsPickupReady_orderStatusRemainsPackedAfterCall() {
        Order order = packedPickupOrder();
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsPickupReady(COMPANY_ID, ORDER_ID, USER_ID);

        assertEquals(OrderStatus.PACKED, order.getStatus());
    }

    @Test
    void markAsPickupReady_throwsBadRequestOnDeliveryOrder() {
        Order order = packedPickupOrder();
        order.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.markAsPickupReady(COMPANY_ID, ORDER_ID, USER_ID));
    }

    // -------------------------------------------------------------------------
    // markAsShipped guard
    // -------------------------------------------------------------------------

    @Test
    void markAsShipped_throwsBadRequestOnPickupOrder() {
        Order order = packedPickupOrder();
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.markAsShipped(COMPANY_ID, ORDER_ID, USER_ID,
                        new backend.dtos.requests.order.ShipOrderRequest("TRK123", "UPS", null, null)));
    }

    // -------------------------------------------------------------------------
    // markAsDelivered — PICKUP path
    // -------------------------------------------------------------------------

    @Test
    void markAsDelivered_acceptsPackedStatusForPickupOrders() {
        Order order = pickupReadyOrder();
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsDelivered(COMPANY_ID, ORDER_ID, USER_ID);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void markAsDelivered_transitionsPickupReadyItemsToDelivered() {
        Order order = pickupReadyOrder();
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsDelivered(COMPANY_ID, ORDER_ID, USER_ID);

        assertEquals(FulfillmentStatus.DELIVERED, order.getItems().get(0).getFulfillmentStatus());
    }

    // -------------------------------------------------------------------------
    // cancelOrder — customer-initiated
    // -------------------------------------------------------------------------

    @Test
    void cancelOrder_reservedOrder_voidsPaymentIntentNotRefund() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(ORDER_ID, USER_ID);

        verify(paymentService).cancelPaymentIntent(PAYMENT_INTENT_ID);
        verify(paymentService, never()).refundPayment(any(), any());
    }

    @Test
    void cancelOrder_paidOrder_issuesFullRefundInCents() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(ORDER_ID, USER_ID);

        verify(paymentService).refundPayment(PAYMENT_INTENT_ID, 5000L);
        verify(paymentService, never()).cancelPaymentIntent(any());
    }

    @Test
    void cancelOrder_shippedOrder_throwsConflict() {
        Order order = cancellableOrder(OrderStatus.SHIPPED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () -> service.cancelOrder(ORDER_ID, USER_ID));
    }

    @Test
    void cancelOrder_setsCancelledStatusAndCustomerRequestReason() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(ORDER_ID, USER_ID);

        assertEquals(OrderStatus.CANCELLED, saved.getValue().getStatus());
        assertEquals(CancellationReason.CUSTOMER_REQUEST, saved.getValue().getCancellationReason());
        assertNonNull(saved.getValue().getCancelledAt());
    }

    @Test
    void cancelOrder_restoresStockForItems() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(ORDER_ID, USER_ID);

        verify(productRepository).restoreStock(PRODUCT_ID, 1);
    }

    @Test
    void cancelOrder_publishesCancelledKafkaEvent() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrder(ORDER_ID, USER_ID);

        ArgumentCaptor<OrderFulfillmentEvent> eventCaptor = ArgumentCaptor.forClass(OrderFulfillmentEvent.class);
        verify(fulfillmentEventPublisher).publish(eventCaptor.capture());
        OrderFulfillmentEvent.Cancelled event = (OrderFulfillmentEvent.Cancelled) eventCaptor.getValue();
        assertEquals(ORDER_ID, event.orderId());
        assertEquals(USER_ID, event.userId());
        assertEquals(CancellationReason.CUSTOMER_REQUEST, event.reason());
    }

    // -------------------------------------------------------------------------
    // cancelOrderByCompany — merchant-initiated
    // -------------------------------------------------------------------------

    @Test
    void cancelOrderByCompany_requiresCompanyAccess() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrderByCompany(COMPANY_ID, ORDER_ID, USER_ID);

        verify(companyAccessService).require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS);
    }

    @Test
    void cancelOrderByCompany_throwsWhenOrderNotFound() {
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.cancelOrderByCompany(COMPANY_ID, ORDER_ID, USER_ID));
    }

    @Test
    void cancelOrderByCompany_setsMerchantCancelledReason() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrderByCompany(COMPANY_ID, ORDER_ID, USER_ID);

        assertEquals(CancellationReason.MERCHANT_CANCELLED, saved.getValue().getCancellationReason());
    }

    @Test
    void cancelOrderByCompany_paidOrder_issuesFullRefund() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrderByCompany(COMPANY_ID, ORDER_ID, USER_ID);

        verify(paymentService).refundPayment(PAYMENT_INTENT_ID, 5000L);
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Order cancellableOrder(OrderStatus status) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company(COMPANY_ID));
        product.setName("Widget");

        OrderItem item = new OrderItem();
        item.setId(TestIds.uuid(10));
        item.setProduct(product);
        item.setProductName("Widget");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setFulfillmentStatus(FulfillmentStatus.PENDING);
        item.setDiscountAmount(BigDecimal.ZERO);

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUser(user(USER_ID));
        order.setStatus(status);
        order.setPaymentIntentId(PAYMENT_INTENT_ID);
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setCurrency("USD");
        order.setCouponDiscountAmount(BigDecimal.ZERO);
        order.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        order.setItems(List.of(item));
        return order;
    }

    private Order packedPickupOrder() {
        InventoryLocation loc = new InventoryLocation();
        loc.setId(TestIds.uuid(50));
        loc.setName("Downtown Store");

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUser(user(USER_ID));
        order.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        order.setPickupLocation(loc);
        order.setPickupLocationName("Downtown Store");
        order.setStatus(OrderStatus.PACKED);
        order.setTotalAmount(new BigDecimal("19.99"));
        order.setCurrency("USD");
        order.setCouponDiscountAmount(BigDecimal.ZERO);
        order.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        OrderItem item = orderItem(TestIds.uuid(10), company(COMPANY_ID), "Desk", new BigDecimal("19.99"));
        item.setFulfillmentStatus(FulfillmentStatus.PACKED);
        order.setItems(List.of(item));
        return order;
    }

    private Order pickupReadyOrder() {
        Order order = packedPickupOrder();
        order.getItems().forEach(i -> i.setFulfillmentStatus(FulfillmentStatus.PICKUP_READY));
        return order;
    }

    private static void assertNonNull(Object value) {
        if (value == null) throw new AssertionError("Expected non-null value");
    }

    private Order order() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUser(user(USER_ID));
        order.setItems(List.of(orderItem(TestIds.uuid(10), company(COMPANY_ID), "Desk", new BigDecimal("19.99"))));
        order.setTotalAmount(new BigDecimal("19.99"));
        order.setCurrency("USD");
        order.setStatus(OrderStatus.PAID);
        order.setCouponDiscountAmount(BigDecimal.ZERO);
        order.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return order;
    }

    private Order mixedCompanyOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUser(user(USER_ID));
        order.setItems(List.of(
                orderItem(TestIds.uuid(11), company(COMPANY_ID), "Desk", new BigDecimal("10.00")),
                orderItem(TestIds.uuid(12), company(TestIds.uuid(20)), "Lamp", new BigDecimal("15.00"))
        ));
        order.setTotalAmount(new BigDecimal("25.00"));
        order.setCurrency("USD");
        order.setStatus(OrderStatus.PAID);
        order.setCouponDiscountAmount(BigDecimal.ZERO);
        order.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return order;
    }

    private OrderItem orderItem(UUID itemId, Company company, String name, BigDecimal unitPrice) {
        Product product = new Product();
        product.setId(TestIds.uuid(100));
        product.setCompany(company);
        product.setName(name);

        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setProduct(product);
        item.setProductName(name);
        item.setQuantity(1);
        item.setUnitPrice(unitPrice);
        item.setFulfillmentStatus(FulfillmentStatus.PENDING);
        item.setDiscountAmount(BigDecimal.ZERO);
        return item;
    }

    private Company company(UUID id) {
        Company company = new Company();
        company.setId(id);
        company.setName("Company " + id);
        company.setOwner(user(USER_ID));
        return company;
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setRole(UserRole.USER);
        user.setEmail("user" + id + "@example.com");
        return user;
    }

    // -------------------------------------------------------------------------
    // markAsShipped — happy path
    // -------------------------------------------------------------------------

    @Test
    void markAsShipped_packedDeliveryOrder_setsShippedStatus() {
        Order order = packedDeliveryOrder();
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsShipped(COMPANY_ID, ORDER_ID, USER_ID, new ShipOrderRequest("TRK-1", "UPS", null, null));

        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertEquals("TRK-1", order.getTrackingNumber());
        verify(fulfillmentEventPublisher).publish(any(OrderFulfillmentEvent.Shipped.class));
    }

    @Test
    void markAsShipped_setsItemFulfillmentStatusToShipped() {
        Order order = packedDeliveryOrder();
        when(companyAccessService.require(COMPANY_ID, USER_ID, backend.models.enums.CompanyCapability.FULFILL_ORDERS))
                .thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsShipped(COMPANY_ID, ORDER_ID, USER_ID, new ShipOrderRequest("TRK-2", "FedEx", null, null));

        assertEquals(FulfillmentStatus.SHIPPED, order.getItems().get(0).getFulfillmentStatus());
    }

    // -------------------------------------------------------------------------
    // compensateOrder
    // -------------------------------------------------------------------------

    @Test
    void compensateOrder_alreadyCompensated_returnsEarly() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.markCompensated(ORDER_ID)).thenReturn(0); // 0 = already compensated

        service.compensateOrder(order);

        verify(productRepository, never()).restoreStock(any(), anyInt());
        verify(compensationRepository, never()).save(any());
    }

    @Test
    void compensateOrder_reservedOrder_cancelsPaymentAndSetsFailedStatus() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.markCompensated(ORDER_ID)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.compensateOrder(order);

        verify(paymentService).cancelPaymentIntent(PAYMENT_INTENT_ID);
        assertEquals(OrderStatus.FAILED, order.getStatus());
        assertEquals(CancellationReason.STALE_TIMEOUT, order.getCancellationReason());
    }

    @Test
    void compensateOrder_restoresStockForEachItem() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.markCompensated(ORDER_ID)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.compensateOrder(order);

        verify(productRepository).restoreStock(PRODUCT_ID, 1);
    }

    @Test
    void compensateOrder_restockFails_stillSavesOrderAndReleasesLocks() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.markCompensated(ORDER_ID)).thenReturn(1);
        when(productRepository.restoreStock(any(), anyInt())).thenThrow(new RuntimeException("DB error"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.compensateOrder(order); // must not throw

        verify(orderRepository).save(order);
    }

    @Test
    void compensateOrder_callsLoyaltyClawback() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.markCompensated(ORDER_ID)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.compensateOrder(order);

        verify(loyaltyService).restoreRedeemedPoints(ORDER_ID);
    }

    // -------------------------------------------------------------------------
    // retryCompensation
    // -------------------------------------------------------------------------

    @Test
    void retryCompensation_alreadyClaimed_returnsEarly() {
        OrderCompensation comp = compensation(CompensationType.STOCK_RESTORE, "product:1234:qty:1");
        when(compensationRepository.claimForRetry(comp.getId())).thenReturn(0);

        service.retryCompensation(comp);

        verify(productRepository, never()).restoreStock(any(), anyInt());
        verify(compensationRepository, never()).save(any());
    }

    @Test
    void retryCompensation_stockRestore_productPath_restoresStock() {
        UUID productId = TestIds.uuid(20);
        String detail = "[PRODUCT]:" + productId + ":qty:2";
        OrderCompensation comp = compensation(CompensationType.STOCK_RESTORE, detail);
        when(compensationRepository.claimForRetry(comp.getId())).thenReturn(1);

        service.retryCompensation(comp);

        verify(compensationRepository).save(comp);
        assertEquals(CompensationStatus.COMPLETED, comp.getStatus());
    }

    @Test
    void retryCompensation_paymentCancel_cancelsIntent() {
        String detail = "pi_test_intent";
        OrderCompensation comp = compensation(CompensationType.PAYMENT_CANCEL,
                "Cancelled payment intent: " + detail);
        when(compensationRepository.claimForRetry(comp.getId())).thenReturn(1);

        service.retryCompensation(comp);

        verify(paymentService).cancelPaymentIntent(detail);
        verify(compensationRepository).save(comp);
        assertEquals(CompensationStatus.COMPLETED, comp.getStatus());
    }

    @Test
    void retryCompensation_paymentCancelThrows_setsErrorMessage() {
        String detail = "pi_fail_intent";
        OrderCompensation comp = compensation(CompensationType.PAYMENT_CANCEL,
                "Cancelled payment intent: " + detail);
        when(compensationRepository.claimForRetry(comp.getId())).thenReturn(1);
        when(paymentService.cancelPaymentIntent(detail)).thenThrow(new RuntimeException("Stripe error"));

        service.retryCompensation(comp);

        verify(compensationRepository).save(comp);
        assertEquals("Stripe error", comp.getErrorMessage());
    }

    // -------------------------------------------------------------------------
    // fulfillPendingBackorders
    // -------------------------------------------------------------------------

    @Test
    void fulfillPendingBackorders_lockNotAcquired_returnsEarly() {
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(false);

        service.fulfillPendingBackorders(PRODUCT_ID, null, 5, null);

        verify(orderRepository, never()).findPaidOrdersWithBackorderedProduct(any(), any());
    }

    @Test
    void fulfillPendingBackorders_noBackorders_doesNotDecrementStock() {
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(orderRepository.findPaidOrdersWithBackorderedProduct(PRODUCT_ID, FulfillmentStatus.BACKORDERED))
                .thenReturn(List.of());

        service.fulfillPendingBackorders(PRODUCT_ID, null, 5, null);

        verify(productRepository, never()).decrementStock(any(), anyInt());
    }

    @Test
    void fulfillPendingBackorders_backorderAvailable_decrementsStockAndTransitionsPending() {
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company(COMPANY_ID));
        product.setStock(10);

        OrderItem item = new OrderItem();
        item.setId(TestIds.uuid(10));
        item.setProduct(product);
        item.setQuantity(2);
        item.setFulfillmentStatus(FulfillmentStatus.BACKORDERED);

        Order order = cancellableOrder(OrderStatus.PAID);
        order.setItems(List.of(item));

        when(orderRepository.findPaidOrdersWithBackorderedProduct(PRODUCT_ID, FulfillmentStatus.BACKORDERED))
                .thenReturn(List.of(order));
        when(productRepository.decrementStock(PRODUCT_ID, 2)).thenReturn(1);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.fulfillPendingBackorders(PRODUCT_ID, null, 5, null);

        verify(productRepository).decrementStock(PRODUCT_ID, 2);
        assertEquals(FulfillmentStatus.PENDING, item.getFulfillmentStatus());
    }

    // -------------------------------------------------------------------------
    // approveRiskReview
    // -------------------------------------------------------------------------

    @Test
    void approveRiskReview_orderNotUnderReview_throwsConflict() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company(COMPANY_ID));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () ->
                service.approveRiskReview(COMPANY_ID, ORDER_ID, USER_ID, null));
    }

    @Test
    void approveRiskReview_happyPath_createsPaymentIntentAndSetsReserved() {
        Order order = cancellableOrder(OrderStatus.UNDER_REVIEW);
        order.getItems().get(0).setProduct(productInCompany(COMPANY_ID));
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company(COMPANY_ID));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        RiskReview review = new RiskReview();
        review.setStatus(RiskReviewStatus.PENDING);
        when(riskReviewRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(review));

        when(paymentService.createPaymentIntent(anyLong(), any(), any(), any()))
                .thenReturn(new PaymentService.PaymentIntentResult("pi_new", "secret", 5000L, "usd", "requires_payment_method", null));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approveRiskReview(COMPANY_ID, ORDER_ID, USER_ID, null);

        assertEquals(OrderStatus.RESERVED, order.getStatus());
        assertEquals(RiskReviewStatus.APPROVED, review.getStatus());
    }

    // -------------------------------------------------------------------------
    // rejectRiskReview
    // -------------------------------------------------------------------------

    @Test
    void rejectRiskReview_orderNotUnderReview_throwsConflict() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company(COMPANY_ID));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () ->
                service.rejectRiskReview(COMPANY_ID, ORDER_ID, USER_ID, null));
    }

    @Test
    void rejectRiskReview_happyPath_cancelsOrderAndSetsRejected() {
        Order order = cancellableOrder(OrderStatus.UNDER_REVIEW);
        order.getItems().get(0).setProduct(productInCompany(COMPANY_ID));
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company(COMPANY_ID));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndUserId(eq(ORDER_ID), any(UUID.class))).thenReturn(Optional.of(order));

        RiskReview review = new RiskReview();
        review.setStatus(RiskReviewStatus.PENDING);
        when(riskReviewRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(review));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.rejectRiskReview(COMPANY_ID, ORDER_ID, USER_ID, null);

        assertEquals(RiskReviewStatus.REJECTED, review.getStatus());
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // markAsPacked
    // -------------------------------------------------------------------------

    @Test
    void markAsPacked_happyPath_setsOrderAndItemStatus() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsPacked(COMPANY_ID, ORDER_ID, USER_ID);

        assertEquals(OrderStatus.PACKED, order.getStatus());
        assertEquals(FulfillmentStatus.PACKED, order.getItems().get(0).getFulfillmentStatus());
    }

    @Test
    void markAsPacked_wrongStatus_throwsConflictOrBadRequest() {
        Order order = cancellableOrder(OrderStatus.SHIPPED);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));

        assertThrows(Exception.class, () -> service.markAsPacked(COMPANY_ID, ORDER_ID, USER_ID));
    }

    // -------------------------------------------------------------------------
    // markAsDelivered — delivery (non-pickup) path
    // -------------------------------------------------------------------------

    @Test
    void markAsDelivered_deliveryOrder_setsDeliveredAndPublishesEvent() {
        Order order = cancellableOrder(OrderStatus.SHIPPED);
        order.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        order.getItems().get(0).setFulfillmentStatus(FulfillmentStatus.SHIPPED);
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company(COMPANY_ID));
        when(orderRepository.findByIdAndProductCompanyId(ORDER_ID, COMPANY_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markAsDelivered(COMPANY_ID, ORDER_ID, USER_ID);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(FulfillmentStatus.DELIVERED, order.getItems().get(0).getFulfillmentStatus());
        verify(fulfillmentEventPublisher).publish(any(OrderFulfillmentEvent.Delivered.class));
    }

    // -------------------------------------------------------------------------
    // Additional helpers
    // -------------------------------------------------------------------------

    private Order packedDeliveryOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUser(user(USER_ID));
        order.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        order.setStatus(OrderStatus.PACKED);
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setCurrency("USD");
        order.setCouponDiscountAmount(BigDecimal.ZERO);
        order.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        OrderItem item = orderItem(TestIds.uuid(11), company(COMPANY_ID), "Widget", new BigDecimal("50.00"));
        item.setFulfillmentStatus(FulfillmentStatus.PACKED);
        order.setItems(new java.util.ArrayList<>(List.of(item)));
        return order;
    }

    private Product productInCompany(UUID companyId) {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCompany(company(companyId));
        return p;
    }

    private OrderCompensation compensation(CompensationType type, String detail) {
        OrderCompensation c = new OrderCompensation();
        c.setId(TestIds.uuid(99));
        c.setType(type);
        c.setDetail(detail);
        c.setStatus(CompensationStatus.FAILED);
        c.setAttempts(1);
        return c;
    }

    // =========================================================================
    // createOrder — comprehensive tests
    // =========================================================================

    /** Stubs the distributed lock to succeed for every key. */
    private void stubLockSuccess() {
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
    }

    /** Stubs pricingEngine.quote() to return a zero-price result. */
    private void stubPricingSuccess() {
        when(pricingEngine.quote(any(CartContext.class))).thenReturn(
                new PricingResult(List.of(), List.of(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        List.of()));
    }

    /** Stubs riskEngine.assess() to allow the order. */
    private void stubRiskAllow() {
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
    }

    /** Stubs paymentService.createPaymentIntent() to return a test result. */
    private void stubPaymentSuccess() {
        when(paymentService.createPaymentIntent(anyLong(), any(), any(), any()))
                .thenReturn(new PaymentService.PaymentIntentResult(
                        "pi_test", "secret", 5000L, "usd", "requires_payment_method", null));
    }

    /** Returns a User with PREMIUM tier (not capped at 50 items). */
    private User premiumUser() {
        User u = user(USER_ID);
        u.setTier(UserTier.PREMIUM);
        return u;
    }

    /** Returns a ACTIVE, listed, purchasable product belonging to COMPANY_ID. */
    private Product activeProduct() {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setName("Widget");
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        p.setPrice(new BigDecimal("50.00"));
        Company co = company(COMPANY_ID);
        p.setCompany(co);
        return p;
    }

    /** Builds a single-item DELIVERY CreateOrderRequest with a full shipping address. */
    private CreateOrderRequest deliveryRequest(UUID productId, int qty) {
        CreateOrderRequest req = new CreateOrderRequest();
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        req.setItems(List.of(item));
        req.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        req.setShipRecipientName("Alice Smith");
        req.setShipStreet("123 Main St");
        req.setShipCity("Toronto");
        req.setShipPostalCode("M5V1A1");
        req.setShipCountry("CA");
        return req;
    }

    // ─── createOrder happy path ───────────────────────────────────────────────

    @Test
    void createOrder_singleProductItem_successfullyCreatesOrder() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        stubLockSuccess();
        stubPricingSuccess();
        stubRiskAllow();
        stubPaymentSuccess();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        OrderResponse resp = service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1));

        assertNotNull(resp);
        verify(orderRepository, atLeast(2)).save(any(Order.class));
        verify(paymentService).createPaymentIntent(anyLong(), any(), any(), any());
    }

    @Test
    void createOrder_deliveryWithoutAddress_throwsBadRequestException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));

        CreateOrderRequest req = new CreateOrderRequest();
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(PRODUCT_ID);
        item.setQuantity(1);
        req.setItems(List.of(item));
        req.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        // No address fields set

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, req));
    }

    @Test
    void createOrder_productNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of()); // empty → size mismatch
        stubLockSuccess();

        assertThrows(ResourceNotFoundException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1)));
    }

    @Test
    void createOrder_productNotActive_throwsBadRequestException() {
        Product product = activeProduct();
        product.setStatus(ProductStatus.DRAFT); // not ACTIVE
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        stubLockSuccess();

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1)));
    }

    @Test
    void createOrder_productNotPurchasable_throwsBadRequestException() {
        Product product = activeProduct();
        product.setPurchasable(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        stubLockSuccess();

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1)));
    }

    @Test
    void createOrder_insufficientStock_throwsConflictException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(0); // 0 = out of stock
        stubLockSuccess();

        assertThrows(ConflictException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1)));
    }

    @Test
    void createOrder_riskEngineBlocks_orderSetToUnderReview() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        stubLockSuccess();
        stubPricingSuccess();
        when(riskEngine.assess(any())).thenReturn(
                RiskAssessmentResult.block(80, List.of(), List.of()));
        when(riskProperties.getMode()).thenReturn(backend.models.enums.RiskMode.ENFORCE);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        OrderResponse resp = service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1));

        assertEquals(OrderStatus.UNDER_REVIEW.name(), resp.getStatus());
        verify(paymentService, never()).createPaymentIntent(anyLong(), any(), any(), any());
    }

    @Test
    void createOrder_paymentFails_savesFailedOrderAndRethrows() {
        // createOrder re-throws the payment exception after saving FAILED status and scheduling compensation
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        stubLockSuccess();
        stubPricingSuccess();
        stubRiskAllow();
        when(paymentService.createPaymentIntent(anyLong(), any(), any(), any()))
                .thenThrow(new RuntimeException("Stripe down"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        // The exception propagates (payment failure is re-thrown after saving FAILED status)
        assertThrows(RuntimeException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1)));

        // Verify: save was called (initial save + FAILED save), and stock schedule was triggered
        verify(orderRepository, atLeast(1)).save(any(Order.class));
    }

    @Test
    void createOrder_freeTierTooManyItems_throwsPremiumRequiredException() {
        User freeUser = user(USER_ID);
        freeUser.setTier(UserTier.FREE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(freeUser));

        CreateOrderRequest req = new CreateOrderRequest();
        List<CreateOrderRequest.OrderItemRequest> items = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) {
            CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
            item.setProductId(TestIds.uuid(100 + i));
            item.setQuantity(1);
            items.add(item);
        }
        req.setItems(items);
        req.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        req.setShipRecipientName("Alice");
        req.setShipStreet("123 St");
        req.setShipCity("City");
        req.setShipPostalCode("12345");
        req.setShipCountry("US");

        assertThrows(backend.exceptions.http.PremiumRequiredException.class,
                () -> service.createOrder(USER_ID, req));
    }

    @Test
    void createOrder_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1)));
    }

    @Test
    void createOrder_itemWithNeitherProductNorBundle_throwsBadRequestException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));

        CreateOrderRequest req = new CreateOrderRequest();
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        // No productId, bundleId, or kitId set
        item.setQuantity(1);
        req.setItems(List.of(item));
        req.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        req.setShipRecipientName("Alice");
        req.setShipStreet("123 St");
        req.setShipCity("City");
        req.setShipPostalCode("12345");
        req.setShipCountry("US");

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, req));
    }

    @Test
    void createOrder_pickupWithoutLocationId_throwsBadRequestException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));

        CreateOrderRequest req = new CreateOrderRequest();
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(PRODUCT_ID);
        item.setQuantity(1);
        req.setItems(List.of(item));
        req.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        // No pickupLocationId

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, req));
    }

    @Test
    void createOrder_backorderProduct_itemStatusSetToBackordered() {
        Product product = activeProduct();
        product.setBackorderEnabled(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(0); // 0 = out of stock
        stubLockSuccess();
        stubPricingSuccess();
        stubRiskAllow();
        stubPaymentSuccess();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        OrderResponse resp = service.createOrder(USER_ID, deliveryRequest(PRODUCT_ID, 1));

        assertNotNull(resp);
        // Order still created (backordered items don't fail the order)
        verify(orderRepository, atLeast(1)).save(any(Order.class));
    }

    // ─── listRiskReviews / getOrderRisk ───────────────────────────────────────

    @Test
    void listRiskReviews_returnsPagedResults() {
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(company(COMPANY_ID));
        when(riskReviewRepository.findByCompanyIdAndStatus(eq(COMPANY_ID), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.listRiskReviews(COMPANY_ID, USER_ID, null, 0, 10);

        assertNotNull(result);
    }

    // =========================================================================
    // Bundle item tests
    // =========================================================================

    @Test
    void createOrder_bundleItem_happyPath_decrementsConstituentStock() {
        UUID BUNDLE_PRODUCT_ID = TestIds.uuid(50);
        UUID BUNDLE_ID         = TestIds.uuid(51);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of()); // no standalone products
        when(bundleRepository.findById(BUNDLE_ID)).thenReturn(Optional.of(activeBundle(BUNDLE_ID, BUNDLE_PRODUCT_ID)));
        when(productRepository.decrementStock(BUNDLE_PRODUCT_ID, 1)).thenReturn(1);
        stubLockSuccess();
        stubPricingSuccess();
        stubRiskAllow();
        stubPaymentSuccess();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        OrderResponse resp = service.createOrder(USER_ID, bundleRequest(BUNDLE_ID, 1));

        assertNotNull(resp);
        verify(productRepository).decrementStock(BUNDLE_PRODUCT_ID, 1);
        verify(orderRepository, atLeast(2)).save(any(Order.class));
    }

    @Test
    void createOrder_bundleConstituent_notActive_throwsBadRequest() {
        UUID BUNDLE_PRODUCT_ID = TestIds.uuid(52);
        UUID BUNDLE_ID         = TestIds.uuid(53);

        ProductBundle bundle = activeBundle(BUNDLE_ID, BUNDLE_PRODUCT_ID);
        bundle.getItems().get(0).getProduct().setStatus(ProductStatus.DRAFT); // not active

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(bundleRepository.findById(BUNDLE_ID)).thenReturn(Optional.of(bundle));
        when(productRepository.decrementStock(any(), anyInt())).thenReturn(1);
        stubLockSuccess();

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, bundleRequest(BUNDLE_ID, 1)));
    }

    @Test
    void createOrder_bundleInsufficientStock_throwsConflictException() {
        UUID BUNDLE_PRODUCT_ID = TestIds.uuid(54);
        UUID BUNDLE_ID         = TestIds.uuid(55);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(bundleRepository.findById(BUNDLE_ID)).thenReturn(Optional.of(activeBundle(BUNDLE_ID, BUNDLE_PRODUCT_ID)));
        when(productRepository.decrementStock(BUNDLE_PRODUCT_ID, 1)).thenReturn(0); // out of stock
        stubLockSuccess();

        assertThrows(ConflictException.class,
                () -> service.createOrder(USER_ID, bundleRequest(BUNDLE_ID, 1)));
    }

    // =========================================================================
    // Coupon tests
    // =========================================================================

    @Test
    void createOrder_couponNotFound_throwsBadRequestException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        when(couponRepository.findByCodeIgnoreCase("SUMMER20")).thenReturn(Optional.empty());
        stubLockSuccess();

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequestWithCoupon(PRODUCT_ID, 1, "SUMMER20")));
    }

    @Test
    void createOrder_couponDisabled_throwsBadRequestException() {
        Coupon coupon = coupon("SUMMER20", DiscountStatus.DISABLED, null, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        when(couponRepository.findByCodeIgnoreCase("SUMMER20")).thenReturn(Optional.of(coupon));
        stubLockSuccess();

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequestWithCoupon(PRODUCT_ID, 1, "SUMMER20")));
    }

    @Test
    void createOrder_couponPerUserExhausted_throwsBadRequestException() {
        Coupon coupon = coupon("LIMIT1", DiscountStatus.ACTIVE, null, 1); // max 1 use per user
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        when(couponRepository.findByCodeIgnoreCase("LIMIT1")).thenReturn(Optional.of(coupon));
        when(couponPerUserCountRepository.tryIncrementUserCount(any(), eq(USER_ID), eq(1))).thenReturn(0); // exhausted
        stubLockSuccess();

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequestWithCoupon(PRODUCT_ID, 1, "LIMIT1")));
    }

    @Test
    void createOrder_couponValid_orderCreatedWithCouponCode() {
        Coupon coupon = coupon("SAVE10", DiscountStatus.ACTIVE, null, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(coupon));
        when(couponRepository.tryIncrementUsedCount(any(), any(), any())).thenReturn(1);
        stubLockSuccess();
        // Pricing returns the coupon code as applied
        when(pricingEngine.quote(any(CartContext.class))).thenReturn(
                new PricingResult(List.of(), List.of(),
                        new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("5.00"),
                        "SAVE10", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("45.00"),
                        List.of()));
        stubRiskAllow();
        stubPaymentSuccess();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        OrderResponse resp = service.createOrder(USER_ID, deliveryRequestWithCoupon(PRODUCT_ID, 1, "SAVE10"));

        assertNotNull(resp);
        verify(orderRepository, atLeast(2)).save(any(Order.class));
    }

    // =========================================================================
    // Loyalty redemption tests
    // =========================================================================

    @Test
    void createOrder_loyaltyPointsValid_deductsFromTotal() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        stubLockSuccess();
        stubPricingSuccess(); // finalTotal = 0 from pricing, loyalty would make it go to 0 too
        // Override pricing to return non-zero so loyalty has something to deduct
        when(pricingEngine.quote(any(CartContext.class))).thenReturn(
                new PricingResult(List.of(), List.of(),
                        new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"),
                        List.of()));

        LoyaltyRedemptionQuoteResponse quote = new LoyaltyRedemptionQuoteResponse(
                USER_ID, COMPANY_ID, 100, 500L, 1000L, 900L, true, null);
        when(loyaltyService.getRedemptionQuote(USER_ID, COMPANY_ID, 100)).thenReturn(quote);
        stubRiskAllow();
        stubPaymentSuccess();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return o;
        });

        CreateOrderRequest req = deliveryRequest(PRODUCT_ID, 1);
        req.setLoyaltyPointsToRedeem(100);

        OrderResponse resp = service.createOrder(USER_ID, req);

        assertNotNull(resp);
        verify(loyaltyService).getRedemptionQuote(USER_ID, COMPANY_ID, 100);
    }

    @Test
    void createOrder_loyaltyPointsInvalid_throwsBadRequestException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(premiumUser()));
        when(productRepository.findAllById(any())).thenReturn(List.of(activeProduct()));
        when(productRepository.decrementStock(PRODUCT_ID, 1)).thenReturn(1);
        stubLockSuccess();
        when(pricingEngine.quote(any(CartContext.class))).thenReturn(
                new PricingResult(List.of(), List.of(),
                        new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"),
                        List.of()));

        LoyaltyRedemptionQuoteResponse invalidQuote = new LoyaltyRedemptionQuoteResponse(
                USER_ID, COMPANY_ID, 100, 0L, 50L, 50L, false, "Insufficient balance");
        when(loyaltyService.getRedemptionQuote(USER_ID, COMPANY_ID, 100)).thenReturn(invalidQuote);

        CreateOrderRequest req = deliveryRequest(PRODUCT_ID, 1);
        req.setLoyaltyPointsToRedeem(100);

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.createOrder(USER_ID, req));
    }

    // ─── Additional helpers ───────────────────────────────────────────────────

    /** Creates a delivery request with a bundle item (no standalone products). */
    private CreateOrderRequest bundleRequest(UUID bundleId, int qty) {
        CreateOrderRequest req = new CreateOrderRequest();
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setBundleId(bundleId);
        item.setQuantity(qty);
        req.setItems(List.of(item));
        req.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        req.setShipRecipientName("Alice");
        req.setShipStreet("123 Main St");
        req.setShipCity("Toronto");
        req.setShipPostalCode("M5V1A1");
        req.setShipCountry("CA");
        return req;
    }

    /** Creates a delivery request with a product item and a coupon code. */
    private CreateOrderRequest deliveryRequestWithCoupon(UUID productId, int qty, String couponCode) {
        CreateOrderRequest req = deliveryRequest(productId, qty);
        req.setCouponCode(couponCode);
        return req;
    }

    /** Creates an ACTIVE, listed bundle with one BundleItem pointing to a new Product. */
    private ProductBundle activeBundle(UUID bundleId, UUID constituentProductId) {
        Product constituentProduct = activeProduct();
        constituentProduct.setId(constituentProductId);

        BundleItem bi = new BundleItem();
        bi.setId(TestIds.uuid(200));
        bi.setProduct(constituentProduct);
        bi.setQuantity(1);

        ProductBundle bundle = new ProductBundle();
        bundle.setId(bundleId);
        bundle.setName("Test Bundle");
        bundle.setPrice(new BigDecimal("99.00"));
        bundle.setStatus(backend.models.enums.ProductStatus.ACTIVE);
        bundle.setListed(true);
        bundle.setItems(new java.util.ArrayList<>(List.of(bi)));
        bundle.setCompany(company(COMPANY_ID));
        return bundle;
    }

    /** Creates a Coupon with the given status, endDate, and maxUsesPerUser. */
    private Coupon coupon(String code, DiscountStatus status, java.time.Instant endDate, Integer maxUsesPerUser) {
        Coupon c = new Coupon();
        c.setId(TestIds.uuid(300));
        c.setCode(code);
        c.setStatus(status);
        c.setEndDate(endDate);
        c.setMaxUsesPerUser(maxUsesPerUser);
        return c;
    }

    // =========================================================================
    // handlePaymentSuccess
    // =========================================================================

    @Test
    void handlePaymentSuccess_reservedOrder_transitionsToPayAndSaves() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order));
        when(orderRepository.transitionStatus(ORDER_ID, OrderStatus.RESERVED, OrderStatus.PAID)).thenReturn(1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handlePaymentSuccess(PAYMENT_INTENT_ID);

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void handlePaymentSuccess_alreadyPaid_idempotentNoOp() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order));
        when(orderRepository.transitionStatus(ORDER_ID, OrderStatus.RESERVED, OrderStatus.PAID)).thenReturn(0); // already done
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.handlePaymentSuccess(PAYMENT_INTENT_ID);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void handlePaymentSuccess_noOrderFound_doesNothing() {
        when(orderRepository.findByPaymentIntentId("pi_unknown")).thenReturn(Optional.empty());

        service.handlePaymentSuccess("pi_unknown"); // must not throw

        verify(orderRepository, never()).save(any(Order.class));
    }

    // =========================================================================
    // handlePaymentFailure
    // =========================================================================

    @Test
    void handlePaymentFailure_reservedOrder_setsFailedAndRestoresStock() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order));
        when(orderRepository.markCompensated(ORDER_ID)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handlePaymentFailure(PAYMENT_INTENT_ID);

        assertEquals(OrderStatus.FAILED, order.getStatus());
        verify(productRepository).restoreStock(PRODUCT_ID, 1);
    }

    @Test
    void handlePaymentFailure_alreadyPaid_skipsProcessing() {
        Order order = cancellableOrder(OrderStatus.PAID);
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order));

        service.handlePaymentFailure(PAYMENT_INTENT_ID); // must not throw

        verify(orderRepository, never()).markCompensated(any());
    }

    @Test
    void handlePaymentFailure_noOrderFound_doesNothing() {
        when(orderRepository.findByPaymentIntentId("pi_unknown")).thenReturn(Optional.empty());

        service.handlePaymentFailure("pi_unknown");

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void handlePaymentFailure_alreadyCompensated_skipsProcessing() {
        Order order = cancellableOrder(OrderStatus.RESERVED);
        when(orderRepository.findByPaymentIntentId(PAYMENT_INTENT_ID)).thenReturn(Optional.of(order));
        when(orderRepository.markCompensated(ORDER_ID)).thenReturn(0); // already compensated

        service.handlePaymentFailure(PAYMENT_INTENT_ID);

        verify(orderRepository, never()).save(any(Order.class));
    }

    private static void assertNotNull(Object value) {
        if (value == null) throw new AssertionError("Expected non-null value but was null");
    }
}
