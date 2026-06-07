package backend.services.impl.subscriptions;

import backend.dtos.requests.subscription.CreateSubscriptionRequest;
import backend.dtos.requests.subscription.ShippingAddressRequest;
import backend.dtos.requests.subscription.UpdateSubscriptionRequest;
import backend.dtos.responses.subscription.SetupIntentResponse;
import backend.dtos.responses.subscription.SubscriptionResponse;
import backend.events.activity.UserActivityEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.SavedPaymentMethod;
import backend.models.core.Subscription;
import backend.models.core.SubscriptionItem;
import backend.models.core.User;
import backend.models.enums.BillingInterval;
import backend.models.enums.SubscriptionStatus;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.SavedPaymentMethodRepository;
import backend.repositories.SubscriptionRepository;
import backend.repositories.UserRepository;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.orders.OrderService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.promotions.LoyaltyService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceImplTest {

    private SubscriptionRepository subscriptionRepository;
    private SavedPaymentMethodRepository savedPaymentMethodRepository;
    private UserRepository userRepository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private PaymentService paymentService;
    private OrderService orderService;
    private OrderRepository orderRepository;
    private LoyaltyService loyaltyService;
    private ActivityEventPublisher activityEventPublisher;
    private SubscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        subscriptionRepository       = mock(SubscriptionRepository.class);
        savedPaymentMethodRepository = mock(SavedPaymentMethodRepository.class);
        userRepository               = mock(UserRepository.class);
        productRepository            = mock(ProductRepository.class);
        variantRepository            = mock(ProductVariantRepository.class);
        paymentService               = mock(PaymentService.class);
        orderService                 = mock(OrderService.class);
        orderRepository              = mock(OrderRepository.class);
        loyaltyService               = mock(LoyaltyService.class);
        activityEventPublisher       = mock(ActivityEventPublisher.class);

        service = new SubscriptionServiceImpl(
                subscriptionRepository,
                savedPaymentMethodRepository,
                userRepository,
                productRepository,
                variantRepository,
                paymentService,
                orderService,
                orderRepository,
                loyaltyService,
                activityEventPublisher);
    }

    // ─── create ─────────────────────────────────────────────────────────────

    @Test
    void create_rejectsNonSubscribableProduct() {
        User user = makeUser(TestIds.uuid(1));
        Product product = makeProduct(TestIds.uuid(10), false, null);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class, () -> service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10))));
    }

    @Test
    void create_rejectsDisallowedInterval() {
        User user = makeUser(TestIds.uuid(1));
        Product product = makeProduct(TestIds.uuid(10), true, "MONTH:1,MONTH:3");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));

        CreateSubscriptionRequest req = makeCreateRequest(TestIds.uuid(10));
        req.setBillingInterval(BillingInterval.WEEK);
        req.setIntervalCount(1);

        assertThrows(BadRequestException.class, () -> service.create(TestIds.uuid(1), req));
    }

    @Test
    void create_rejectsUnavailableProduct() {
        User user = makeUser(TestIds.uuid(1));
        Product product = makeProduct(TestIds.uuid(10), true, null);
        product.setListed(false);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class, () -> service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10))));
    }

    @Test
    void create_rejectsUnavailableVariant() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(TestIds.uuid(10), true, null);
        ProductVariant variant = makeVariant(TestIds.uuid(55), product, false);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(TestIds.uuid(55), TestIds.uuid(10))).thenReturn(Optional.of(variant));

        CreateSubscriptionRequest req = makeCreateRequest(TestIds.uuid(10));
        req.setVariantId(TestIds.uuid(55));

        assertThrows(BadRequestException.class, () -> service.create(TestIds.uuid(1), req));
    }

    @Test
    void create_acceptsAllowedIntervalAndPersistsSubscription() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(TestIds.uuid(10), true, "MONTH:1");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));

        when(paymentService.retrievePaymentMethod("pm_test"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_test", "cus_123", "visa", "4242", 12, 2030));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_1", 1000L, "usd"));
        when(paymentService.createSubscription(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.SubscriptionResult(
                        "sub_1", "cus_123", "active", "in_1",
                        Instant.now(), Instant.now().plusSeconds(86400 * 30), "pm_test", "si_1"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10)));

        assertEquals(SubscriptionStatus.ACTIVE, res.getStatus());
        assertEquals(BillingInterval.MONTH, res.getBillingInterval());
        assertEquals(1, res.getItems().size());
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void create_rejectsPaymentMethodOwnedByDifferentCustomer() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(TestIds.uuid(10), true, null);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));
        when(paymentService.retrievePaymentMethod("pm_test"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_test", "cus_OTHER", "visa", "4242", 12, 2030));

        assertThrows(BadRequestException.class, () -> service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10))));
    }

    // ─── pause / resume ─────────────────────────────────────────────────────

    @Test
    void pause_onlyActiveSubscription() {
        Subscription sub = makeSubscription(SubscriptionStatus.PAUSED);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        assertThrows(ConflictException.class, () -> service.pause(TestIds.uuid(1), TestIds.uuid(99)));
    }

    @Test
    void pause_callsStripeAndPersistsPausedStatus() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(paymentService.pauseSubscription("sub_1")).thenReturn(stripeResult("paused"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.pause(TestIds.uuid(1), TestIds.uuid(99));

        assertEquals(SubscriptionStatus.PAUSED, res.getStatus());
        assertNotNull(res.getPausedAt());
        verify(paymentService).pauseSubscription("sub_1");
    }

    @Test
    void resume_onlyPausedSubscription() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        assertThrows(ConflictException.class, () -> service.resume(TestIds.uuid(1), TestIds.uuid(99)));
    }

    // ─── skipNext ───────────────────────────────────────────────────────────

    @Test
    void skipNext_setsFlagAndAdvancesPeriod() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(paymentService.skipNextCycle("sub_1", BillingInterval.MONTH, 1)).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.skipNext(TestIds.uuid(1), TestIds.uuid(99));
        assertTrue(res.isSkipNextCycle());
    }

    // ─── update (qty only vs price-changing) ────────────────────────────────

    @Test
    void update_quantityOnlyUsesUpdateQuantityPath() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(paymentService.updateSubscriptionQuantity("sub_1", "si_1", 3)).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setQuantity(3);

        service.update(TestIds.uuid(1), TestIds.uuid(99), req);

        verify(paymentService).updateSubscriptionQuantity("sub_1", "si_1", 3);
        verify(paymentService, never()).swapSubscriptionPrice(any(), any(), any(), anyInt());
    }

    @Test
    void update_intervalChangeCreatesNewPriceAndSwaps() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_2", 1000L, "usd"));
        when(paymentService.swapSubscriptionPrice("sub_1", "si_1", "price_2", 2)).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setBillingInterval(BillingInterval.WEEK);
        req.setIntervalCount(2);
        req.setQuantity(2);

        service.update(TestIds.uuid(1), TestIds.uuid(99), req);

        verify(paymentService).swapSubscriptionPrice("sub_1", "si_1", "price_2", 2);
    }

    @Test
    void update_productSwapRejectsUnavailableProduct() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        Product replacement = makeProduct(TestIds.uuid(20), true, null);
        replacement.setPurchasable(false);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(productRepository.findById(TestIds.uuid(20))).thenReturn(Optional.of(replacement));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setProductId(TestIds.uuid(20));

        assertThrows(BadRequestException.class, () -> service.update(TestIds.uuid(1), TestIds.uuid(99), req));
    }

    @Test
    void update_productSwapRefreshesSubscriptionCompany() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        Product replacement = makeProduct(TestIds.uuid(20), true, null);
        Company replacementCompany = new Company();
        replacementCompany.setId(TestIds.uuid(77));
        replacement.setCompany(replacementCompany);

        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(productRepository.findById(TestIds.uuid(20))).thenReturn(Optional.of(replacement));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_2", 1000L, "usd"));
        when(paymentService.swapSubscriptionPrice("sub_1", "si_1", "price_2", 1)).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setProductId(TestIds.uuid(20));

        service.update(TestIds.uuid(1), TestIds.uuid(99), req);

        assertEquals(TestIds.uuid(77), sub.getCompany().getId());
        assertEquals(TestIds.uuid(20), sub.getItems().get(0).getProduct().getId());
    }

    // ─── cancel ─────────────────────────────────────────────────────────────

    @Test
    void cancel_atPeriodEndKeepsActiveStatusButFlags() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserIdForUpdate(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(paymentService.cancelSubscription("sub_1", true)).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.cancel(TestIds.uuid(1), TestIds.uuid(99), true);
        assertTrue(res.isCancelAtPeriodEnd());
        assertNotEquals(SubscriptionStatus.CANCELLED, res.getStatus());
    }

    @Test
    void cancel_immediateMarksCancelledAndClearsNextBilling() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserIdForUpdate(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(paymentService.cancelSubscription("sub_1", false)).thenReturn(stripeResult("canceled"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.cancel(TestIds.uuid(1), TestIds.uuid(99), false);
        assertEquals(SubscriptionStatus.CANCELLED, res.getStatus());
        assertNull(res.getNextBillingAt());
        assertNotNull(res.getCancelledAt());
    }

    // ─── invoice.paid webhook ────────────────────────────────────────────────

    @Test
    void handleInvoicePaid_createsOrderAndMarksActive() {
        Subscription sub = makeSubscription(SubscriptionStatus.PAST_DUE);
        Company oldCompany = new Company();
        oldCompany.setId(TestIds.uuid(10));
        sub.setCompany(oldCompany);
        Company currentCompany = new Company();
        currentCompany.setId(TestIds.uuid(20));
        sub.getItems().get(0).getProduct().setCompany(currentCompany);

        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(stripeResult("active"));
        when(orderRepository.findByStripeInvoiceId("in_42")).thenReturn(Optional.of(new backend.models.core.Order()));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleInvoicePaid("in_42", "sub_1", 1500L);

        verify(orderService).createRenewalOrder(eq(sub), eq("in_42"), eq(1500L));
        verify(loyaltyService).recordOrderEarn(any(), eq(TestIds.uuid(20)));
        assertEquals(TestIds.uuid(20), sub.getCompany().getId());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertFalse(sub.isSkipNextCycle());
    }

    @Test
    void handleInvoicePaid_unknownSubscriptionIsNoop() {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_unknown")).thenReturn(Optional.empty());

        service.handleInvoicePaid("in_42", "sub_unknown", 100L);

        verify(orderService, never()).createRenewalOrder(any(), anyString(), anyLong());
    }

    @Test
    void handleInvoicePaymentFailed_marksPastDue() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleInvoicePaymentFailed("in_99", "sub_1");
        assertEquals(SubscriptionStatus.PAST_DUE, sub.getStatus());
    }

    // ─── resume happy path ───────────────────────────────────────────────────

    @Test
    void resume_callsStripeAndPersistsActiveStatus() {
        Subscription sub = makeSubscription(SubscriptionStatus.PAUSED);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(paymentService.resumeSubscription("sub_1")).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.resume(TestIds.uuid(1), TestIds.uuid(99));

        assertEquals(SubscriptionStatus.ACTIVE, res.getStatus());
        assertNull(res.getPausedAt());
        verify(paymentService).resumeSubscription("sub_1");
    }

    // ─── skipNext guard ───────────────────────────────────────────────────────

    @Test
    void skipNext_nonActiveSubscription_throwsConflict() {
        Subscription sub = makeSubscription(SubscriptionStatus.PAUSED);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));

        assertThrows(ConflictException.class, () -> service.skipNext(TestIds.uuid(1), TestIds.uuid(99)));
        verifyNoInteractions(paymentService);
    }

    // ─── cancel guard ─────────────────────────────────────────────────────────

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        Subscription sub = makeSubscription(SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findByIdAndUserIdForUpdate(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));

        assertThrows(ConflictException.class, () -> service.cancel(TestIds.uuid(1), TestIds.uuid(99), true));
        verifyNoInteractions(paymentService);
    }

    // ─── create — discount & activity ────────────────────────────────────────

    @Test
    void create_subscriptionDiscount_reducesUnitPrice() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(TestIds.uuid(10), true, null);
        product.setSubscriptionDiscountPercent(BigDecimal.TEN); // 10% off $10 = $9 = 900 cents
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));
        when(paymentService.retrievePaymentMethod("pm_test"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_test", "cus_123", "visa", "4242", 12, 2030));
        when(paymentService.createRecurringPrice(eq(900L), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_discounted", 900L, "usd"));
        when(paymentService.createSubscription(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10)));

        verify(paymentService).createRecurringPrice(eq(900L), anyString(), any(), anyInt(), anyString(), any());
    }

    @Test
    void create_publishesActivityEvent_whenMarketplaceIdPresent() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(TestIds.uuid(10), true, null);
        product.setMarketplaceId(TestIds.uuid(99));
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));
        when(paymentService.retrievePaymentMethod("pm_test"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_test", "cus_123", "visa", "4242", 12, 2030));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_1", 1000L, "usd"));
        when(paymentService.createSubscription(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10)));

        verify(activityEventPublisher).publish(any(UserActivityEvent.class));
    }

    @Test
    void create_noActivityEvent_whenMarketplaceIdNull() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(TestIds.uuid(10), true, null);
        // marketplaceId is null by default in makeProduct
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));
        when(paymentService.retrievePaymentMethod("pm_test"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_test", "cus_123", "visa", "4242", 12, 2030));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_1", 1000L, "usd"));
        when(paymentService.createSubscription(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10)));

        verifyNoInteractions(activityEventPublisher);
    }

    @Test
    void create_createsStripeCustomer_whenUserHasNoCustomerId() {
        User user = makeUser(TestIds.uuid(1));
        // stripeCustomerId is null — ensureStripeCustomer should create one
        Product product = makeProduct(TestIds.uuid(10), true, null);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));

        PaymentService.CustomerResult custResult = new PaymentService.CustomerResult("cus_new", "u@example.com", "Test User");
        when(paymentService.createCustomer(anyString(), anyString(), any())).thenReturn(custResult);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentService.retrievePaymentMethod("pm_test"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_test", "cus_new", "visa", "4242", 12, 2030));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_1", 1000L, "usd"));
        when(paymentService.createSubscription(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10)));

        verify(paymentService).createCustomer(anyString(), anyString(), any());
    }

    // ─── handleInvoicePaid guards ─────────────────────────────────────────────

    @Test
    void handleInvoicePaid_cancelAtPeriodEnd_skipsOrderCreation() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        sub.setCancelAtPeriodEnd(true);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));

        service.handleInvoicePaid("in_42", "sub_1", 1500L);

        verify(orderService, never()).createRenewalOrder(any(), anyString(), anyLong());
    }

    @Test
    void handleInvoicePaid_cancelledStatus_skipsOrderCreation() {
        Subscription sub = makeSubscription(SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));

        service.handleInvoicePaid("in_42", "sub_1", 1500L);

        verify(orderService, never()).createRenewalOrder(any(), anyString(), anyLong());
    }

    // ─── handleSubscriptionUpdated ────────────────────────────────────────────

    @Test
    void handleSubscriptionUpdated_syncsStatusFromStripe() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(stripeResult("canceled"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleSubscriptionUpdated("sub_1");

        assertEquals(SubscriptionStatus.CANCELLED, sub.getStatus());
        assertNotNull(sub.getCancelledAt());
        assertNull(sub.getNextBillingAt());
    }

    @Test
    void handleSubscriptionUpdated_unknownSubscription_isNoop() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_unknown")).thenReturn(Optional.empty());

        service.handleSubscriptionUpdated("sub_unknown");

        verify(paymentService, never()).retrieveSubscription(any());
    }

    // ─── handleSetupIntentSucceeded ───────────────────────────────────────────

    @Test
    void handleSetupIntentSucceeded_savesPaymentMethod() {
        User user = makeUser(TestIds.uuid(1));
        when(savedPaymentMethodRepository.findByStripePaymentMethodId("pm_new")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(user));
        when(paymentService.retrievePaymentMethod("pm_new"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_new", "cus_123", "visa", "4242", 12, 2030));
        when(savedPaymentMethodRepository.findByUserIdAndIsDefaultTrue(any())).thenReturn(Optional.empty());
        when(savedPaymentMethodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleSetupIntentSucceeded("cus_123", "pm_new");

        verify(savedPaymentMethodRepository).save(any(SavedPaymentMethod.class));
    }

    @Test
    void handleSetupIntentSucceeded_idempotent_skipsIfAlreadySaved() {
        when(savedPaymentMethodRepository.findByStripePaymentMethodId("pm_existing"))
                .thenReturn(Optional.of(new SavedPaymentMethod()));

        service.handleSetupIntentSucceeded("cus_123", "pm_existing");

        verify(savedPaymentMethodRepository, never()).save(any());
    }

    // ─── listForUser & get ────────────────────────────────────────────────────

    @Test
    void listForUser_returnsMappedList() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findAllByUserIdOrderByCreatedAtDesc(TestIds.uuid(1)))
                .thenReturn(List.of(sub));

        List<SubscriptionResponse> result = service.listForUser(TestIds.uuid(1));

        assertEquals(1, result.size());
        assertEquals(SubscriptionStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void get_returnsResponse() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1)))
                .thenReturn(Optional.of(sub));

        SubscriptionResponse res = service.get(TestIds.uuid(1), TestIds.uuid(99));

        assertEquals(SubscriptionStatus.ACTIVE, res.getStatus());
    }

    @Test
    void get_notFound_throwsResourceNotFound() {
        when(subscriptionRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(TestIds.uuid(1), TestIds.uuid(99)));
    }

    // ─── createSetupIntent ────────────────────────────────────────────────────

    @Test
    void createSetupIntent_returnsSetupIntentResponse() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(paymentService.createSetupIntent("cus_123"))
                .thenReturn(new PaymentService.SetupIntentResult("si_abc", "seti_secret", "cus_123"));

        SetupIntentResponse res = service.createSetupIntent(TestIds.uuid(1));

        assertEquals("si_abc", res.getSetupIntentId());
        assertEquals("seti_secret", res.getClientSecret());
    }

    // ─── listPaymentMethods ───────────────────────────────────────────────────

    @Test
    void listPaymentMethods_returnsMappedList() {
        SavedPaymentMethod spm = new SavedPaymentMethod();
        spm.setId(TestIds.uuid(55));
        spm.setStripePaymentMethodId("pm_abc");
        spm.setBrand("mastercard");
        spm.setLast4("1234");
        spm.setExpMonth(6);
        spm.setExpYear(2028);
        when(savedPaymentMethodRepository.findAllByUserId(TestIds.uuid(1))).thenReturn(List.of(spm));

        var result = service.listPaymentMethods(TestIds.uuid(1));

        assertEquals(1, result.size());
        assertEquals("mastercard", result.get(0).getBrand());
    }

    // ─── detachPaymentMethod ──────────────────────────────────────────────────

    @Test
    void detachPaymentMethod_happyPath_deletesFromRepo() {
        User owner = makeUser(TestIds.uuid(1));
        SavedPaymentMethod spm = new SavedPaymentMethod();
        spm.setId(TestIds.uuid(55));
        spm.setStripePaymentMethodId("pm_test");
        spm.setUser(owner);
        when(savedPaymentMethodRepository.findById(TestIds.uuid(55))).thenReturn(Optional.of(spm));

        service.detachPaymentMethod(TestIds.uuid(1), TestIds.uuid(55));

        verify(paymentService).detachPaymentMethod("pm_test");
        verify(savedPaymentMethodRepository).delete(spm);
    }

    @Test
    void detachPaymentMethod_differentOwner_throwsResourceNotFound() {
        User owner = makeUser(TestIds.uuid(1));
        SavedPaymentMethod spm = new SavedPaymentMethod();
        spm.setId(TestIds.uuid(55));
        spm.setUser(owner);
        when(savedPaymentMethodRepository.findById(TestIds.uuid(55))).thenReturn(Optional.of(spm));

        assertThrows(ResourceNotFoundException.class,
                () -> service.detachPaymentMethod(TestIds.uuid(2), TestIds.uuid(55)));
        verify(savedPaymentMethodRepository, never()).delete(any());
    }

    @Test
    void detachPaymentMethod_stripeFailure_stillDeletesLocal() {
        User owner = makeUser(TestIds.uuid(1));
        SavedPaymentMethod spm = new SavedPaymentMethod();
        spm.setId(TestIds.uuid(55));
        spm.setStripePaymentMethodId("pm_test");
        spm.setUser(owner);
        when(savedPaymentMethodRepository.findById(TestIds.uuid(55))).thenReturn(Optional.of(spm));
        doThrow(new RuntimeException("Stripe unavailable")).when(paymentService).detachPaymentMethod("pm_test");

        service.detachPaymentMethod(TestIds.uuid(1), TestIds.uuid(55));

        verify(savedPaymentMethodRepository).delete(spm); // local record removed despite Stripe failure
    }

    // ─── update guards ────────────────────────────────────────────────────────

    @Test
    void update_cancelledSubscription_throwsConflict() {
        Subscription sub = makeSubscription(SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));

        assertThrows(ConflictException.class,
                () -> service.update(TestIds.uuid(1), TestIds.uuid(99), new UpdateSubscriptionRequest()));
        verifyNoInteractions(paymentService);
    }

    @Test
    void update_sameQuantity_skipsStripeQuantityCall() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        // item quantity is already 1 — sending quantity=1 should not call Stripe
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setQuantity(1); // same as existing

        service.update(TestIds.uuid(1), TestIds.uuid(99), req);

        verify(paymentService, never()).updateSubscriptionQuantity(any(), any(), anyInt());
        verify(paymentService, never()).swapSubscriptionPrice(any(), any(), any(), anyInt());
    }

    // ─── handleSubscriptionUpdated — branch coverage ─────────────────────────

    @Test
    void handleSubscriptionUpdated_activeStatus_setsNextBillingAt() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleSubscriptionUpdated("sub_1");

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertNotNull(sub.getNextBillingAt());
        assertNull(sub.getCancelledAt());
    }

    @Test
    void handleSubscriptionUpdated_cancelledAtAlreadySet_doesNotOverwrite() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        Instant originalCancelledAt = Instant.now().minusSeconds(3600);
        sub.setCancelledAt(originalCancelledAt);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(stripeResult("canceled"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleSubscriptionUpdated("sub_1");

        // cancelledAt already set — must not be overwritten
        assertEquals(originalCancelledAt, sub.getCancelledAt());
    }

    // ─── handleInvoicePaid — additional branches ─────────────────────────────

    @Test
    void handleInvoicePaid_blankSubscriptionId_isNoop() {
        service.handleInvoicePaid("in_42", "  ", 1500L);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void handleInvoicePaid_nullSubscriptionId_isNoop() {
        service.handleInvoicePaid("in_42", null, 1500L);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void handleInvoicePaid_unknownSubscription_isNoop() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_unknown"))
                .thenReturn(Optional.empty());

        service.handleInvoicePaid("in_99", "sub_unknown", 1000L);

        verifyNoInteractions(orderService);
    }

    @Test
    void handleInvoicePaid_happyPath_createsRenewalOrderAndUpdatesStatus() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(orderRepository.findByStripeInvoiceId("in_42")).thenReturn(Optional.empty());
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleInvoicePaid("in_42", "sub_1", 1500L);

        verify(orderService).createRenewalOrder(eq(sub), eq("in_42"), eq(1500L));
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertNull(sub.getPastDueSince());
        assertFalse(sub.isSkipNextCycle());
    }

    @Test
    void handleInvoicePaid_createRenewalOrderThrows_returnEarlyNoStatusUpdate() {
        Subscription sub = makeSubscription(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        doThrow(new RuntimeException("order creation failed"))
                .when(orderService).createRenewalOrder(any(), any(), anyLong());

        service.handleInvoicePaid("in_42", "sub_1", 1500L);

        // returned early — status must NOT have been updated to ACTIVE
        assertEquals(SubscriptionStatus.PAST_DUE, sub.getStatus());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleInvoicePaid_loyaltyServiceThrows_continuesAndSavesSub() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(orderRepository.findByStripeInvoiceId("in_42"))
                .thenReturn(Optional.of(new backend.models.core.Order()));
        doThrow(new RuntimeException("loyalty error"))
                .when(loyaltyService).recordOrderEarn(any(), any());
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // must not propagate loyalty failure
        assertDoesNotThrow(() -> service.handleInvoicePaid("in_42", "sub_1", 1500L));

        verify(subscriptionRepository).save(any());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
    }

    // ─── handleInvoicePaymentFailed ───────────────────────────────────────────

    @Test
    void handleInvoicePaymentFailed_nullSubscriptionId_isNoop() {
        service.handleInvoicePaymentFailed("in_42", null);

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void handleInvoicePaymentFailed_unknownSubscription_isNoop() {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_unknown"))
                .thenReturn(Optional.empty());

        service.handleInvoicePaymentFailed("in_42", "sub_unknown");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void handleInvoicePaymentFailed_happyPath_marksPastDueAndSetsPastDueSince() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleInvoicePaymentFailed("in_42", "sub_1");

        assertEquals(SubscriptionStatus.PAST_DUE, sub.getStatus());
        assertNotNull(sub.getPastDueSince());
    }

    @Test
    void handleInvoicePaymentFailed_pastDueSinceAlreadySet_doesNotOverwrite() {
        Subscription sub = makeSubscription(SubscriptionStatus.PAST_DUE);
        Instant original = Instant.now().minusSeconds(86400);
        sub.setPastDueSince(original);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleInvoicePaymentFailed("in_42", "sub_1");

        assertEquals(original, sub.getPastDueSince());
    }

    // ─── handleSetupIntentSucceeded — additional branches ────────────────────

    @Test
    void handleSetupIntentSucceeded_nullParams_isNoop() {
        service.handleSetupIntentSucceeded(null, null);

        verifyNoInteractions(savedPaymentMethodRepository);
    }

    @Test
    void handleSetupIntentSucceeded_userNotFound_isNoop() {
        when(savedPaymentMethodRepository.findByStripePaymentMethodId("pm_new")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_unknown")).thenReturn(Optional.empty());

        service.handleSetupIntentSucceeded("cus_unknown", "pm_new");

        verify(savedPaymentMethodRepository, never()).save(any());
    }

    @Test
    void handleSetupIntentSucceeded_pmRetrievalFails_isNoop() {
        User user = makeUser(TestIds.uuid(1));
        when(savedPaymentMethodRepository.findByStripePaymentMethodId("pm_err")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("Stripe timeout"))
                .when(paymentService).retrievePaymentMethod("pm_err");

        assertDoesNotThrow(() -> service.handleSetupIntentSucceeded("cus_123", "pm_err"));

        verify(savedPaymentMethodRepository, never()).save(any());
    }

    @Test
    void handleSetupIntentSucceeded_existingDefaultPresent_savesAsNonDefault() {
        User user = makeUser(TestIds.uuid(1));
        when(savedPaymentMethodRepository.findByStripePaymentMethodId("pm_new")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(user));
        when(paymentService.retrievePaymentMethod("pm_new"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_new", "cus_123", "visa", "4242", 12, 2030));
        when(savedPaymentMethodRepository.findByUserIdAndIsDefaultTrue(any()))
                .thenReturn(Optional.of(new SavedPaymentMethod())); // existing default
        when(savedPaymentMethodRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleSetupIntentSucceeded("cus_123", "pm_new");

        ArgumentCaptor<SavedPaymentMethod> captor = ArgumentCaptor.forClass(SavedPaymentMethod.class);
        verify(savedPaymentMethodRepository).save(captor.capture());
        assertFalse(captor.getValue().isDefault());
    }

    // ─── create — zero price guard ────────────────────────────────────────────

    @Test
    void create_zeroPriceProduct_throwsBadRequest() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(TestIds.uuid(10), true, null);
        product.setPrice(BigDecimal.ZERO); // zero price → unitAmountCents = 0 → throws
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class,
                () -> service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10))));
    }

    @Test
    void create_retrievePaymentMethodThrows_throwsBadRequest() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(TestIds.uuid(10), true, null);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(product));
        doThrow(new RuntimeException("Stripe error")).when(paymentService).retrievePaymentMethod("pm_test");

        assertThrows(BadRequestException.class,
                () -> service.create(TestIds.uuid(1), makeCreateRequest(TestIds.uuid(10))));
    }

    // ─── update — empty items guard ───────────────────────────────────────────

    @Test
    void update_emptyItems_throwsConflict() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        sub.setItems(new java.util.ArrayList<>());
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1)))
                .thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Even a no-op update request should hit the empty-items check before qty comparison
        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setBillingInterval(BillingInterval.WEEK);

        assertThrows(ConflictException.class,
                () -> service.update(TestIds.uuid(1), TestIds.uuid(99), req));
    }

    // ─── update — variant in price swap ──────────────────────────────────────

    @Test
    void update_withVariantId_usesVariantPriceInSwap() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        ProductVariant variant = makeVariant(TestIds.uuid(40), sub.getItems().get(0).getProduct(), true);

        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));
        when(variantRepository.findByIdAndProductId(TestIds.uuid(40), TestIds.uuid(10))).thenReturn(Optional.of(variant));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_v", 1200L, "usd"));
        when(paymentService.swapSubscriptionPrice("sub_1", "si_1", "price_v", 1)).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setVariantId(TestIds.uuid(40));

        service.update(TestIds.uuid(1), TestIds.uuid(99), req);

        verify(paymentService).swapSubscriptionPrice("sub_1", "si_1", "price_v", 1);
    }

    // ─── mapStripeStatus — unmapped branches ─────────────────────────────────

    @Test
    void handleSubscriptionUpdated_trialingStatus_mapsToActive() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(
                new PaymentService.SubscriptionResult("sub_1", "cus_1", "trialing", "in_1",
                        Instant.now(), Instant.now().plusSeconds(86400 * 14), "pm_test", "si_1"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleSubscriptionUpdated("sub_1");

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
    }

    @Test
    void handleSubscriptionUpdated_incompleteExpiredStatus_mapsToExpired() {
        Subscription sub = makeSubscription(SubscriptionStatus.INCOMPLETE);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(
                new PaymentService.SubscriptionResult("sub_1", "cus_1", "incomplete_expired", "in_1",
                        Instant.now(), Instant.now().plusSeconds(86400), "pm_test", "si_1"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleSubscriptionUpdated("sub_1");

        assertEquals(SubscriptionStatus.EXPIRED, sub.getStatus());
    }

    @Test
    void handleSubscriptionUpdated_unknownStatus_mapsToIncomplete() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(
                new PaymentService.SubscriptionResult("sub_1", "cus_1", "some_future_status", "in_1",
                        Instant.now(), Instant.now().plusSeconds(86400), "pm_test", "si_1"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleSubscriptionUpdated("sub_1");

        assertEquals(SubscriptionStatus.INCOMPLETE, sub.getStatus());
    }

    // ─── requireMutable — EXPIRED status ─────────────────────────────────────

    @Test
    void update_expiredSubscription_throwsConflict() {
        Subscription sub = makeSubscription(SubscriptionStatus.EXPIRED);
        when(subscriptionRepository.findByIdAndUserId(TestIds.uuid(99), TestIds.uuid(1))).thenReturn(Optional.of(sub));

        assertThrows(ConflictException.class,
                () -> service.update(TestIds.uuid(1), TestIds.uuid(99), new UpdateSubscriptionRequest()));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private User makeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@example.com");
        u.setFirstName("Test");
        u.setLastName("User");
        return u;
    }

    private Product makeProduct(UUID id, boolean subscribable, String allowedIntervals) {
        Product p = new Product();
        p.setId(id);
        p.setName("Test Product");
        p.setPrice(BigDecimal.valueOf(10));
        p.setCurrency("USD");
        p.setStatus(backend.models.enums.ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        p.setSubscribable(subscribable);
        p.setSubscriptionIntervals(allowedIntervals);
        Company company = new Company();
        company.setId(TestIds.uuid(1010));
        p.setCompany(company);
        return p;
    }

    private ProductVariant makeVariant(UUID id, Product product, boolean purchasable) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setPrice(BigDecimal.valueOf(12));
        variant.setPurchasable(purchasable);
        return variant;
    }

    private CreateSubscriptionRequest makeCreateRequest(UUID productId) {
        CreateSubscriptionRequest req = new CreateSubscriptionRequest();
        req.setProductId(productId);
        req.setQuantity(1);
        req.setBillingInterval(BillingInterval.MONTH);
        req.setIntervalCount(1);
        req.setPaymentMethodId("pm_test");
        req.setCurrency("USD");
        ShippingAddressRequest addr = new ShippingAddressRequest();
        addr.setName("T User");
        addr.setStreet("1 Main St");
        addr.setCity("Boston");
        addr.setPostalCode("02110");
        addr.setCountry("US");
        req.setShippingAddress(addr);
        return req;
    }

    private Subscription makeSubscription(SubscriptionStatus status) {
        User user = makeUser(TestIds.uuid(1));
        Product product = makeProduct(TestIds.uuid(10), true, null);

        Subscription sub = new Subscription();
        sub.setId(TestIds.uuid(99));
        sub.setUser(user);
        sub.setCompany(product.getCompany());
        sub.setStripeSubscriptionId("sub_1");
        sub.setStripeCustomerId("cus_1");
        sub.setStripePriceId("price_1");
        sub.setStripePaymentMethodId("pm_test");
        sub.setStatus(status);
        sub.setBillingInterval(BillingInterval.MONTH);
        sub.setIntervalCount(1);
        sub.setCurrentPeriodStart(Instant.now().minusSeconds(86400));
        sub.setCurrentPeriodEnd(Instant.now().plusSeconds(86400 * 29));
        sub.setNextBillingAt(sub.getCurrentPeriodEnd());
        sub.setCurrency("USD");
        sub.setUnitAmountCents(1000L);

        SubscriptionItem item = new SubscriptionItem();
        item.setId(TestIds.uuid(1));
        item.setSubscription(sub);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPriceCents(1000L);
        item.setStripeSubscriptionItemId("si_1");
        sub.setItems(new java.util.ArrayList<>(List.of(item)));
        return sub;
    }

    private PaymentService.SubscriptionResult stripeResult(String status) {
        return new PaymentService.SubscriptionResult(
                "sub_1", "cus_1", status, "in_42",
                Instant.now(), Instant.now().plusSeconds(86400 * 30), "pm_test", "si_1");
    }
}
