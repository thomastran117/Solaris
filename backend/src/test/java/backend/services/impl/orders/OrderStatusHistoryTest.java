package backend.services.impl.orders;

import backend.configurations.environment.RiskProperties;
import backend.dtos.responses.order.OrderStatusHistoryResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.core.OrderStatusHistory;
import backend.models.core.User;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.OrderHistoryEventType;
import backend.models.enums.OrderStatus;
import backend.repositories.*;
import backend.services.impl.inventory.StockAlertService;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.CacheService;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.auth.EmailVerificationService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.AllocationService;
import backend.services.intf.orders.OrderFulfillmentEventPublisher;
import backend.services.intf.orders.TrackingService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.pricing.CommissionEngine;
import backend.services.intf.pricing.PricingEngine;
import backend.services.intf.pricing.RiskEngine;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.support.EmailService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderStatusHistoryTest {

    private OrderRepository orderRepository;
    private OrderStatusHistoryRepository historyRepository;
    private OrderFulfillmentEventPublisher fulfillmentEventPublisher;
    private CompanyAccessService companyAccessService;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository          = mock(OrderRepository.class);
        historyRepository        = mock(OrderStatusHistoryRepository.class);
        fulfillmentEventPublisher = mock(OrderFulfillmentEventPublisher.class);
        companyAccessService     = mock(CompanyAccessService.class);

        service = new OrderServiceImpl(
                orderRepository,
                mock(OrderCompensationRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                mock(InventoryLocationRepository.class),
                mock(BundleRepository.class),
                mock(ProductKitRepository.class),
                mock(UserRepository.class),
                mock(CompanyRepository.class),
                mock(CouponRepository.class),
                mock(CouponRedemptionRepository.class),
                mock(CouponPerUserCountRepository.class),
                mock(PromotionRuleRepository.class),
                mock(PromotionRedemptionRepository.class),
                mock(PricingEngine.class),
                new backend.services.impl.pricing.TaxServiceImpl(mock(backend.repositories.TaxRateRepository.class), new java.math.BigDecimal("0.00"), false),
                mock(PaymentService.class),
                mock(CacheService.class),
                mock(StockAlertService.class),
                mock(EmailService.class),
                mock(AllocationService.class),
                mock(RiskEngine.class),
                mock(RiskAssessmentRepository.class),
                mock(RiskReviewRepository.class),
                mock(FailedPaymentAttemptRepository.class),
                mock(RiskProperties.class),
                mock(DeviceService.class),
                mock(EmailVerificationService.class),
                mock(MarketplaceVendorRepository.class),
                mock(SubOrderRepository.class),
                mock(OrderItemRepository.class),
                mock(CommissionEngine.class),
                mock(CommissionRecordRepository.class),
                mock(VendorBalanceRepository.class),
                mock(LoyaltyService.class),
                mock(ActivityEventPublisher.class),
                companyAccessService,
                fulfillmentEventPublisher,
                mock(TrackingService.class));

        service.setOrderStatusHistoryRepository(historyRepository);
    }

    // ── getOrderHistory ───────────────────────────────────────────────────────

    @Test
    void getOrderHistory_returnsEntriesMappedCorrectly() {
        UUID orderId = TestIds.uuid(1);
        UUID userId  = TestIds.uuid(2);
        UUID actorId = TestIds.uuid(3);

        Order order = makeOrder(orderId, makeUser(userId), OrderStatus.PAID);
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setId(TestIds.uuid(10));
        entry.setOrderId(orderId);
        entry.setEventType(OrderHistoryEventType.STATUS_CHANGED);
        entry.setStatus(OrderStatus.PAID);
        entry.setOccurredAt(Instant.parse("2024-01-01T12:00:00Z"));
        entry.setActorId(actorId);
        entry.setNote("Payment confirmed");
        when(historyRepository.findAllByOrderIdOrderByOccurredAtAsc(orderId)).thenReturn(List.of(entry));

        List<OrderStatusHistoryResponse> result = service.getOrderHistory(orderId, userId);

        assertEquals(1, result.size());
        OrderStatusHistoryResponse r = result.get(0);
        assertEquals(TestIds.uuid(10), r.id());
        assertEquals("STATUS_CHANGED", r.eventType());
        assertEquals("PAID", r.status());
        assertEquals(Instant.parse("2024-01-01T12:00:00Z"), r.occurredAt());
        assertEquals(actorId, r.actorId());
        assertEquals("Payment confirmed", r.note());
    }

    @Test
    void getOrderHistory_mapsNullStatusForNonStatusChangedEvent() {
        UUID orderId = TestIds.uuid(1);
        UUID userId  = TestIds.uuid(2);

        Order order = makeOrder(orderId, makeUser(userId), OrderStatus.SHIPPED);
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setId(TestIds.uuid(10));
        entry.setOrderId(orderId);
        entry.setEventType(OrderHistoryEventType.DRIVER_PICKED_UP);
        entry.setStatus(null);
        entry.setOccurredAt(Instant.now());
        when(historyRepository.findAllByOrderIdOrderByOccurredAtAsc(orderId)).thenReturn(List.of(entry));

        List<OrderStatusHistoryResponse> result = service.getOrderHistory(orderId, userId);

        assertEquals("DRIVER_PICKED_UP", result.get(0).eventType());
        assertNull(result.get(0).status());
    }

    @Test
    void getOrderHistory_throwsWhenOrderNotOwnedByUser() {
        UUID orderId = TestIds.uuid(1);
        UUID userId  = TestIds.uuid(2);

        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getOrderHistory(orderId, userId));
        verify(historyRepository, never()).findAllByOrderIdOrderByOccurredAtAsc(any());
    }

    @Test
    void getOrderHistory_returnsEmptyListWhenNoEntries() {
        UUID orderId = TestIds.uuid(1);
        UUID userId  = TestIds.uuid(2);

        Order order = makeOrder(orderId, makeUser(userId), OrderStatus.PAID);
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        when(historyRepository.findAllByOrderIdOrderByOccurredAtAsc(orderId)).thenReturn(List.of());

        List<OrderStatusHistoryResponse> result = service.getOrderHistory(orderId, userId);

        assertTrue(result.isEmpty());
    }

    // ── History recording — markAsPacked ─────────────────────────────────────

    @Test
    void markAsPacked_recordsStatusChangedHistoryWithOwnerId() {
        UUID companyId = TestIds.uuid(1);
        UUID orderId   = TestIds.uuid(2);
        UUID ownerId   = TestIds.uuid(3);

        Order order = makeOrder(orderId, makeUser(TestIds.uuid(9)), OrderStatus.PAID);
        when(orderRepository.findByIdAndProductCompanyId(orderId, companyId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markAsPacked(companyId, orderId, ownerId);

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        OrderStatusHistory recorded = captor.getValue();

        assertEquals(OrderHistoryEventType.STATUS_CHANGED, recorded.getEventType());
        assertEquals(OrderStatus.PACKED, recorded.getStatus());
        assertEquals(ownerId, recorded.getActorId());
        assertNull(recorded.getNote());
        assertEquals(orderId, recorded.getOrderId());
        assertNotNull(recorded.getOccurredAt());
    }

    // ── History recording — autoMarkDeliveredByTracking ───────────────────────

    @Test
    void autoMarkDeliveredByTracking_recordsCarrierDeliveredHistory() {
        UUID orderId = TestIds.uuid(1);
        Order order = makeOrder(orderId, makeUser(TestIds.uuid(9)), OrderStatus.SHIPPED);
        when(orderRepository.findByTrackingNumber("TRK-001")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.autoMarkDeliveredByTracking("TRK-001");

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        OrderStatusHistory recorded = captor.getValue();

        assertEquals(OrderHistoryEventType.STATUS_CHANGED, recorded.getEventType());
        assertEquals(OrderStatus.DELIVERED, recorded.getStatus());
        assertNull(recorded.getActorId());
        assertEquals("Delivery confirmed by carrier", recorded.getNote());
    }

    @Test
    void autoMarkDeliveredByTracking_doesNothingWhenAlreadyDelivered() {
        UUID orderId = TestIds.uuid(1);
        Order order = makeOrder(orderId, makeUser(TestIds.uuid(9)), OrderStatus.DELIVERED);
        when(orderRepository.findByTrackingNumber("TRK-002")).thenReturn(Optional.of(order));

        service.autoMarkDeliveredByTracking("TRK-002");

        verify(historyRepository, never()).save(any());
    }

    @Test
    void autoMarkDeliveredByTracking_doesNothingWhenOrderNotFound() {
        when(orderRepository.findByTrackingNumber("UNKNOWN")).thenReturn(Optional.empty());

        service.autoMarkDeliveredByTracking("UNKNOWN");

        verify(historyRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User makeUser(UUID id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Order makeOrder(UUID id, User user, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setUser(user);
        o.setStatus(status);
        o.setTotalAmount(BigDecimal.TEN);
        o.setCurrency("USD");
        o.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        o.setItems(new ArrayList<>());
        return o;
    }
}
