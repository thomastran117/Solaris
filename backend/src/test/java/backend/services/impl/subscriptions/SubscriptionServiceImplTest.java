package backend.services.impl.subscriptions;

import backend.dtos.requests.subscription.CreateSubscriptionRequest;
import backend.dtos.requests.subscription.ShippingAddressRequest;
import backend.dtos.requests.subscription.UpdateSubscriptionRequest;
import backend.dtos.responses.subscription.SubscriptionResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
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
