package backend.services.impl.premium;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.responses.premium.PremiumStatusResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.models.enums.UserTier;
import backend.repositories.UserRepository;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.PaymentService.CustomerResult;
import backend.services.intf.payments.PaymentService.SubscriptionResult;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PremiumServiceImplTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final String STRIPE_CUSTOMER_ID = "cus_test123";
    private static final String STRIPE_PRICE_ID    = "price_premium";
    private static final String STRIPE_SUB_ID      = "sub_premium123";
    private static final String SUCCESS_URL = "http://localhost:5173/account?upgrade=success";
    private static final String CANCEL_URL  = "http://localhost:5173/account";
    private static final String PORTAL_URL  = "http://localhost:5173/account";

    private UserRepository userRepository;
    private PaymentService paymentService;
    private EnvironmentSetting env;
    private PremiumServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        paymentService = mock(PaymentService.class);
        env = buildEnv();
        service = new PremiumServiceImpl(userRepository, paymentService, env);
    }

    // ─── getStatus ───────────────────────────────────────────────────────────

    @Test
    void getStatus_freeUser_returnsFree() {
        User user = makeUser(USER_ID, UserTier.FREE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        PremiumStatusResponse resp = service.getStatus(USER_ID);

        assertEquals(UserTier.FREE, resp.tier());
        assertNull(resp.premiumExpiresAt());
    }

    @Test
    void getStatus_premiumUser_returnsPremiumWithExpiry() {
        Instant expiry = Instant.parse("2026-06-22T00:00:00Z");
        User user = makeUser(USER_ID, UserTier.PREMIUM);
        user.setPremiumExpiresAt(expiry);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        PremiumStatusResponse resp = service.getStatus(USER_ID);

        assertEquals(UserTier.PREMIUM, resp.tier());
        assertEquals(expiry, resp.premiumExpiresAt());
    }

    @Test
    void getStatus_unknownUser_throwsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getStatus(USER_ID));
    }

    // ─── createCheckoutSession ───────────────────────────────────────────────

    @Test
    void createCheckoutSession_existingStripeCustomer_skipsProvisioning() {
        User user = makeUser(USER_ID, UserTier.FREE);
        user.setStripeCustomerId(STRIPE_CUSTOMER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(paymentService.createCheckoutSession(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("https://checkout.stripe.com/pay/cs_test");

        String url = service.createCheckoutSession(USER_ID);

        assertNotNull(url);
        verify(paymentService, never()).createCustomer(any(), any(), any());
        verify(paymentService).createCheckoutSession(STRIPE_CUSTOMER_ID, STRIPE_PRICE_ID, SUCCESS_URL, CANCEL_URL);
    }

    @Test
    void createCheckoutSession_noStripeCustomer_provisionsThenCreatesSession() {
        User user = makeUser(USER_ID, UserTier.FREE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(paymentService.createCustomer(anyString(), anyString(), anyMap()))
                .thenReturn(new CustomerResult(STRIPE_CUSTOMER_ID, user.getEmail(), ""));
        when(paymentService.createCheckoutSession(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("https://checkout.stripe.com/pay/cs_test");

        service.createCheckoutSession(USER_ID);

        verify(paymentService).createCustomer(eq(user.getEmail()), anyString(), anyMap());
        verify(paymentService).createCheckoutSession(STRIPE_CUSTOMER_ID, STRIPE_PRICE_ID, SUCCESS_URL, CANCEL_URL);
        verify(userRepository, atLeastOnce()).save(user);
        assertEquals(STRIPE_CUSTOMER_ID, user.getStripeCustomerId());
    }

    // ─── createPortalSession ─────────────────────────────────────────────────

    @Test
    void createPortalSession_noStripeCustomer_throwsBadRequest() {
        User user = makeUser(USER_ID, UserTier.FREE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        assertThrows(BadRequestException.class, () -> service.createPortalSession(USER_ID));
    }

    @Test
    void createPortalSession_premiumUser_returnsPortalUrl() {
        User user = makeUser(USER_ID, UserTier.PREMIUM);
        user.setStripeCustomerId(STRIPE_CUSTOMER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(paymentService.createPortalSession(anyString(), anyString()))
                .thenReturn("https://billing.stripe.com/p/session/test");

        String url = service.createPortalSession(USER_ID);

        assertTrue(url.startsWith("https://billing.stripe.com"));
        verify(paymentService).createPortalSession(STRIPE_CUSTOMER_ID, PORTAL_URL);
    }

    // ─── handleCheckoutCompletedFromEvent ────────────────────────────────────

    @Test
    void handleCheckoutCompletedFromEvent_validCustomer_setsTierAndSubscriptionId() {
        User user = makeUser(USER_ID, UserTier.FREE);
        user.setStripeCustomerId(STRIPE_CUSTOMER_ID);
        when(userRepository.findByStripeCustomerId(STRIPE_CUSTOMER_ID)).thenReturn(Optional.of(user));

        service.handleCheckoutCompletedFromEvent(STRIPE_CUSTOMER_ID, STRIPE_SUB_ID, "1750000000");

        assertEquals(UserTier.PREMIUM, user.getTier());
        assertEquals(STRIPE_SUB_ID, user.getPremiumStripeSubscriptionId());
        assertNotNull(user.getPremiumExpiresAt());
        verify(userRepository).save(user);
    }

    @Test
    void handleCheckoutCompletedFromEvent_unknownCustomer_throwsNotFound() {
        when(userRepository.findByStripeCustomerId(anyString())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.handleCheckoutCompletedFromEvent(STRIPE_CUSTOMER_ID, STRIPE_SUB_ID, null));
    }

    // ─── handleSubscriptionUpdated ───────────────────────────────────────────

    @Test
    void handleSubscriptionUpdated_activeStatus_keepsPremium() {
        User user = makeUser(USER_ID, UserTier.PREMIUM);
        user.setPremiumStripeSubscriptionId(STRIPE_SUB_ID);
        when(userRepository.findByPremiumStripeSubscriptionId(STRIPE_SUB_ID)).thenReturn(Optional.of(user));
        when(paymentService.retrieveSubscription(STRIPE_SUB_ID))
                .thenReturn(activeSubscriptionResult());

        service.handleSubscriptionUpdated(STRIPE_SUB_ID);

        assertEquals(UserTier.PREMIUM, user.getTier());
        verify(userRepository).save(user);
    }

    @Test
    void handleSubscriptionUpdated_cancelledStatus_downgradesUser() {
        User user = makeUser(USER_ID, UserTier.PREMIUM);
        user.setPremiumStripeSubscriptionId(STRIPE_SUB_ID);
        when(userRepository.findByPremiumStripeSubscriptionId(STRIPE_SUB_ID)).thenReturn(Optional.of(user));
        when(paymentService.retrieveSubscription(STRIPE_SUB_ID))
                .thenReturn(cancelledSubscriptionResult());

        service.handleSubscriptionUpdated(STRIPE_SUB_ID);

        assertEquals(UserTier.FREE, user.getTier());
        assertNull(user.getPremiumStripeSubscriptionId());
        assertNull(user.getPremiumExpiresAt());
        verify(userRepository).save(user);
    }

    // ─── handleSubscriptionDeleted ───────────────────────────────────────────

    @Test
    void handleSubscriptionDeleted_knownSub_downgradesUser() {
        User user = makeUser(USER_ID, UserTier.PREMIUM);
        user.setPremiumStripeSubscriptionId(STRIPE_SUB_ID);
        user.setPremiumExpiresAt(Instant.now().plusSeconds(3600));
        when(userRepository.findByPremiumStripeSubscriptionId(STRIPE_SUB_ID)).thenReturn(Optional.of(user));

        service.handleSubscriptionDeleted(STRIPE_SUB_ID);

        assertEquals(UserTier.FREE, user.getTier());
        assertNull(user.getPremiumStripeSubscriptionId());
        assertNull(user.getPremiumExpiresAt());
        verify(userRepository).save(user);
    }

    @Test
    void handleSubscriptionDeleted_unknownSub_noOp() {
        when(userRepository.findByPremiumStripeSubscriptionId(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.handleSubscriptionDeleted("sub_unknown"));
        verify(userRepository, never()).save(any());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static User makeUser(UUID id, UserTier tier) {
        User u = new User();
        u.setId(id);
        u.setEmail("user@test.com");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setTier(tier);
        return u;
    }

    private static SubscriptionResult activeSubscriptionResult() {
        return new SubscriptionResult(
                STRIPE_SUB_ID, STRIPE_CUSTOMER_ID, "active", null,
                Instant.now(), Instant.now().plusSeconds(2592000), null, null);
    }

    private static SubscriptionResult cancelledSubscriptionResult() {
        return new SubscriptionResult(
                STRIPE_SUB_ID, STRIPE_CUSTOMER_ID, "canceled", null,
                Instant.now(), Instant.now(), null, null);
    }

    private static EnvironmentSetting buildEnv() {
        EnvironmentSetting env = new EnvironmentSetting();
        env.getStripe().getPremium().setPriceId(STRIPE_PRICE_ID);
        env.getStripe().getPremium().setCheckoutSuccessUrl(SUCCESS_URL);
        env.getStripe().getPremium().setCheckoutCancelUrl(CANCEL_URL);
        env.getStripe().getPremium().setPortalReturnUrl(PORTAL_URL);
        return env;
    }
}
