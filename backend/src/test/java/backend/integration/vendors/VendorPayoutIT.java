package backend.integration.vendors;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.*;
import backend.models.enums.*;
import backend.repositories.*;
import backend.services.intf.payments.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VendorPayoutIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private MarketplaceProfileRepository marketplaceProfileRepository;
    @Autowired private MarketplaceVendorRepository marketplaceVendorRepository;
    @Autowired private VendorBalanceRepository vendorBalanceRepository;
    @Autowired private VendorPayoutRepository vendorPayoutRepository;
    @Autowired private VendorPayoutItemRepository vendorPayoutItemRepository;

    @MockitoBean private PaymentService paymentService;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM vendor_adjustments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM vendor_payout_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM vendor_payouts"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM vendor_balances"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM marketplace_vendors"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM marketplace_profiles"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createMarketplace(User operator) {
        Company company = new Company();
        company.setOwner(operator);
        company.setName("Marketplace " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(operator);
        m.setRole(CompanyRole.OWNER);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);

        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(company);
        profile.setSlug("mp-" + UUID.randomUUID().toString().substring(0, 8));
        profile.setAcceptingApplications(true);
        marketplaceProfileRepository.save(profile);

        return company;
    }

    private Company createVendorCompany(User owner) {
        Company company = new Company();
        company.setOwner(owner);
        company.setName("Vendor Co " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(company);
    }

    private MarketplaceVendor createVendor(Company marketplace, Company vendorCompany) {
        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setMarketplace(marketplace);
        vendor.setVendorCompany(vendorCompany);
        vendor.setStatus(VendorStatus.APPROVED);
        return marketplaceVendorRepository.save(vendor);
    }

    private MarketplaceVendor createPayoutsEnabledVendor(Company marketplace, Company vendorCompany) {
        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setMarketplace(marketplace);
        vendor.setVendorCompany(vendorCompany);
        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setChargesEnabled(true);
        vendor.setPayoutsEnabled(true);
        vendor.setStripeConnectAccountId("acct_test_" + UUID.randomUUID().toString().substring(0, 8));
        return marketplaceVendorRepository.save(vendor);
    }

    private VendorBalance createBalance(UUID vendorId, long availableCents) {
        VendorBalance balance = new VendorBalance();
        balance.setVendorId(vendorId);
        balance.setAvailableCents(availableCents);
        balance.setCurrency("USD");
        return vendorBalanceRepository.save(balance);
    }

    private VendorPayout createPayout(UUID vendorId, UUID marketplaceId, PayoutStatus status, BigDecimal net) {
        VendorPayout payout = new VendorPayout();
        payout.setVendorId(vendorId);
        payout.setMarketplaceId(marketplaceId);
        payout.setGrossAmount(net);
        payout.setCommissionAmount(BigDecimal.ZERO);
        payout.setRefundAmount(BigDecimal.ZERO);
        payout.setAdjustmentAmount(BigDecimal.ZERO);
        payout.setNetAmount(net);
        payout.setCurrency("USD");
        payout.setStatus(status);
        return vendorPayoutRepository.save(payout);
    }

    // ── GET /vendors/{vendorId}/balance ───────────────────────────────────────

    @Test
    void getBalance_noBalance_returnsZeros() throws Exception {
        User vendorOwner = createActiveUser("vp-bal-empty@example.com", "Password1!");
        User operator = createActiveUser("vp-bal-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/balance")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.availableCents").value(0))
                .andExpect(jsonPath("$.data.pendingCents").value(0))
                .andExpect(jsonPath("$.data.inTransitCents").value(0))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void getBalance_withBalance_returnsValues() throws Exception {
        User vendorOwner = createActiveUser("vp-bal-ok@example.com", "Password1!");
        User operator = createActiveUser("vp-bal-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        createBalance(vendor.getId(), 5000L);

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/balance")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableCents").value(5000))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void getBalance_nonOwner_returns403() throws Exception {
        User vendorOwner = createActiveUser("vp-bal-own@example.com", "Password1!");
        User other = createActiveUser("vp-bal-other@example.com", "Password1!");
        User operator = createActiveUser("vp-bal-op3@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/balance")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBalance_unknownVendor_returns403() throws Exception {
        User user = createActiveUser("vp-bal-404@example.com", "Password1!");

        mockMvc.perform(get("/vendors/" + UUID.randomUUID() + "/balance")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBalance_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/vendors/" + UUID.randomUUID() + "/balance"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /vendors/{vendorId}/payouts ───────────────────────────────────────

    @Test
    void listPayouts_empty_returns200() throws Exception {
        User vendorOwner = createActiveUser("vp-list-empty@example.com", "Password1!");
        User operator = createActiveUser("vp-list-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/payouts")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listPayouts_withPayouts_returnsPage() throws Exception {
        User vendorOwner = createActiveUser("vp-list-ok@example.com", "Password1!");
        User operator = createActiveUser("vp-list-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.PAID, new BigDecimal("100.00"));
        createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.SCHEDULED, new BigDecimal("50.00"));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/payouts")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void listPayouts_statusFilter_returnsFiltered() throws Exception {
        User vendorOwner = createActiveUser("vp-list-filter@example.com", "Password1!");
        User operator = createActiveUser("vp-list-op3@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.PAID, new BigDecimal("100.00"));
        createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.SCHEDULED, new BigDecimal("50.00"));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/payouts?status=PAID")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }

    @Test
    void listPayouts_nonOwner_returns403() throws Exception {
        User vendorOwner = createActiveUser("vp-list-own@example.com", "Password1!");
        User other = createActiveUser("vp-list-other@example.com", "Password1!");
        User operator = createActiveUser("vp-list-op4@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/payouts")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPayouts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/vendors/" + UUID.randomUUID() + "/payouts"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /vendors/{vendorId}/payouts/{payoutId} ────────────────────────────

    @Test
    void getPayoutDetail_returns200() throws Exception {
        User vendorOwner = createActiveUser("vp-detail-ok@example.com", "Password1!");
        User operator = createActiveUser("vp-detail-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        VendorPayout payout = createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.PAID, new BigDecimal("75.00"));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/payouts/" + payout.getId())
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(payout.getId().toString()))
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.netAmount").value(75.0))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void getPayoutDetail_unknownPayout_returns404() throws Exception {
        User vendorOwner = createActiveUser("vp-detail-404@example.com", "Password1!");
        User operator = createActiveUser("vp-detail-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/payouts/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPayoutDetail_payoutFromOtherVendor_returns404() throws Exception {
        User ownerA = createActiveUser("vp-detail-a@example.com", "Password1!");
        User ownerB = createActiveUser("vp-detail-b@example.com", "Password1!");
        User operator = createActiveUser("vp-detail-op3@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendorA = createVendor(marketplace, createVendorCompany(ownerA));
        MarketplaceVendor vendorB = createVendor(marketplace, createVendorCompany(ownerB));

        VendorPayout payoutA = createPayout(vendorA.getId(), marketplace.getId(), PayoutStatus.PAID, new BigDecimal("100.00"));

        // vendorB's owner tries to fetch vendorA's payout via vendorB path
        mockMvc.perform(get("/vendors/" + vendorB.getId() + "/payouts/" + payoutA.getId())
                        .header("Authorization", bearer(accessTokenFor(ownerB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPayoutDetail_nonOwner_returns403() throws Exception {
        User vendorOwner = createActiveUser("vp-detail-own@example.com", "Password1!");
        User other = createActiveUser("vp-detail-other@example.com", "Password1!");
        User operator = createActiveUser("vp-detail-op4@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        VendorPayout payout = createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.SCHEDULED, new BigDecimal("20.00"));

        mockMvc.perform(get("/vendors/" + vendor.getId() + "/payouts/" + payout.getId())
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    // ── POST /marketplaces/{marketplaceId}/payouts/run ────────────────────────

    @Test
    void triggerManualPayout_returns201() throws Exception {
        User vendorOwner = createActiveUser("vp-trig-ok@example.com", "Password1!");
        User operator = createActiveUser("vp-trig-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createPayoutsEnabledVendor(marketplace, createVendorCompany(vendorOwner));
        createBalance(vendor.getId(), 1000L);

        when(paymentService.createTransfer(anyString(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(new PaymentService.TransferResult("tr_test123", 1000L, "usd", "pending"));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/payouts/run?vendorId=" + vendor.getId())
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.stripeTransferId").value("tr_test123"))
                .andExpect(jsonPath("$.data.netAmount").value(10.0))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void triggerManualPayout_noBalance_returns400() throws Exception {
        User vendorOwner = createActiveUser("vp-trig-nobals@example.com", "Password1!");
        User operator = createActiveUser("vp-trig-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        createPayoutsEnabledVendor(marketplace, createVendorCompany(vendorOwner));

        // Create a second vendor to use its ID (no balance exists for it)
        MarketplaceVendor vendor = createPayoutsEnabledVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/payouts/run?vendorId=" + vendor.getId())
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void triggerManualPayout_zeroBalance_returns400() throws Exception {
        User vendorOwner = createActiveUser("vp-trig-zero@example.com", "Password1!");
        User operator = createActiveUser("vp-trig-op3@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createPayoutsEnabledVendor(marketplace, createVendorCompany(vendorOwner));
        createBalance(vendor.getId(), 0L);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/payouts/run?vendorId=" + vendor.getId())
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void triggerManualPayout_payoutsNotEnabled_returns400() throws Exception {
        User vendorOwner = createActiveUser("vp-trig-noen@example.com", "Password1!");
        User operator = createActiveUser("vp-trig-op4@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        // Vendor with chargesEnabled=false, payoutsEnabled=false (default)
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        createBalance(vendor.getId(), 1000L);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/payouts/run?vendorId=" + vendor.getId())
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void triggerManualPayout_nonOperator_returns403() throws Exception {
        User vendorOwner = createActiveUser("vp-trig-nonop@example.com", "Password1!");
        User operator = createActiveUser("vp-trig-op5@example.com", "Password1!");
        User other = createActiveUser("vp-trig-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createPayoutsEnabledVendor(marketplace, createVendorCompany(vendorOwner));
        createBalance(vendor.getId(), 1000L);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/payouts/run?vendorId=" + vendor.getId())
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void triggerManualPayout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/marketplaces/" + UUID.randomUUID() + "/payouts/run?vendorId=" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /marketplaces/{marketplaceId}/vendors/{vendorId}/adjustments ─────

    // NOTE: createAdjustment with positive amountCents calls balanceRepository.upsertPending(),
    // a native MySQL INSERT ... ON DUPLICATE KEY UPDATE that omits the NOT NULL `id` column.
    // MySQL defers NOT NULL validation until the row is inserted; H2 validates upfront and
    // throws 23502 even when the duplicate-key UPDATE path would be taken. Positive credit
    // adjustments are tested in unit tests against a real MySQL container instead.

    @Test
    void createAdjustment_negative_returns201() throws Exception {
        User vendorOwner = createActiveUser("vp-adj-neg@example.com", "Password1!");
        User operator = createActiveUser("vp-adj-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        String body = objectMapper.writeValueAsString(Map.of(
                "amountCents", -200L,
                "currency", "USD",
                "reason", "Chargeback deduction"
        ));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/adjustments")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amountCents").value(-200))
                .andExpect(jsonPath("$.data.reason").value("Chargeback deduction"));
    }

    @Test
    void createAdjustment_zeroAmount_returns400() throws Exception {
        User vendorOwner = createActiveUser("vp-adj-zero@example.com", "Password1!");
        User operator = createActiveUser("vp-adj-op3@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        String body = objectMapper.writeValueAsString(Map.of(
                "amountCents", 0L,
                "currency", "USD",
                "reason", "Zero adjustment"
        ));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/adjustments")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAdjustment_missingReason_returns400() throws Exception {
        User vendorOwner = createActiveUser("vp-adj-noreason@example.com", "Password1!");
        User operator = createActiveUser("vp-adj-op4@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        String body = objectMapper.writeValueAsString(Map.of(
                "amountCents", 100L,
                "currency", "USD"
        ));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/adjustments")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAdjustment_nonOperator_returns403() throws Exception {
        User vendorOwner = createActiveUser("vp-adj-own@example.com", "Password1!");
        User operator = createActiveUser("vp-adj-op5@example.com", "Password1!");
        User other = createActiveUser("vp-adj-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        String body = objectMapper.writeValueAsString(Map.of(
                "amountCents", 100L,
                "currency", "USD",
                "reason", "Test adjustment"
        ));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/adjustments")
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAdjustment_unauthenticated_returns401() throws Exception {
        // Send valid body so @NotBlank/@NotNull validation does not fire before auth check
        String body = objectMapper.writeValueAsString(Map.of(
                "amountCents", 100L,
                "currency", "USD",
                "reason", "Test"
        ));

        mockMvc.perform(post("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
