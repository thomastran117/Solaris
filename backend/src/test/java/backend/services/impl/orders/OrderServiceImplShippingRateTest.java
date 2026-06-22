package backend.services.impl.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import backend.dtos.responses.order.OrderResponse;
import backend.dtos.shipping.ShippingRate;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.FulfillmentMethod;
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
import backend.repositories.ProductKitRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.PromotionRedemptionRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.RiskAssessmentRepository;
import backend.repositories.RiskReviewRepository;
import backend.repositories.SubOrderRepository;
import backend.repositories.UserRepository;
import backend.repositories.VendorBalanceRepository;
import backend.configurations.environment.RiskProperties;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.CacheService;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.auth.EmailVerificationService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.AllocationService;
import backend.services.intf.orders.OrderFulfillmentEventPublisher;
import backend.services.intf.orders.TrackingService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.PaymentService.PaymentIntentResult;
import backend.services.intf.pricing.CommissionEngine;
import backend.services.intf.pricing.PricingEngine;
import backend.services.intf.pricing.RiskEngine;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.shipping.ShippingRateService;
import backend.services.impl.inventory.StockAlertService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;

class OrderServiceImplShippingRateTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID ORDER_ID = TestIds.uuid(3);
    private static final String PI = "pi_test_123";

    private OrderRepository orderRepository;
    private PaymentService paymentService;
    private CacheService cacheService;
    private InventoryLocationRepository locationRepository;
    private ShippingRateService shippingRateService;
    private OrderServiceImpl service;

    private static final List<ShippingRate> RATES = List.of(
            new ShippingRate("rate_1", "USPS", "Priority", "Priority", 2, 799, "USD"),
            new ShippingRate("rate_2", "UPS", "Ground", "Ground", 5, 599, "USD"));

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentService = mock(PaymentService.class);
        cacheService = mock(CacheService.class);
        locationRepository = mock(InventoryLocationRepository.class);
        shippingRateService = mock(ShippingRateService.class);

        service = new OrderServiceImpl(
                orderRepository,
                mock(OrderCompensationRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                locationRepository,
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
                paymentService,
                cacheService,
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
        service.setShippingRateService(shippingRateService);
        service.setEnvironmentSetting(new backend.configurations.environment.EnvironmentSetting());

        // Lock acquired by default; persist echoes the saved order.
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(locationRepository.findFirstByCompanyIdAndActiveTrueOrderByDisplayOrderAscNameAsc(any()))
                .thenReturn(Optional.empty());
    }

    // ---- getShippingRates --------------------------------------------------

    @Test
    void getShippingRates_returnsProviderRates() {
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(reservedOrder(new BigDecimal("20.00"), 0)));
        when(shippingRateService.getRates(any())).thenReturn(RATES);

        List<ShippingRate> result = service.getShippingRates(USER_ID, ORDER_ID);

        assertEquals(RATES, result);
    }

    @Test
    void getShippingRates_throwsNotFoundForOtherUsersOrder() {
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getShippingRates(USER_ID, ORDER_ID));
    }

    @Test
    void getShippingRates_throwsBadRequestWhenNotReserved() {
        Order order = reservedOrder(new BigDecimal("20.00"), 0);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        assertThrows(BadRequestException.class, () -> service.getShippingRates(USER_ID, ORDER_ID));
    }

    // ---- confirmShippingRate ----------------------------------------------

    @Test
    void confirmShippingRate_foldsShippingIntoTotalAndUpdatesPaymentIntent() {
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(reservedOrder(new BigDecimal("20.00"), 0)));
        when(shippingRateService.getRates(any())).thenReturn(RATES);
        when(paymentService.updatePaymentIntentAmount(eq(PI), anyLong()))
                .thenReturn(new PaymentIntentResult(PI, "secret", 2599, "usd", "requires_payment_method", null));

        OrderResponse response = service.confirmShippingRate(USER_ID, ORDER_ID, "rate_2");

        assertEquals(0, new BigDecimal("25.99").compareTo(response.getTotalAmount()));
        assertEquals(599, response.getShippingCostCents());
        assertEquals("UPS", response.getShippingCarrier());
        verify(paymentService).updatePaymentIntentAmount(PI, 2599);
    }

    @Test
    void confirmShippingRate_replacesPreviousShippingInsteadOfStacking() {
        // Order already has rate_2 (599) applied: total 25.99 = base 20.00 + 5.99.
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(reservedOrder(new BigDecimal("25.99"), 599)));
        when(shippingRateService.getRates(any())).thenReturn(RATES);
        when(paymentService.updatePaymentIntentAmount(eq(PI), anyLong()))
                .thenReturn(new PaymentIntentResult(PI, "secret", 2799, "usd", "requires_payment_method", null));

        OrderResponse response = service.confirmShippingRate(USER_ID, ORDER_ID, "rate_1");

        assertEquals(0, new BigDecimal("27.99").compareTo(response.getTotalAmount()));
        assertEquals(799, response.getShippingCostCents());
        verify(paymentService).updatePaymentIntentAmount(PI, 2799);
    }

    @Test
    void confirmShippingRate_rejectsRateNotInProviderList() {
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(reservedOrder(new BigDecimal("20.00"), 0)));
        when(shippingRateService.getRates(any())).thenReturn(RATES);

        assertThrows(BadRequestException.class, () -> service.confirmShippingRate(USER_ID, ORDER_ID, "rate_unknown"));
        verify(paymentService, never()).updatePaymentIntentAmount(anyString(), anyLong());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmShippingRate_rejectsRateInDifferentCurrency() {
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(reservedOrder(new BigDecimal("20.00"), 0))); // order currency USD
        when(shippingRateService.getRates(any())).thenReturn(List.of(
                new ShippingRate("rate_cad", "Canada Post", "Expedited", "Expedited", 3, 599, "CAD")));

        assertThrows(BadRequestException.class, () -> service.confirmShippingRate(USER_ID, ORDER_ID, "rate_cad"));
        verify(paymentService, never()).updatePaymentIntentAmount(anyString(), anyLong());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmShippingRate_rejectsNonReservedOrder() {
        Order order = reservedOrder(new BigDecimal("20.00"), 0);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () -> service.confirmShippingRate(USER_ID, ORDER_ID, "rate_1"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmShippingRate_throwsNotFoundForMissingOrder() {
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.confirmShippingRate(USER_ID, ORDER_ID, "rate_1"));
    }

    @Test
    void confirmShippingRate_doesNotPersistWhenStripeRejectsAmountChange() {
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(reservedOrder(new BigDecimal("20.00"), 0)));
        when(shippingRateService.getRates(any())).thenReturn(RATES);
        // Simulates Stripe InvalidRequestException already mapped to a domain 400.
        when(paymentService.updatePaymentIntentAmount(eq(PI), anyLong()))
                .thenThrow(new BadRequestException("This payment can no longer be modified"));

        assertThrows(BadRequestException.class, () -> service.confirmShippingRate(USER_ID, ORDER_ID, "rate_1"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmShippingRate_rollsBackPaymentIntentWhenPersistFails() {
        when(orderRepository.findByIdAndUserIdWithItems(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(reservedOrder(new BigDecimal("20.00"), 0)));
        when(shippingRateService.getRates(any())).thenReturn(RATES);
        when(paymentService.updatePaymentIntentAmount(eq(PI), anyLong()))
                .thenReturn(new PaymentIntentResult(PI, "secret", 2799, "usd", "requires_payment_method", null));
        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> service.confirmShippingRate(USER_ID, ORDER_ID, "rate_1"));

        // New amount sent first (2799 = 2000 + 799), then a best-effort rollback to the prior total (2000).
        verify(paymentService).updatePaymentIntentAmount(PI, 2799);
        verify(paymentService).updatePaymentIntentAmount(PI, 2000);
    }

    // ---- helpers -----------------------------------------------------------

    private Order reservedOrder(BigDecimal total, long shippingCents) {
        Company company = new Company();
        company.setId(COMPANY_ID);

        InventoryLocation location = new InventoryLocation();
        location.setId(TestIds.uuid(50));
        location.setCompany(company);

        Product product = new Product();
        product.setId(TestIds.uuid(100));
        product.setCompany(company);
        product.setName("Desk");
        product.setWeightGrams(800);

        OrderItem item = new OrderItem();
        item.setId(TestIds.uuid(10));
        item.setProduct(product);
        item.setProductName("Desk");
        item.setQuantity(1);
        item.setUnitPrice(total);
        item.setFulfillmentLocation(location);

        User user = new User();
        user.setId(USER_ID);
        user.setRole(UserRole.USER);
        user.setEmail("u@example.com");

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUser(user);
        order.setItems(List.of(item));
        order.setTotalAmount(total);
        order.setCurrency("USD");
        order.setStatus(OrderStatus.RESERVED);
        order.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        order.setPaymentIntentId(PI);
        order.setShippingCostCents(shippingCents);
        order.setShipCity("Boston");
        order.setShipPostalCode("02108");
        order.setShipCountry("US");
        return order;
    }
}
