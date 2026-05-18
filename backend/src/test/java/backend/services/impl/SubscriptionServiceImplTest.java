package backend.services.impl;

import java.util.UUID;
import backend.testutil.TestIds;
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
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.SavedPaymentMethodRepository;
import backend.repositories.OrderRepository;
import backend.repositories.SubscriptionRepository;
import backend.repositories.UserRepository;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.payments.PaymentService;
import backend.services.impl.subscriptions.SubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
        subscriptionRepository = mock(SubscriptionRepository.class);
        savedPaymentMethodRepository = mock(SavedPaymentMethodRepository.class);
        userRepository = mock(UserRepository.class);
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        paymentService = mock(PaymentService.class);
        orderService = mock(OrderService.class);
        orderRepository = mock(OrderRepository.class);
        loyaltyService = mock(LoyaltyService.class);
        activityEventPublisher = mock(ActivityEventPublisher.class);

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
        Product product = makeProduct(10L, false, null);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class,
                () -> service.create(1L, makeCreateRequest(10L)));
    }

    @Test
    void create_rejectsDisallowedInterval() {
        User user = makeUser(TestIds.uuid(1));
        Product product = makeProduct(10L, true, "MONTH:1,MONTH:3");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        CreateSubscriptionRequest req = makeCreateRequest(10L);
        req.setBillingInterval(BillingInterval.WEEK);
        req.setIntervalCount(1);

        assertThrows(BadRequestException.class, () -> service.create(1L, req));
    }

    @Test
    void create_rejectsUnavailableProduct() {
        User user = makeUser(TestIds.uuid(1));
        Product product = makeProduct(10L, true, null);
        product.setListed(false);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThrows(BadRequestException.class,
                () -> service.create(1L, makeCreateRequest(10L)));
    }

    @Test
    void create_rejectsUnavailableVariant() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(10L, true, null);
        ProductVariant variant = makeVariant(55L, product, false);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductId(55L, 10L)).thenReturn(Optional.of(variant));

        CreateSubscriptionRequest req = makeCreateRequest(10L);
        req.setVariantId(55L);

        assertThrows(BadRequestException.class, () -> service.create(1L, req));
    }

    @Test
    void create_acceptsAllowedIntervalAndPersistsSubscription() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(10L, true, "MONTH:1");
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        when(paymentService.retrievePaymentMethod("pm_test"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_test", "cus_123",
                        "visa", "4242", 12, 2030));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_1", 1000L, "usd"));
        when(paymentService.createSubscription(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.SubscriptionResult(
                        "sub_1", "cus_123", "active", "in_1",
                        Instant.now(), Instant.now().plusSeconds(86400 * 30),
                        "pm_test", "si_1"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.create(1L, makeCreateRequest(10L));

        assertEquals(SubscriptionStatus.ACTIVE, res.getStatus());
        assertEquals(BillingInterval.MONTH, res.getBillingInterval());
        assertEquals(1, res.getItems().size());
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void create_rejectsPaymentMethodOwnedByDifferentCustomer() {
        User user = makeUser(TestIds.uuid(1));
        user.setStripeCustomerId("cus_123");
        Product product = makeProduct(10L, true, null);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(paymentService.retrievePaymentMethod("pm_test"))
                .thenReturn(new PaymentService.PaymentMethodInfo("pm_test", "cus_OTHER",
                        "visa", "4242", 12, 2030));

        assertThrows(BadRequestException.class, () -> service.create(1L, makeCreateRequest(10L)));
    }

    // ─── pause / resume ─────────────────────────────────────────────────────

    @Test
    void pause_onlyActiveSubscription() {
        Subscription sub = makeSubscription(SubscriptionStatus.PAUSED);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        assertThrows(ConflictException.class, () -> service.pause(1L, 99L));
    }

    @Test
    void pause_callsStripeAndPersistsPausedStatus() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        when(paymentService.pauseSubscription("sub_1")).thenReturn(stripeResult("paused"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.pause(1L, 99L);

        assertEquals(SubscriptionStatus.PAUSED, res.getStatus());
        assertNotNull(res.getPausedAt());
        verify(paymentService).pauseSubscription("sub_1");
    }

    @Test
    void resume_onlyPausedSubscription() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        assertThrows(ConflictException.class, () -> service.resume(1L, 99L));
    }

    // ─── skipNext ───────────────────────────────────────────────────────────

    @Test
    void skipNext_setsFlagAndAdvancesPeriod() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        when(paymentService.skipNextCycle("sub_1", BillingInterval.MONTH, 1))
                .thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.skipNext(1L, 99L);
        assertTrue(res.isSkipNextCycle());
    }

    // ─── update (qty only vs price-changing) ────────────────────────────────

    @Test
    void update_quantityOnlyUsesUpdateQuantityPath() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        when(paymentService.updateSubscriptionQuantity("sub_1", "si_1", 3))
                .thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setQuantity(3);

        service.update(1L, 99L, req);

        verify(paymentService).updateSubscriptionQuantity("sub_1", "si_1", 3);
        verify(paymentService, never()).swapSubscriptionPrice(any(), any(), any(), anyInt());
    }

    @Test
    void update_intervalChangeCreatesNewPriceAndSwaps() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_2", 1000L, "usd"));
        when(paymentService.swapSubscriptionPrice("sub_1", "si_1", "price_2", 2))
                .thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setBillingInterval(BillingInterval.WEEK);
        req.setIntervalCount(2);
        req.setQuantity(2);

        service.update(1L, 99L, req);

        verify(paymentService).swapSubscriptionPrice("sub_1", "si_1", "price_2", 2);
    }

    @Test
    void update_productSwapRejectsUnavailableProduct() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        Product replacement = makeProduct(20L, true, null);
        replacement.setPurchasable(false);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        when(productRepository.findById(20L)).thenReturn(Optional.of(replacement));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setProductId(20L);

        assertThrows(BadRequestException.class, () -> service.update(1L, 99L, req));
    }

    @Test
    void update_productSwapRefreshesSubscriptionCompany() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        Product replacement = makeProduct(20L, true, null);
        Company replacementCompany = new Company();
        replacementCompany.setId(77L);
        replacement.setCompany(replacementCompany);

        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        when(productRepository.findById(20L)).thenReturn(Optional.of(replacement));
        when(paymentService.createRecurringPrice(anyLong(), anyString(), any(), anyInt(), anyString(), any()))
                .thenReturn(new PaymentService.PriceResult("price_2", 1000L, "usd"));
        when(paymentService.swapSubscriptionPrice("sub_1", "si_1", "price_2", 1))
                .thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubscriptionRequest req = new UpdateSubscriptionRequest();
        req.setProductId(20L);

        service.update(1L, 99L, req);

        assertEquals(77L, sub.getCompany().getId());
        assertEquals(20L, sub.getItems().get(0).getProduct().getId());
    }

    // ─── cancel ─────────────────────────────────────────────────────────────

    @Test
    void cancel_atPeriodEndKeepsActiveStatusButFlags() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        when(paymentService.cancelSubscription("sub_1", true)).thenReturn(stripeResult("active"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.cancel(1L, 99L, true);
        assertTrue(res.isCancelAtPeriodEnd());
        assertNotEquals(SubscriptionStatus.CANCELLED, res.getStatus());
    }

    @Test
    void cancel_immediateMarksCancelledAndClearsNextBilling() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(sub));
        when(paymentService.cancelSubscription("sub_1", false)).thenReturn(stripeResult("canceled"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse res = service.cancel(1L, 99L, false);
        assertEquals(SubscriptionStatus.CANCELLED, res.getStatus());
        assertNull(res.getNextBillingAt());
        assertNotNull(res.getCancelledAt());
    }

    // ─── invoice.paid webhook (idempotency + renewal order spawn) ───────────

    @Test
    void handleInvoicePaid_createsOrderAndMarksActive() {
        Subscription sub = makeSubscription(SubscriptionStatus.PAST_DUE);
        Company oldCompany = new Company();
        oldCompany.setId(10L);
        sub.setCompany(oldCompany);
        Company currentCompany = new Company();
        currentCompany.setId(20L);
        sub.getItems().get(0).getProduct().setCompany(currentCompany);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1"))
                .thenReturn(Optional.of(sub));
        when(paymentService.retrieveSubscription("sub_1")).thenReturn(stripeResult("active"));
        when(orderRepository.findByStripeInvoiceId("in_42")).thenReturn(Optional.of(new backend.models.core.Order()));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleInvoicePaid("in_42", "sub_1", 1500L);

        verify(orderService).createRenewalOrder(eq(sub), eq("in_42"), eq(1500L));
        verify(loyaltyService).recordOrderEarn(any(), eq(20L));
        assertEquals(20L, sub.getCompany().getId());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertFalse(sub.isSkipNextCycle());
    }

    @Test
    void handleInvoicePaid_unknownSubscriptionIsNoop() {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_unknown"))
                .thenReturn(Optional.empty());

        service.handleInvoicePaid("in_42", "sub_unknown", 100L);

        verify(orderService, never()).createRenewalOrder(any(), anyString(), anyLong());
    }

    @Test
    void handleInvoicePaymentFailed_marksPastDue() {
        Subscription sub = makeSubscription(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1"))
                .thenReturn(Optional.of(sub));
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

    private Product makeProduct(long id, boolean subscribable, String allowedIntervals) {
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
        company.setId(id + 1000);
        p.setCompany(company);
        return p;
    }

    private ProductVariant makeVariant(long id, Product product, boolean purchasable) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setPrice(BigDecimal.valueOf(12));
        variant.setPurchasable(purchasable);
        return variant;
    }

    private CreateSubscriptionRequest makeCreateRequest(long productId) {
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
        Product product = makeProduct(10L, true, null);

        Subscription sub = new Subscription();
        sub.setId(99L);
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
        item.setId(1L);
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
                Instant.now(), Instant.now().plusSeconds(86400 * 30),
                "pm_test", "si_1");
    }
}
