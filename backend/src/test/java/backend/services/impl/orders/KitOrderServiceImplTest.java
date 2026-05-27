package backend.services.impl.orders;

import backend.configurations.environment.RiskProperties;
import backend.dtos.requests.order.CreateOrderRequest;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.models.core.Company;
import backend.models.core.KitSlot;
import backend.models.core.KitSlotChoice;
import backend.models.core.Order;
import backend.models.core.Product;
import backend.models.core.ProductKit;
import backend.models.core.RiskAssessment;
import backend.models.core.User;
import backend.models.enums.ProductStatus;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskMode;
import backend.models.enums.UserRole;
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
import backend.services.pricing.CartContext;
import backend.services.pricing.LineBreakdown;
import backend.services.pricing.PricingResult;
import backend.services.risk.RiskAssessmentResult;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for kit-related logic in {@link OrderServiceImpl#createOrder}.
 *
 * Tests cover: pre-lock kit validation, validateKitSelections failure cases,
 * insufficient stock, and the happy-path stock decrement + order persist flow.
 */
class KitOrderServiceImplTest {

    private static final UUID USER_ID         = TestIds.uuid(1);
    private static final UUID KIT_ID          = TestIds.uuid(2);
    private static final UUID COMPANY_ID      = TestIds.uuid(3);
    private static final UUID SLOT_A_ID       = TestIds.uuid(10);   // required
    private static final UUID SLOT_B_ID       = TestIds.uuid(11);   // optional
    private static final UUID PRODUCT_A_ID    = TestIds.uuid(20);
    private static final UUID UNKNOWN_SLOT_ID = TestIds.uuid(99);

    private OrderRepository         orderRepository;
    private ProductKitRepository    kitRepository;
    private ProductRepository       productRepository;
    private CacheService            cacheService;
    private PricingEngine           pricingEngine;
    private PaymentService          paymentService;
    private UserRepository          userRepository;
    private RiskEngine              riskEngine;
    private RiskProperties          riskProperties;
    private RiskAssessmentRepository riskAssessmentRepository;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository          = mock(OrderRepository.class);
        kitRepository            = mock(ProductKitRepository.class);
        productRepository        = mock(ProductRepository.class);
        cacheService             = mock(CacheService.class);
        pricingEngine            = mock(PricingEngine.class);
        paymentService           = mock(PaymentService.class);
        userRepository           = mock(UserRepository.class);
        riskEngine               = mock(RiskEngine.class);
        riskProperties           = mock(RiskProperties.class);
        riskAssessmentRepository = mock(RiskAssessmentRepository.class);

        service = new OrderServiceImpl(
                orderRepository,
                mock(OrderCompensationRepository.class),
                productRepository,
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                mock(InventoryLocationRepository.class),
                mock(BundleRepository.class),
                kitRepository,
                userRepository,
                mock(CompanyRepository.class),
                mock(CouponRepository.class),
                mock(CouponRedemptionRepository.class),
                mock(CouponPerUserCountRepository.class),
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
                mock(RiskReviewRepository.class),
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
                mock(LoyaltyService.class),
                mock(ActivityEventPublisher.class),
                mock(CompanyAccessService.class),
                mock(OrderFulfillmentEventPublisher.class),
                mock(TrackingService.class));
    }

    // -------------------------------------------------------------------------
    // Pre-lock kit availability checks
    // -------------------------------------------------------------------------

    @Test
    void kitNotActive_throwsBadRequest() {
        ProductKit kit = kit();
        kit.setStatus(ProductStatus.DRAFT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(kitRepository.findById(KIT_ID)).thenReturn(Optional.of(kit));

        assertThrows(BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(KIT_ID, List.of(selA()))));
    }

    @Test
    void kitNotListed_throwsBadRequest() {
        ProductKit kit = kit();
        kit.setListed(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(kitRepository.findById(KIT_ID)).thenReturn(Optional.of(kit));

        assertThrows(BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(KIT_ID, List.of(selA()))));
    }

    // -------------------------------------------------------------------------
    // validateKitSelections — checked inside the lock block
    // -------------------------------------------------------------------------

    @Test
    void missingRequiredSlot_throwsBadRequest() {
        // Slot A is required but the request provides no selections at all.
        // No product IDs → lock set is empty → no cacheService calls needed.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(kitRepository.findById(KIT_ID)).thenReturn(Optional.of(kit()));
        when(productRepository.findAllById(any())).thenReturn(List.of());

        assertThrows(BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(KIT_ID, List.of())));
    }

    @Test
    void duplicateSlotId_throwsBadRequest() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(kitRepository.findById(KIT_ID)).thenReturn(Optional.of(kit()));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findAllById(any())).thenReturn(List.of());

        CreateOrderRequest.KitSelectionRequest dup = new CreateOrderRequest.KitSelectionRequest();
        dup.setSlotId(SLOT_A_ID);
        dup.setProductId(PRODUCT_A_ID);
        dup.setQuantity(1);

        assertThrows(BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(KIT_ID, List.of(selA(), dup))));
    }

    @Test
    void unknownSlotId_throwsBadRequest() {
        // Provide a valid selection for the required slot A AND an unknown slot ID.
        // validateKitSelections iterates kit slots first (passes), then checks request
        // slots against the kit set → throws "Unrecognised slot id".
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(kitRepository.findById(KIT_ID)).thenReturn(Optional.of(kit()));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findAllById(any())).thenReturn(List.of());

        CreateOrderRequest.KitSelectionRequest unknownSel = new CreateOrderRequest.KitSelectionRequest();
        unknownSel.setSlotId(UNKNOWN_SLOT_ID);
        unknownSel.setProductId(PRODUCT_A_ID);
        unknownSel.setQuantity(1);

        assertThrows(BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(KIT_ID, List.of(selA(), unknownSel))));
    }

    @Test
    void quantityExceedsSlotMax_throwsBadRequest() {
        // Slot A has maxQty=1; requesting quantity=5 violates that constraint.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(kitRepository.findById(KIT_ID)).thenReturn(Optional.of(kit()));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findAllById(any())).thenReturn(List.of());

        CreateOrderRequest.KitSelectionRequest overQty = new CreateOrderRequest.KitSelectionRequest();
        overQty.setSlotId(SLOT_A_ID);
        overQty.setProductId(PRODUCT_A_ID);
        overQty.setQuantity(5);

        assertThrows(BadRequestException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(KIT_ID, List.of(overQty))));
    }

    // -------------------------------------------------------------------------
    // Stock reservation
    // -------------------------------------------------------------------------

    @Test
    void insufficientStockForComponent_throwsConflict() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(kitRepository.findById(KIT_ID)).thenReturn(Optional.of(kit()));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(productRepository.decrementStock(PRODUCT_A_ID, 1)).thenReturn(0);

        assertThrows(ConflictException.class,
                () -> service.createOrder(USER_ID, deliveryRequest(KIT_ID, List.of(selA()))));
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void happyPath_stockDecrementedAndOrderPersisted() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(kitRepository.findById(KIT_ID)).thenReturn(Optional.of(kit()));
        when(cacheService.tryLock(any(), any(), anyLong())).thenReturn(true);
        when(productRepository.findAllById(any())).thenReturn(List.of());
        when(productRepository.decrementStock(PRODUCT_A_ID, 1)).thenReturn(1);
        when(userRepository.findSegmentIdsByUserId(USER_ID)).thenReturn(List.of());

        BigDecimal componentPrice = new BigDecimal("200.00");
        PricingResult pricing = simplePricing(0, componentPrice, 1);
        when(pricingEngine.quote(any(CartContext.class))).thenReturn(pricing);

        RiskAssessment savedRisk = new RiskAssessment();
        savedRisk.setId(UUID.randomUUID());
        savedRisk.setDecision(RiskAction.ALLOW);
        savedRisk.setScore(0);
        when(riskEngine.assess(any())).thenReturn(RiskAssessmentResult.allow(0, List.of(), List.of()));
        when(riskProperties.getMode()).thenReturn(RiskMode.SHADOW);
        when(riskAssessmentRepository.save(any())).thenReturn(savedRisk);

        when(paymentService.createPaymentIntent(anyLong(), any(), any(), any()))
                .thenReturn(new PaymentService.PaymentIntentResult(
                        "pi_test", "secret_test", 20000L, "usd", "requires_payment_method", null));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(TestIds.uuid(99));
            return o;
        });

        service.createOrder(USER_ID, deliveryRequest(KIT_ID, List.of(selA())));

        verify(productRepository).decrementStock(PRODUCT_A_ID, 1);
        verify(orderRepository, atLeastOnce()).save(any(Order.class));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private User user() {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail("buyer@example.com");
        u.setFirstName("Test");
        u.setRole(UserRole.USER);
        u.setTier(UserTier.FREE);
        u.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return u;
    }

    private ProductKit kit() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Tech Shop");

        Product prodA = new Product();
        prodA.setId(PRODUCT_A_ID);
        prodA.setName("CPU i9");
        prodA.setStatus(ProductStatus.ACTIVE);
        prodA.setPurchasable(true);
        prodA.setPrice(new BigDecimal("200.00"));
        prodA.setCompany(company);

        KitSlotChoice choiceA = new KitSlotChoice();
        choiceA.setId(TestIds.uuid(30));
        choiceA.setProduct(prodA);
        choiceA.setDefaultChoice(false);
        choiceA.setDisplayOrder(0);

        KitSlot slotA = new KitSlot();
        slotA.setId(SLOT_A_ID);
        slotA.setName("CPU");
        slotA.setRequired(true);
        slotA.setMinQty(1);
        slotA.setMaxQty(1);
        slotA.setDisplayOrder(0);
        slotA.setChoices(new ArrayList<>(List.of(choiceA)));

        KitSlot slotB = new KitSlot();
        slotB.setId(SLOT_B_ID);
        slotB.setName("GPU");
        slotB.setRequired(false);
        slotB.setMinQty(1);
        slotB.setMaxQty(1);
        slotB.setDisplayOrder(1);
        slotB.setChoices(new ArrayList<>());

        ProductKit kit = new ProductKit();
        kit.setId(KIT_ID);
        kit.setName("Build Your PC");
        kit.setStatus(ProductStatus.ACTIVE);
        kit.setListed(true);
        kit.setCompany(company);
        kit.setCurrency("USD");
        kit.setSlots(new ArrayList<>(List.of(slotA, slotB)));
        return kit;
    }

    /** A kit selection for slot A → product A, qty 1. */
    private CreateOrderRequest.KitSelectionRequest selA() {
        CreateOrderRequest.KitSelectionRequest sel = new CreateOrderRequest.KitSelectionRequest();
        sel.setSlotId(SLOT_A_ID);
        sel.setProductId(PRODUCT_A_ID);
        sel.setQuantity(1);
        return sel;
    }

    /** Delivery request with a single kit item. */
    private CreateOrderRequest deliveryRequest(UUID kitId, List<CreateOrderRequest.KitSelectionRequest> selections) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setKitId(kitId);
        item.setQuantity(1);
        item.setKitSelections(selections);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item));
        req.setCurrency("usd");
        req.setShipRecipientName("Jane Doe");
        req.setShipStreet("1 Market St");
        req.setShipCity("San Francisco");
        req.setShipPostalCode("94105");
        req.setShipCountry("US");
        return req;
    }

    /**
     * Builds a minimal PricingResult with {@code lineCount} lines each at {@code unitPrice},
     * no discounts, total = {@code lineCount * unitPrice}.
     */
    private PricingResult simplePricing(int index, BigDecimal unitPrice, int quantity) {
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        LineBreakdown lb = new LineBreakdown(
                index, null, null, quantity, unitPrice, BigDecimal.ZERO, total, List.of(), null);
        return new PricingResult(
                List.of(lb), List.of(), total, BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, BigDecimal.ZERO, total, List.of());
    }
}
