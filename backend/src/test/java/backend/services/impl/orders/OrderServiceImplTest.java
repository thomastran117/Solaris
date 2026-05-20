package backend.services.impl.orders;

import backend.configurations.environment.RiskProperties;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CompanyOrderResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
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
import backend.repositories.ProductVariantRepository;
import backend.repositories.PromotionRedemptionRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.RiskAssessmentRepository;
import backend.repositories.RiskReviewRepository;
import backend.repositories.SubOrderRepository;
import backend.repositories.UserRepository;
import backend.repositories.VendorBalanceRepository;
import backend.services.impl.inventory.StockAlertService;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.CacheService;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.auth.EmailVerificationService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.AllocationService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID ORDER_ID = TestIds.uuid(3);

    private OrderRepository orderRepository;
    private CompanyAccessService companyAccessService;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        companyAccessService = mock(CompanyAccessService.class);

        service = new OrderServiceImpl(
                orderRepository,
                mock(OrderCompensationRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                mock(InventoryLocationRepository.class),
                mock(BundleRepository.class),
                mock(UserRepository.class),
                mock(CompanyRepository.class),
                mock(CouponRepository.class),
                mock(CouponRedemptionRepository.class),
                mock(CouponPerUserCountRepository.class),
                mock(PromotionRuleRepository.class),
                mock(PromotionRedemptionRepository.class),
                mock(PricingEngine.class),
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
                companyAccessService);
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
}
