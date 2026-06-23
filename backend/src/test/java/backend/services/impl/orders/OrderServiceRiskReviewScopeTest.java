package backend.services.impl.orders;

import backend.configurations.environment.RiskProperties;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.OrderStatus;
import backend.models.enums.UserRole;
import backend.repositories.BundleRepository;
import backend.repositories.ProductKitRepository;
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
import backend.services.intf.inventory.AllocationService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.pricing.CommissionEngine;
import backend.services.intf.pricing.PricingEngine;
import backend.services.intf.pricing.RiskEngine;
import backend.services.intf.orders.OrderFulfillmentEventPublisher;
import backend.services.intf.orders.TrackingService;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceRiskReviewScopeTest {

    private OrderRepository orderRepository;
    private CompanyRepository companyRepository;
    private RiskAssessmentRepository riskAssessmentRepository;
    private RiskReviewRepository riskReviewRepository;
    private PaymentService paymentService;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository          = mock(OrderRepository.class);
        companyRepository        = mock(CompanyRepository.class);
        riskAssessmentRepository = mock(RiskAssessmentRepository.class);
        riskReviewRepository     = mock(RiskReviewRepository.class);
        paymentService           = mock(PaymentService.class);

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
                companyRepository,
                mock(CouponRepository.class),
                mock(CouponRedemptionRepository.class),
                mock(CouponPerUserCountRepository.class),
                mock(PromotionRuleRepository.class),
                mock(PromotionRedemptionRepository.class),
                mock(PricingEngine.class),
                new backend.services.impl.pricing.TaxServiceImpl(mock(backend.repositories.TaxRateRepository.class), new java.math.BigDecimal("0.00"), false),
                paymentService,
                mock(CacheService.class),
                mock(StockAlertService.class),
                mock(EmailService.class),
                mock(AllocationService.class),
                mock(RiskEngine.class),
                riskAssessmentRepository,
                riskReviewRepository,
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
                mock(CompanyAccessService.class),
                mock(OrderFulfillmentEventPublisher.class),
                mock(TrackingService.class));
    }

    @Test
    void getOrderRisk_throwsWhenOrderContainsAnotherCompany() {
        Company company = makeCompany(TestIds.uuid(10), TestIds.uuid(3));
        Order order     = makeMixedCompanyOrder(TestIds.uuid(200));

        when(companyRepository.findByIdAndOwnerId(TestIds.uuid(10), TestIds.uuid(3))).thenReturn(Optional.of(company));
        when(orderRepository.findByIdAndProductCompanyId(TestIds.uuid(200), TestIds.uuid(10))).thenReturn(Optional.of(order));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getOrderRisk(TestIds.uuid(10), TestIds.uuid(200), TestIds.uuid(3)));
        verifyNoInteractions(riskAssessmentRepository);
    }

    @Test
    void approveRiskReview_throwsWhenOrderContainsAnotherCompany() {
        Company company = makeCompany(TestIds.uuid(10), TestIds.uuid(3));
        Order order     = makeMixedCompanyOrder(TestIds.uuid(200));
        order.setStatus(OrderStatus.UNDER_REVIEW);

        when(companyRepository.findByIdAndOwnerId(TestIds.uuid(10), TestIds.uuid(3))).thenReturn(Optional.of(company));
        when(orderRepository.findByIdAndProductCompanyId(TestIds.uuid(200), TestIds.uuid(10))).thenReturn(Optional.of(order));

        assertThrows(ResourceNotFoundException.class,
                () -> service.approveRiskReview(TestIds.uuid(10), TestIds.uuid(200), TestIds.uuid(3), null));
        verifyNoInteractions(riskReviewRepository);
        verifyNoInteractions(paymentService);
    }

    private Company makeCompany(UUID companyId, UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setRole(UserRole.MERCHANT);

        Company company = new Company();
        company.setId(companyId);
        company.setOwner(owner);
        company.setName("Company " + companyId);
        return company;
    }

    private Order makeMixedCompanyOrder(UUID orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.RESERVED);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCurrency("USD");
        order.setUser(makeUser(TestIds.uuid(9)));
        order.setItems(List.of(
                makeOrderItem(TestIds.uuid(1), TestIds.uuid(10)),
                makeOrderItem(TestIds.uuid(2), TestIds.uuid(20))));
        return order;
    }

    private OrderItem makeOrderItem(UUID itemId, UUID companyId) {
        Company company = makeCompany(companyId, TestIds.uuid(1000));
        Product product = new Product();
        product.setId(itemId);
        product.setCompany(company);
        product.setName("Product " + itemId);

        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.ONE);
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setPromotionSavings(BigDecimal.ZERO);
        return item;
    }

    private User makeUser(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setRole(UserRole.USER);
        user.setEmail("user" + userId + "@example.com");
        return user;
    }
}
