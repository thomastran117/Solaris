package backend.services.impl.orders;

import backend.dtos.requests.order.CancelSubOrderRequest;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.Company;
import backend.models.core.MarketplaceVendor;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.SubOrder;
import backend.models.core.User;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.SubOrderStatus;
import backend.models.enums.UserRole;
import backend.repositories.CommissionRecordRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.OrderItemRepository;
import backend.repositories.SubOrderRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubOrderServiceImplTest {

    private SubOrderRepository subOrderRepository;
    private OrderItemRepository orderItemRepository;
    private CommissionRecordRepository commissionRecordRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private SubOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        subOrderRepository         = mock(SubOrderRepository.class);
        orderItemRepository        = mock(OrderItemRepository.class);
        commissionRecordRepository = mock(CommissionRecordRepository.class);
        marketplaceVendorRepository = mock(MarketplaceVendorRepository.class);
        service = new SubOrderServiceImpl(
                subOrderRepository,
                orderItemRepository,
                commissionRecordRepository,
                marketplaceVendorRepository);
    }

    @Test
    void markPacked_throwsWhenActorDoesNotOwnVendor() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));

        assertThrows(ForbiddenException.class,
                () -> service.markPacked(TestIds.uuid(100), TestIds.uuid(7), TestIds.uuid(99)));
    }

    @Test
    void cancelSubOrder_marksPendingPackAndBackorderItemsCancelled() {
        MarketplaceVendor vendor  = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        SubOrder subOrder         = makeSubOrder(TestIds.uuid(100), vendor, SubOrderStatus.PACKED);
        OrderItem pending         = makeOrderItem(TestIds.uuid(1), FulfillmentStatus.PENDING);
        OrderItem packed          = makeOrderItem(TestIds.uuid(2), FulfillmentStatus.PACKED);
        OrderItem backordered     = makeOrderItem(TestIds.uuid(3), FulfillmentStatus.BACKORDERED);

        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByIdAndMarketplaceVendorId(TestIds.uuid(100), TestIds.uuid(7)))
                .thenReturn(Optional.of(subOrder));
        when(orderItemRepository.findAllBySubOrderId(TestIds.uuid(100)))
                .thenReturn(List.of(pending, packed, backordered));
        when(subOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CancelSubOrderRequest request = new CancelSubOrderRequest();
        request.setReason("Customer requested cancellation");

        service.cancelSubOrder(TestIds.uuid(100), TestIds.uuid(7), request, TestIds.uuid(10));

        assertEquals(SubOrderStatus.CANCELLED, subOrder.getStatus());
        assertEquals(FulfillmentStatus.CANCELLED, pending.getFulfillmentStatus());
        assertEquals(FulfillmentStatus.CANCELLED, packed.getFulfillmentStatus());
        assertEquals(FulfillmentStatus.CANCELLED, backordered.getFulfillmentStatus());

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository, times(3)).save(itemCaptor.capture());
        itemCaptor.getAllValues().forEach(item ->
                assertEquals(FulfillmentStatus.CANCELLED, item.getFulfillmentStatus()));
    }

    // ─── listVendorSubOrders ──────────────────────────────────────────────────

    @Test
    void listVendorSubOrders_withStatus_queriesByStatus() {
        UUID vendorId = TestIds.uuid(7);
        UUID ownerId  = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByMarketplaceVendorIdAndStatus(
                eq(vendorId), eq(SubOrderStatus.PENDING), any())).thenReturn(new PageImpl<>(List.of()));

        service.listVendorSubOrders(vendorId, SubOrderStatus.PENDING, 0, 10, ownerId);

        verify(subOrderRepository).findByMarketplaceVendorIdAndStatus(eq(vendorId), eq(SubOrderStatus.PENDING), any());
    }

    @Test
    void listVendorSubOrders_noStatus_queriesAll() {
        UUID vendorId = TestIds.uuid(7);
        UUID ownerId  = TestIds.uuid(10);
        MarketplaceVendor vendor = makeVendor(vendorId, ownerId);
        when(marketplaceVendorRepository.findById(vendorId)).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByMarketplaceVendorId(eq(vendorId), any())).thenReturn(new PageImpl<>(List.of()));

        service.listVendorSubOrders(vendorId, null, 0, 10, ownerId);

        verify(subOrderRepository).findByMarketplaceVendorId(eq(vendorId), any());
    }

    // ─── markPacked ────────────────────────────────────────────────────────────

    @Test
    void markPacked_happyPath_setsPackedStatus() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        SubOrder subOrder = makeSubOrder(TestIds.uuid(100), vendor, SubOrderStatus.PENDING);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByIdAndMarketplaceVendorId(TestIds.uuid(100), TestIds.uuid(7)))
                .thenReturn(Optional.of(subOrder));
        OrderItem item = makeOrderItem(TestIds.uuid(1), FulfillmentStatus.PENDING);
        when(orderItemRepository.findAllBySubOrderId(TestIds.uuid(100))).thenReturn(List.of(item));
        when(subOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markPacked(TestIds.uuid(100), TestIds.uuid(7), TestIds.uuid(10));

        assertEquals(SubOrderStatus.PACKED, subOrder.getStatus());
        assertEquals(FulfillmentStatus.PACKED, item.getFulfillmentStatus());
    }

    @Test
    void markPacked_wrongStatus_throwsBadRequest() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        SubOrder subOrder = makeSubOrder(TestIds.uuid(100), vendor, SubOrderStatus.SHIPPED);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByIdAndMarketplaceVendorId(TestIds.uuid(100), TestIds.uuid(7)))
                .thenReturn(Optional.of(subOrder));

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.markPacked(TestIds.uuid(100), TestIds.uuid(7), TestIds.uuid(10)));
    }

    // ─── markShipped ──────────────────────────────────────────────────────────

    @Test
    void markShipped_happyPath_setsShippedStatus() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        SubOrder subOrder = makeSubOrder(TestIds.uuid(100), vendor, SubOrderStatus.PACKED);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByIdAndMarketplaceVendorId(TestIds.uuid(100), TestIds.uuid(7)))
                .thenReturn(Optional.of(subOrder));
        OrderItem item = makeOrderItem(TestIds.uuid(1), FulfillmentStatus.PACKED);
        when(orderItemRepository.findAllBySubOrderId(TestIds.uuid(100))).thenReturn(List.of(item));
        when(subOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        backend.dtos.requests.order.ShipSubOrderRequest req = new backend.dtos.requests.order.ShipSubOrderRequest();
        req.setTrackingNumber("TRK-789");
        req.setCarrier("FedEx");

        service.markShipped(TestIds.uuid(100), TestIds.uuid(7), req, TestIds.uuid(10));

        assertEquals(SubOrderStatus.SHIPPED, subOrder.getStatus());
        assertEquals("TRK-789", subOrder.getTrackingNumber());
        assertEquals(FulfillmentStatus.SHIPPED, item.getFulfillmentStatus());
    }

    @Test
    void markShipped_wrongStatus_throwsBadRequest() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        SubOrder subOrder = makeSubOrder(TestIds.uuid(100), vendor, SubOrderStatus.PENDING);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByIdAndMarketplaceVendorId(TestIds.uuid(100), TestIds.uuid(7)))
                .thenReturn(Optional.of(subOrder));

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.markShipped(TestIds.uuid(100), TestIds.uuid(7),
                        new backend.dtos.requests.order.ShipSubOrderRequest(), TestIds.uuid(10)));
    }

    // ─── markDelivered ────────────────────────────────────────────────────────

    @Test
    void markDelivered_happyPath_setsDeliveredStatus() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        SubOrder subOrder = makeSubOrder(TestIds.uuid(100), vendor, SubOrderStatus.SHIPPED);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByIdAndMarketplaceVendorId(TestIds.uuid(100), TestIds.uuid(7)))
                .thenReturn(Optional.of(subOrder));
        OrderItem item = makeOrderItem(TestIds.uuid(1), FulfillmentStatus.SHIPPED);
        when(orderItemRepository.findAllBySubOrderId(TestIds.uuid(100))).thenReturn(List.of(item));
        when(subOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markDelivered(TestIds.uuid(100), TestIds.uuid(7), TestIds.uuid(10));

        assertEquals(SubOrderStatus.DELIVERED, subOrder.getStatus());
        assertEquals(FulfillmentStatus.DELIVERED, item.getFulfillmentStatus());
    }

    // ─── cancelSubOrder status guards ─────────────────────────────────────────

    @Test
    void cancelSubOrder_shippedStatus_throwsBadRequest() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        SubOrder subOrder = makeSubOrder(TestIds.uuid(100), vendor, SubOrderStatus.SHIPPED);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByIdAndMarketplaceVendorId(TestIds.uuid(100), TestIds.uuid(7)))
                .thenReturn(Optional.of(subOrder));

        backend.dtos.requests.order.CancelSubOrderRequest req = new backend.dtos.requests.order.CancelSubOrderRequest();
        req.setReason("late");

        assertThrows(backend.exceptions.http.BadRequestException.class,
                () -> service.cancelSubOrder(TestIds.uuid(100), TestIds.uuid(7), req, TestIds.uuid(10)));
    }

    // ─── getCommissionRecord ──────────────────────────────────────────────────

    @Test
    void getCommissionRecord_notFound_throwsResourceNotFoundException() {
        MarketplaceVendor vendor = makeVendor(TestIds.uuid(7), TestIds.uuid(10));
        SubOrder subOrder = makeSubOrder(TestIds.uuid(100), vendor, SubOrderStatus.SHIPPED);
        when(marketplaceVendorRepository.findById(TestIds.uuid(7))).thenReturn(Optional.of(vendor));
        when(subOrderRepository.findByIdAndMarketplaceVendorId(TestIds.uuid(100), TestIds.uuid(7)))
                .thenReturn(Optional.of(subOrder));
        when(orderItemRepository.findAllBySubOrderId(any())).thenReturn(List.of());
        when(commissionRecordRepository.findBySubOrderId(TestIds.uuid(100))).thenReturn(Optional.empty());

        assertThrows(backend.exceptions.http.ResourceNotFoundException.class,
                () -> service.getCommissionRecord(TestIds.uuid(100), TestIds.uuid(7), TestIds.uuid(10)));
    }

    private MarketplaceVendor makeVendor(UUID vendorId, UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setRole(UserRole.VENDOR_OWNER);

        Company company = new Company();
        company.setId(TestIds.uuid(42));
        company.setName("Vendor Co");
        company.setOwner(owner);

        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setId(vendorId);
        vendor.setVendorCompany(company);
        return vendor;
    }

    private SubOrder makeSubOrder(UUID id, MarketplaceVendor vendor, SubOrderStatus status) {
        Order order = new Order();
        order.setId(TestIds.uuid(500));

        SubOrder subOrder = new SubOrder();
        subOrder.setId(id);
        subOrder.setOrder(order);
        subOrder.setMarketplaceVendor(vendor);
        subOrder.setMarketplaceId(TestIds.uuid(12));
        subOrder.setStatus(status);
        subOrder.setSubtotal(BigDecimal.TEN);
        subOrder.setTotalAmount(BigDecimal.TEN);
        subOrder.setCurrency("USD");
        return subOrder;
    }

    private OrderItem makeOrderItem(UUID id, FulfillmentStatus status) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setFulfillmentStatus(status);
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.ONE);
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setPromotionSavings(BigDecimal.ZERO);
        item.setProductName("Test Item");
        return item;
    }
}
