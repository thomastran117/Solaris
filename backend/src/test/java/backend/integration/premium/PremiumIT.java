package backend.integration.premium;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import backend.models.enums.UserTier;
import backend.services.intf.payments.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PremiumIT extends AbstractIntegrationIT {

    @MockitoBean private PaymentService paymentService;

    // ── GET /premium/status ───────────────────────────────────────────────────

    @Test
    void getStatus_freeTierUser_returnsFreeWithNullExpiry() throws Exception {
        User user = createActiveUser("premium-status-free@example.com", "Password1!");

        mockMvc.perform(get("/premium/status")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.premiumExpiresAt").value(nullValue()));
    }

    @Test
    void getStatus_premiumTierUser_returnsPremiumWithExpiry() throws Exception {
        User user = createActiveUser("premium-status-premium@example.com", "Password1!");
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        user.setTier(UserTier.PREMIUM);
        user.setPremiumExpiresAt(expiresAt);
        userRepository.save(user);

        mockMvc.perform(get("/premium/status")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tier").value("PREMIUM"))
                .andExpect(jsonPath("$.data.premiumExpiresAt").value(not(emptyOrNullString())));
    }

    @Test
    void getStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/premium/status")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /premium/checkout ────────────────────────────────────────────────

    @Test
    void createCheckout_userWithoutStripeCustomer_createsCustomerAndReturnsUrl() throws Exception {
        User user = createActiveUser("premium-checkout-new@example.com", "Password1!");
        when(paymentService.createCustomer(any(), any(), any()))
                .thenReturn(new PaymentService.CustomerResult("cus_new_123", user.getEmail(), ""));
        when(paymentService.createCheckoutSession(any(), any(), any(), any()))
                .thenReturn("https://checkout.stripe.com/pay/cs_test");

        mockMvc.perform(post("/premium/checkout")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://checkout.stripe.com/pay/cs_test"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("cus_new_123", reloaded.getStripeCustomerId());
    }

    @Test
    void createCheckout_userWithExistingStripeCustomer_doesNotCreateNewCustomer() throws Exception {
        User user = createActiveUser("premium-checkout-existing@example.com", "Password1!");
        user.setStripeCustomerId("cus_existing_123");
        userRepository.save(user);
        when(paymentService.createCheckoutSession(any(), any(), any(), any()))
                .thenReturn("https://checkout.stripe.com/pay/cs_test2");

        mockMvc.perform(post("/premium/checkout")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://checkout.stripe.com/pay/cs_test2"));

        verify(paymentService, never()).createCustomer(any(), any(), any());
    }

    @Test
    void createCheckout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/premium/checkout")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /premium/portal ──────────────────────────────────────────────────

    @Test
    void createPortal_userWithStripeCustomer_returnsUrl() throws Exception {
        User user = createActiveUser("premium-portal-ok@example.com", "Password1!");
        user.setStripeCustomerId("cus_portal_123");
        userRepository.save(user);
        when(paymentService.createPortalSession(any(), any()))
                .thenReturn("https://billing.stripe.com/p/session/test");

        mockMvc.perform(post("/premium/portal")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://billing.stripe.com/p/session/test"));
    }

    @Test
    void createPortal_userWithoutStripeCustomer_returns400() throws Exception {
        User user = createActiveUser("premium-portal-nocustomer@example.com", "Password1!");

        mockMvc.perform(post("/premium/portal")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPortal_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/premium/portal")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /premium/webhook ─────────────────────────────────────────────────

    @Test
    void webhook_checkoutSessionCompleted_upgradesUserToPremium() throws Exception {
        User user = createActiveUser("premium-webhook-checkout@example.com", "Password1!");
        user.setStripeCustomerId("cus_webhook_1");
        userRepository.save(user);

        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_checkout_1", "checkout.session.completed", "cs_test",
                        "checkout.session",
                        Map.of("customerId", "cus_webhook_1", "subscriptionId", "sub_webhook_1")));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UserTier.PREMIUM, reloaded.getTier());
        org.junit.jupiter.api.Assertions.assertEquals("sub_webhook_1", reloaded.getPremiumStripeSubscriptionId());
    }

    @Test
    void webhook_duplicateEventId_doesNotReprocess() throws Exception {
        User user = createActiveUser("premium-webhook-dup@example.com", "Password1!");
        user.setStripeCustomerId("cus_dup_1");
        userRepository.save(user);

        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_dup_1", "checkout.session.completed", "cs_test",
                        "checkout.session",
                        Map.of("customerId", "cus_dup_1", "subscriptionId", "sub_A")))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_dup_1", "checkout.session.completed", "cs_test",
                        "checkout.session",
                        Map.of("customerId", "cus_dup_1", "subscriptionId", "sub_B")));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("sub_A", reloaded.getPremiumStripeSubscriptionId());
    }

    @Test
    void webhook_subscriptionUpdatedActive_upgradesUserWithNewExpiry() throws Exception {
        User user = createActiveUser("premium-webhook-sub-active@example.com", "Password1!");
        user.setPremiumStripeSubscriptionId("sub_update_active");
        userRepository.save(user);

        Instant periodEnd = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_sub_active_1", "customer.subscription.updated", "sub_update_active",
                        "customer.subscription",
                        Map.of("subscriptionId", "sub_update_active")));
        when(paymentService.retrieveSubscription("sub_update_active"))
                .thenReturn(new PaymentService.SubscriptionResult(
                        "sub_update_active", "cus_x", "active", null, null, periodEnd, null, null));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UserTier.PREMIUM, reloaded.getTier());
        org.junit.jupiter.api.Assertions.assertEquals(periodEnd, reloaded.getPremiumExpiresAt());
    }

    @Test
    void webhook_subscriptionUpdatedCanceled_downgradesUser() throws Exception {
        User user = createActiveUser("premium-webhook-sub-canceled@example.com", "Password1!");
        user.setTier(UserTier.PREMIUM);
        user.setPremiumStripeSubscriptionId("sub_update_canceled");
        user.setPremiumExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        userRepository.save(user);

        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_sub_canceled_1", "customer.subscription.updated", "sub_update_canceled",
                        "customer.subscription",
                        Map.of("subscriptionId", "sub_update_canceled")));
        when(paymentService.retrieveSubscription("sub_update_canceled"))
                .thenReturn(new PaymentService.SubscriptionResult(
                        "sub_update_canceled", "cus_x", "canceled", null, null, null, null, null));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UserTier.FREE, reloaded.getTier());
        org.junit.jupiter.api.Assertions.assertNull(reloaded.getPremiumStripeSubscriptionId());
        org.junit.jupiter.api.Assertions.assertNull(reloaded.getPremiumExpiresAt());
    }

    @Test
    void webhook_subscriptionDeleted_downgradesUser() throws Exception {
        User user = createActiveUser("premium-webhook-sub-deleted@example.com", "Password1!");
        user.setTier(UserTier.PREMIUM);
        user.setPremiumStripeSubscriptionId("sub_delete_1");
        user.setPremiumExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        userRepository.save(user);

        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_sub_deleted_1", "customer.subscription.deleted", "sub_delete_1",
                        "customer.subscription",
                        Map.of("subscriptionId", "sub_delete_1")));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UserTier.FREE, reloaded.getTier());
        org.junit.jupiter.api.Assertions.assertNull(reloaded.getPremiumStripeSubscriptionId());
        org.junit.jupiter.api.Assertions.assertNull(reloaded.getPremiumExpiresAt());
    }

    @Test
    void webhook_invoicePaymentFailed_returns200() throws Exception {
        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_invoice_failed_1", "invoice.payment_failed", "in_test",
                        "invoice",
                        Map.of("subscriptionId", "sub_invoice_failed")));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_unknownEventType_returns200() throws Exception {
        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenReturn(new PaymentService.WebhookEvent(
                        "evt_unknown_1", "payment_intent.created", "pi_test",
                        "payment_intent", Map.of()));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_invalidSignature_returns400() throws Exception {
        when(paymentService.constructPremiumWebhookEvent(anyString(), anyString()))
                .thenThrow(new backend.exceptions.http.BadRequestException("Invalid Stripe webhook signature"));

        mockMvc.perform(post("/premium/webhook")
                        .header("Stripe-Signature", "bad_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
