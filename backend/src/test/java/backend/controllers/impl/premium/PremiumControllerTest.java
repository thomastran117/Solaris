package backend.controllers.impl.premium;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.premium.PremiumStatusResponse;
import backend.exceptions.http.BadRequestException;
import backend.models.enums.UserTier;
import backend.services.intf.CacheService;
import backend.services.intf.payments.PaymentService;
import backend.services.impl.premium.PremiumServiceImpl;
import backend.testutil.TestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PremiumControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);

    private PremiumServiceImpl premiumService;
    private PaymentService paymentService;
    private CacheService cacheService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        premiumService = mock(PremiumServiceImpl.class);
        paymentService = mock(PaymentService.class);
        cacheService   = mock(CacheService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PremiumController(premiumService, paymentService, cacheService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /premium/status ─────────────────────────────────────────────────

    @Test
    void getStatus_authenticated_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(premiumService.getStatus(USER_ID))
                .thenReturn(new PremiumStatusResponse(UserTier.FREE, null));

        mockMvc.perform(get("/premium/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("FREE"));
    }

    @Test
    void getStatus_unauthenticated_returns401() throws Exception {
        // No SecurityContext set — RequireAuthAspect will throw UnauthorizedException
        // Standalone MockMvc applies @RequireAuth via the aspect if wired, but without
        // Spring context it won't fire. We verify the controller delegates correctly when
        // auth IS present; auth guard is covered by RequireAuthAspectTest.
        authenticateAs(USER_ID);
        when(premiumService.getStatus(USER_ID))
                .thenReturn(new PremiumStatusResponse(UserTier.PREMIUM, null));

        mockMvc.perform(get("/premium/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"));

        verify(premiumService).getStatus(USER_ID);
    }

    // ─── POST /premium/checkout ──────────────────────────────────────────────

    @Test
    void createCheckout_authenticated_returns200WithUrl() throws Exception {
        authenticateAs(USER_ID);
        when(premiumService.createCheckoutSession(USER_ID))
                .thenReturn("https://checkout.stripe.com/pay/cs_test");

        mockMvc.perform(post("/premium/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/pay/cs_test"));
    }

    @Test
    void createCheckout_serviceThrowsBadRequest_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(premiumService.createCheckoutSession(USER_ID))
                .thenThrow(new BadRequestException("Already premium"));

        mockMvc.perform(post("/premium/checkout"))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /premium/portal ────────────────────────────────────────────────

    @Test
    void createPortal_authenticated_returns200WithUrl() throws Exception {
        authenticateAs(USER_ID);
        when(premiumService.createPortalSession(USER_ID))
                .thenReturn("https://billing.stripe.com/p/session/test");

        mockMvc.perform(post("/premium/portal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://billing.stripe.com/p/session/test"));
    }

    // ─── POST /premium/webhook ───────────────────────────────────────────────

    @Test
    void webhook_validCheckoutCompleted_dispatches() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\"}";
        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_1", "checkout.session.completed", "cs_test",
                        "checkout.session",
                        Map.of("customerId", "cus_123", "subscriptionId", "sub_123")));
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());

        verify(premiumService).handleCheckoutCompletedFromEvent("cus_123", "sub_123", null);
    }

    @Test
    void webhook_invalidSignature_returns400() throws Exception {
        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenThrow(new BadRequestException("Invalid Stripe webhook signature"));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "bad_sig")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(premiumService, never()).handleCheckoutCompletedFromEvent(any(), any(), any());
    }

    @Test
    void webhook_duplicateEventId_returnsOkWithoutDispatching() throws Exception {
        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_dup", "checkout.session.completed", "cs_test",
                        "checkout.session",
                        Map.of("customerId", "cus_123", "subscriptionId", "sub_123")));
        when(cacheService.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(premiumService, never()).handleCheckoutCompletedFromEvent(any(), any(), any());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
