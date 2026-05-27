package backend.services.impl.orders;

import backend.configurations.environment.RiskProperties;
import backend.dtos.requests.order.CreateOrderRequest;
import backend.exceptions.http.PremiumRequiredException;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.models.enums.UserTier;
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
import backend.repositories.ProductKitRepository;
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
import backend.services.intf.orders.OrderFulfillmentEventPublisher;
import backend.services.intf.orders.TrackingService;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused tests for premium-tier behaviour in order creation (item-count gate).
 * The full createOrder pipeline requires extensive integration setup, so these tests
 * verify only the early-exit checks that occur before any stock or pricing logic runs.
 */
class OrderServicePremiumTest {

    private static final UUID USER_ID = TestIds.uuid(1);

    private UserRepository userRepository;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);

        service = new OrderServiceImpl(
                mock(OrderRepository.class),
                mock(OrderCompensationRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                mock(InventoryLocationRepository.class),
                mock(BundleRepository.class),
                mock(ProductKitRepository.class),
                userRepository,
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
                mock(CompanyAccessService.class),
                mock(OrderFulfillmentEventPublisher.class),
                mock(TrackingService.class));
    }

    // ─── item-count gate ─────────────────────────────────────────────────────

    @Test
    void createOrder_freeUserExceeds50Items_throwsPremiumRequired() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser(UserTier.FREE)));

        CreateOrderRequest request = requestWithItems(51);

        assertThrows(PremiumRequiredException.class,
                () -> service.createOrder(USER_ID, request));
    }

    @Test
    void createOrder_freeUserExactly50Items_doesNotThrowOnItemCountGate() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser(UserTier.FREE)));

        CreateOrderRequest request = requestWithItems(50);

        // The gate itself should not fire — subsequent logic will fail on product
        // resolution, but not on the premium item-count check.
        // We assert the thrown exception is NOT PremiumRequiredException.
        Exception ex = assertThrows(Exception.class,
                () -> service.createOrder(USER_ID, request));
        assert !(ex instanceof PremiumRequiredException)
                : "Expected failure from product resolution, not from premium gate";
    }

    @Test
    void createOrder_premiumUserWith51Items_doesNotThrowOnItemCountGate() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser(UserTier.PREMIUM)));

        CreateOrderRequest request = requestWithItems(51);

        // Premium gate must not fire — subsequent failure will be product resolution.
        Exception ex = assertThrows(Exception.class,
                () -> service.createOrder(USER_ID, request));
        assert !(ex instanceof PremiumRequiredException)
                : "Expected failure from product resolution, not from premium gate";
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static User makeUser(UserTier tier) {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail("user@test.com");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setTier(tier);
        return u;
    }

    private static CreateOrderRequest requestWithItems(int count) {
        List<CreateOrderRequest.OrderItemRequest> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
            item.setProductId(TestIds.uuid(100 + i));
            item.setQuantity(1);
            items.add(item);
        }
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(items);
        return req;
    }
}
