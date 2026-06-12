package backend.integration.subscriptions;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.Subscription;
import backend.models.core.SubscriptionItem;
import backend.models.core.User;
import backend.models.enums.BillingInterval;
import backend.models.enums.ProductStatus;
import backend.models.enums.SubscriptionStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.SubscriptionRepository;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.PaymentService.CustomerResult;
import backend.services.intf.payments.PaymentService.PaymentMethodInfo;
import backend.services.intf.payments.PaymentService.PriceResult;
import backend.services.intf.payments.PaymentService.SetupIntentResult;
import backend.services.intf.payments.PaymentService.SubscriptionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubscriptionIT extends AbstractIntegrationIT {

    @MockitoBean private PaymentService paymentService;

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;

    private static final String BASE = "/subscriptions";
    private static final Instant NOW = Instant.now();
    private static final Instant PERIOD_END = NOW.plus(30, ChronoUnit.DAYS);

    @AfterEach
    void cleanSubscriptions() {
        try { jdbcTemplate.execute("DELETE FROM subscription_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM subscriptions"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM saved_payment_methods"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Sub Co " + UUID.randomUUID());
        return companyRepository.save(c);
    }

    private Product createSubscribableProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Test Sub Product");
        p.setPrice(BigDecimal.valueOf(19.99));
        p.setStatus(ProductStatus.ACTIVE);
        p.setSubscribable(true);
        p.setPurchasable(true);
        p.setListed(true);
        return productRepository.save(p);
    }

    private Product createNonSubscribableProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Non-Sub Product");
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setStatus(ProductStatus.ACTIVE);
        p.setSubscribable(false);
        p.setPurchasable(true);
        p.setListed(true);
        return productRepository.save(p);
    }

    private Subscription createSubscriptionInDb(User user, Company company, Product product, SubscriptionStatus status) {
        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setCompany(company);
        // unique stripe ID per call
        sub.setStripeSubscriptionId("sub_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        sub.setStripeCustomerId("cus_test");
        sub.setStripePriceId("price_test");
        sub.setStripePaymentMethodId("pm_test");
        sub.setStatus(status);
        sub.setBillingInterval(BillingInterval.MONTH);
        sub.setIntervalCount(1);
        sub.setCurrentPeriodStart(NOW);
        sub.setCurrentPeriodEnd(PERIOD_END);
        sub.setNextBillingAt(PERIOD_END);
        sub.setCurrency("USD");
        sub.setUnitAmountCents(1999L);

        SubscriptionItem item = new SubscriptionItem();
        item.setSubscription(sub);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPriceCents(1999L);
        sub.getItems().add(item);

        return subscriptionRepository.save(sub);
    }

    private void stubStripeForCreate(String email) {
        when(paymentService.createCustomer(any(), any(), any()))
                .thenReturn(new CustomerResult("cus_test", email, "Test User"));
        when(paymentService.retrievePaymentMethod(any()))
                .thenReturn(new PaymentMethodInfo("pm_test", "cus_test", "visa", "4242", 12, 2030));
        when(paymentService.createRecurringPrice(anyLong(), any(), any(), anyInt(), any(), any()))
                .thenReturn(new PriceResult("price_test", 1999L, "USD"));
        when(paymentService.createSubscription(any(), any(), anyInt(), any(), any()))
                .thenReturn(new SubscriptionResult(
                        "sub_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                        "cus_test", "active", "inv_test", NOW, PERIOD_END, "pm_test", "si_test"));
    }

    private String createBody(UUID productId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId.toString());
        body.put("quantity", 1);
        body.put("billingInterval", "MONTH");
        body.put("intervalCount", 1);
        body.put("paymentMethodId", "pm_test");
        body.put("shippingAddress", Map.of(
                "name", "John Doe",
                "street", "123 Main St",
                "city", "Springfield",
                "state", "IL",
                "postalCode", "62701",
                "country", "US"
        ));
        return objectMapper.writeValueAsString(body);
    }

    // ── GET /subscriptions/payment-methods ───────────────────────────────────

    @Test
    void listPaymentMethods_returns200WithEmptyList() throws Exception {
        User user = createActiveUser("pm-list@example.com", "Password1!");

        mockMvc.perform(get(BASE + "/payment-methods")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listPaymentMethods_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/payment-methods"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /subscriptions/setup-intent ─────────────────────────────────────

    @Test
    void createSetupIntent_returns200WithClientSecret() throws Exception {
        User user = createActiveUser("setup-intent@example.com", "Password1!");
        when(paymentService.createCustomer(any(), any(), any()))
                .thenReturn(new CustomerResult("cus_si", user.getEmail(), "Test User"));
        when(paymentService.createSetupIntent("cus_si"))
                .thenReturn(new SetupIntentResult("seti_test", "seti_secret_xxx", "cus_si"));

        mockMvc.perform(post(BASE + "/setup-intent")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setupIntentId").value("seti_test"))
                .andExpect(jsonPath("$.data.clientSecret").value("seti_secret_xxx"));
    }

    @Test
    void createSetupIntent_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/setup-intent"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /subscriptions/payment-methods/{id} ────────────────────────────

    @Test
    void detachPaymentMethod_unknownId_returns404() throws Exception {
        User user = createActiveUser("detach-404@example.com", "Password1!");

        mockMvc.perform(delete(BASE + "/payment-methods/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void detachPaymentMethod_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete(BASE + "/payment-methods/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /subscriptions ───────────────────────────────────────────────────

    @Test
    void create_returns201WithSubscriptionFields() throws Exception {
        User user = createActiveUser("sub-create@example.com", "Password1!");
        Company company = createCompany(user);
        Product product = createSubscribableProduct(company);
        stubStripeForCreate(user.getEmail());

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.billingInterval").value("MONTH"))
                .andExpect(jsonPath("$.data.intervalCount").value(1))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void create_missingProductId_returns400() throws Exception {
        User user = createActiveUser("sub-noprod@example.com", "Password1!");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quantity", 1);
        body.put("billingInterval", "MONTH");
        body.put("intervalCount", 1);
        body.put("paymentMethodId", "pm_test");
        body.put("shippingAddress", Map.of("name", "J", "street", "1 St", "city", "C",
                "postalCode", "12345", "country", "US"));

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingPaymentMethodId_returns400() throws Exception {
        User user = createActiveUser("sub-nopm@example.com", "Password1!");
        Company company = createCompany(user);
        Product product = createSubscribableProduct(company);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", product.getId().toString());
        body.put("quantity", 1);
        body.put("billingInterval", "MONTH");
        body.put("intervalCount", 1);
        body.put("shippingAddress", Map.of("name", "J", "street", "1 St", "city", "C",
                "postalCode", "12345", "country", "US"));

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownProduct_returns404() throws Exception {
        User user = createActiveUser("sub-unknownprod@example.com", "Password1!");
        // createCustomer called first — stub to avoid NPE in ensureStripeCustomer
        when(paymentService.createCustomer(any(), any(), any()))
                .thenReturn(new CustomerResult("cus_test", user.getEmail(), "Test User"));

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_nonSubscribableProduct_returns400() throws Exception {
        User user = createActiveUser("sub-nonsub@example.com", "Password1!");
        Company company = createCompany(user);
        Product product = createNonSubscribableProduct(company);
        stubStripeForCreate(user.getEmail());

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(product.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        // Use a complete body so body validation doesn't fire before the security check
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /subscriptions ────────────────────────────────────────────────────

    @Test
    void list_returns200WithEmptyList() throws Exception {
        User user = createActiveUser("sub-empty@example.com", "Password1!");

        mockMvc.perform(get(BASE)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void list_returnsOwnSubscriptionsOnly() throws Exception {
        User user = createActiveUser("sub-own@example.com", "Password1!");
        User other = createActiveUser("sub-other@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        createSubscriptionInDb(user, c, p, SubscriptionStatus.ACTIVE);
        createSubscriptionInDb(other, c, p, SubscriptionStatus.ACTIVE);

        mockMvc.perform(get(BASE)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /subscriptions/{id} ───────────────────────────────────────────────

    @Test
    void get_returns200WithFields() throws Exception {
        User user = createActiveUser("sub-get@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.ACTIVE);

        mockMvc.perform(get(BASE + "/" + sub.getId())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sub.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.billingInterval").value("MONTH"));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        User user = createActiveUser("sub-get404@example.com", "Password1!");

        mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_otherUsersSubscription_returns404() throws Exception {
        User owner = createActiveUser("sub-owner@example.com", "Password1!");
        User other = createActiveUser("sub-other2@example.com", "Password1!");
        Company c = createCompany(owner);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(owner, c, p, SubscriptionStatus.ACTIVE);

        mockMvc.perform(get(BASE + "/" + sub.getId())
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /subscriptions/{id} ─────────────────────────────────────────────

    @Test
    void update_quantity_returns200() throws Exception {
        User user = createActiveUser("sub-update@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.ACTIVE);

        when(paymentService.updateSubscriptionQuantity(any(), any(), anyInt()))
                .thenReturn(new SubscriptionResult("sub_test", "cus_test", "active",
                        "inv_test", NOW, PERIOD_END, "pm_test", "si_test"));

        mockMvc.perform(patch(BASE + "/" + sub.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void update_cancelledSubscription_returns409() throws Exception {
        User user = createActiveUser("sub-update-canc@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.CANCELLED);

        mockMvc.perform(patch(BASE + "/" + sub.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 2))))
                .andExpect(status().isConflict());
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        User user = createActiveUser("sub-update404@example.com", "Password1!");

        mockMvc.perform(patch(BASE + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 2))))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch(BASE + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 2))))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /subscriptions/{id}/pause ────────────────────────────────────────

    @Test
    void pause_activeSubscription_returns200WithPausedStatus() throws Exception {
        User user = createActiveUser("sub-pause@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.ACTIVE);

        when(paymentService.pauseSubscription(any()))
                .thenReturn(new SubscriptionResult("sub_test", "cus_test", "paused",
                        null, NOW, PERIOD_END, "pm_test", "si_test"));

        mockMvc.perform(post(BASE + "/" + sub.getId() + "/pause")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    @Test
    void pause_alreadyPaused_returns409() throws Exception {
        User user = createActiveUser("sub-pause2@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.PAUSED);

        mockMvc.perform(post(BASE + "/" + sub.getId() + "/pause")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isConflict());
    }

    @Test
    void pause_unknownId_returns404() throws Exception {
        User user = createActiveUser("sub-pause404@example.com", "Password1!");

        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/pause")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void pause_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/pause"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /subscriptions/{id}/resume ──────────────────────────────────────

    @Test
    void resume_pausedSubscription_returns200WithActiveStatus() throws Exception {
        User user = createActiveUser("sub-resume@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.PAUSED);

        when(paymentService.resumeSubscription(any()))
                .thenReturn(new SubscriptionResult("sub_test", "cus_test", "active",
                        null, NOW, PERIOD_END, "pm_test", "si_test"));

        mockMvc.perform(post(BASE + "/" + sub.getId() + "/resume")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void resume_activeSubscription_returns409() throws Exception {
        User user = createActiveUser("sub-resume2@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.ACTIVE);

        mockMvc.perform(post(BASE + "/" + sub.getId() + "/resume")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isConflict());
    }

    @Test
    void resume_unknownId_returns404() throws Exception {
        User user = createActiveUser("sub-resume404@example.com", "Password1!");

        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/resume")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    // ── POST /subscriptions/{id}/skip-next ───────────────────────────────────

    @Test
    void skipNext_activeSubscription_returns200WithSkipFlag() throws Exception {
        User user = createActiveUser("sub-skip@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.ACTIVE);

        when(paymentService.skipNextCycle(any(), any(), anyInt()))
                .thenReturn(new SubscriptionResult("sub_test", "cus_test", "active",
                        null, NOW, PERIOD_END.plus(30, ChronoUnit.DAYS), "pm_test", "si_test"));

        mockMvc.perform(post(BASE + "/" + sub.getId() + "/skip-next")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipNextCycle").value(true));
    }

    @Test
    void skipNext_pausedSubscription_returns409() throws Exception {
        User user = createActiveUser("sub-skip2@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.PAUSED);

        mockMvc.perform(post(BASE + "/" + sub.getId() + "/skip-next")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isConflict());
    }

    @Test
    void skipNext_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/skip-next"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /subscriptions/{id} ────────────────────────────────────────────

    @Test
    void cancel_atPeriodEnd_returns200WithCancelAtPeriodEndFlag() throws Exception {
        User user = createActiveUser("sub-cancel1@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.ACTIVE);

        when(paymentService.cancelSubscription(any(), anyBoolean()))
                .thenReturn(new SubscriptionResult("sub_test", "cus_test", "active",
                        null, NOW, PERIOD_END, "pm_test", "si_test"));

        mockMvc.perform(delete(BASE + "/" + sub.getId())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true));
    }

    @Test
    void cancel_immediately_returns200WithCancelledStatus() throws Exception {
        User user = createActiveUser("sub-cancel2@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.ACTIVE);

        when(paymentService.cancelSubscription(any(), anyBoolean()))
                .thenReturn(new SubscriptionResult("sub_test", "cus_test", "canceled",
                        null, NOW, PERIOD_END, "pm_test", "si_test"));

        mockMvc.perform(delete(BASE + "/" + sub.getId())
                        .param("atPeriodEnd", "false")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancel_alreadyCancelled_returns409() throws Exception {
        User user = createActiveUser("sub-cancel3@example.com", "Password1!");
        Company c = createCompany(user);
        Product p = createSubscribableProduct(c);
        Subscription sub = createSubscriptionInDb(user, c, p, SubscriptionStatus.CANCELLED);

        mockMvc.perform(delete(BASE + "/" + sub.getId())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_unknownId_returns404() throws Exception {
        User user = createActiveUser("sub-cancel404@example.com", "Password1!");

        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
