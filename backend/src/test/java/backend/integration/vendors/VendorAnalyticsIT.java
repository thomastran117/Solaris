package backend.integration.vendors;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.*;
import backend.models.enums.*;
import backend.repositories.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VendorAnalyticsIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private MarketplaceProfileRepository marketplaceProfileRepository;
    @Autowired private MarketplaceVendorRepository marketplaceVendorRepository;
    @Autowired private VendorPayoutRepository vendorPayoutRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM vendor_payouts"); } catch (Exception ignored) {}
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
        if (status == PayoutStatus.PAID) {
            payout.setPaidAt(Instant.now());
        }
        return vendorPayoutRepository.save(payout);
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/analytics/summary ─

    @Test
    void getSummary_vendorOwner_emptyData_returns200WithZeros() throws Exception {
        User vendorOwner = createActiveUser("va-summary-owner@example.com", "Password1!");
        User operator = createActiveUser("va-summary-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/summary")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.marketplaceId").value(marketplace.getId().toString()))
                .andExpect(jsonPath("$.data.windowDays").value(30))
                .andExpect(jsonPath("$.data.totalSubOrders").value(0))
                .andExpect(jsonPath("$.data.totalGrossRevenue").value(0))
                .andExpect(jsonPath("$.data.totalCommission").value(0))
                .andExpect(jsonPath("$.data.totalNetRevenue").value(0))
                .andExpect(jsonPath("$.data.cancellationRate").value(0.0))
                .andExpect(jsonPath("$.data.refundRate").value(0.0))
                .andExpect(jsonPath("$.data.lateShipmentRate").value(0.0));
    }

    @Test
    void getSummary_marketplaceOperator_returns200() throws Exception {
        User vendorOwner = createActiveUser("va-summary-owner2@example.com", "Password1!");
        User operator = createActiveUser("va-summary-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/summary")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()));
    }

    @Test
    void getSummary_daysAboveMax_clampsTo365() throws Exception {
        User vendorOwner = createActiveUser("va-summary-clamp-hi@example.com", "Password1!");
        User operator = createActiveUser("va-summary-clamp-hi-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/summary")
                        .param("days", "1000")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowDays").value(365));
    }

    @Test
    void getSummary_daysBelowMin_clampsTo7() throws Exception {
        User vendorOwner = createActiveUser("va-summary-clamp-lo@example.com", "Password1!");
        User operator = createActiveUser("va-summary-clamp-lo-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/summary")
                        .param("days", "1")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowDays").value(7));
    }

    @Test
    void getSummary_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("va-summary-owner3@example.com", "Password1!");
        User operator = createActiveUser("va-summary-op3@example.com", "Password1!");
        User other = createActiveUser("va-summary-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/summary")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_unknownVendor_returns404() throws Exception {
        User operator = createActiveUser("va-summary-op4@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + UUID.randomUUID() + "/analytics/summary")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSummary_vendorFromOtherMarketplace_returns404() throws Exception {
        User vendorOwner = createActiveUser("va-summary-owner4@example.com", "Password1!");
        User operatorA = createActiveUser("va-summary-opA@example.com", "Password1!");
        User operatorB = createActiveUser("va-summary-opB@example.com", "Password1!");
        Company marketplaceA = createMarketplace(operatorA);
        Company marketplaceB = createMarketplace(operatorB);
        MarketplaceVendor vendor = createVendor(marketplaceA, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplaceB.getId() + "/vendors/" + vendor.getId() + "/analytics/summary")
                        .header("Authorization", bearer(accessTokenFor(operatorB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSummary_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/analytics/summary"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/analytics/revenue ─

    @Test
    void getRevenue_emptyData_returns200WithZeros() throws Exception {
        User vendorOwner = createActiveUser("va-revenue-owner@example.com", "Password1!");
        User operator = createActiveUser("va-revenue-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/revenue")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.windowDays").value(30))
                .andExpect(jsonPath("$.data.totalGross").value(0))
                .andExpect(jsonPath("$.data.totalCommission").value(0))
                .andExpect(jsonPath("$.data.totalNet").value(0))
                .andExpect(jsonPath("$.data.daily").isArray())
                .andExpect(jsonPath("$.data.daily").isEmpty());
    }

    @Test
    void getRevenue_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("va-revenue-owner2@example.com", "Password1!");
        User operator = createActiveUser("va-revenue-op2@example.com", "Password1!");
        User other = createActiveUser("va-revenue-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/revenue")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRevenue_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/analytics/revenue"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/analytics/top-products ─

    @Test
    void getTopProducts_emptyData_returns200EmptyList() throws Exception {
        User vendorOwner = createActiveUser("va-topproducts-owner@example.com", "Password1!");
        User operator = createActiveUser("va-topproducts-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/top-products")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.windowDays").value(30))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products").isEmpty());
    }

    @Test
    void getTopProducts_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("va-topproducts-owner2@example.com", "Password1!");
        User operator = createActiveUser("va-topproducts-op2@example.com", "Password1!");
        User other = createActiveUser("va-topproducts-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/top-products")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTopProducts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/analytics/top-products"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/analytics/orders ──

    @Test
    void getOrders_emptyData_returns200WithZeros() throws Exception {
        User vendorOwner = createActiveUser("va-orders-owner@example.com", "Password1!");
        User operator = createActiveUser("va-orders-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/orders")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.cancelled").value(0))
                .andExpect(jsonPath("$.data.cancellationRate").value(0.0))
                .andExpect(jsonPath("$.data.daily").isArray())
                .andExpect(jsonPath("$.data.daily").isEmpty());
    }

    @Test
    void getOrders_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("va-orders-owner2@example.com", "Password1!");
        User operator = createActiveUser("va-orders-op2@example.com", "Password1!");
        User other = createActiveUser("va-orders-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/orders")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/analytics/orders"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/analytics/refunds ─

    @Test
    void getRefunds_emptyData_returns200WithZeros() throws Exception {
        User vendorOwner = createActiveUser("va-refunds-owner@example.com", "Password1!");
        User operator = createActiveUser("va-refunds-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/refunds")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReturns").value(0))
                .andExpect(jsonPath("$.data.totalOrders").value(0))
                .andExpect(jsonPath("$.data.refundRate").value(0.0))
                .andExpect(jsonPath("$.data.daily").isArray())
                .andExpect(jsonPath("$.data.daily").isEmpty());
    }

    @Test
    void getRefunds_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("va-refunds-owner2@example.com", "Password1!");
        User operator = createActiveUser("va-refunds-op2@example.com", "Password1!");
        User other = createActiveUser("va-refunds-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/refunds")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRefunds_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/analytics/refunds"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/analytics/payouts ─

    @Test
    void getPayouts_emptyData_returns200WithZeros() throws Exception {
        User vendorOwner = createActiveUser("va-payouts-owner@example.com", "Password1!");
        User operator = createActiveUser("va-payouts-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/payouts")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.totalPaidOut").value(0))
                .andExpect(jsonPath("$.data.totalPayouts").value(0))
                .andExpect(jsonPath("$.data.recent").isArray())
                .andExpect(jsonPath("$.data.recent").isEmpty());
    }

    @Test
    void getPayouts_withPaidPayouts_returnsRecentList() throws Exception {
        User vendorOwner = createActiveUser("va-payouts-owner2@example.com", "Password1!");
        User operator = createActiveUser("va-payouts-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.PAID, new BigDecimal("100.00"));
        createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.PAID, new BigDecimal("50.00"));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/payouts")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPaidOut").value(150.0))
                .andExpect(jsonPath("$.data.totalPayouts").value(2))
                .andExpect(jsonPath("$.data.recent", hasSize(2)));
    }

    @Test
    void getPayouts_excludesNonPaidStatuses() throws Exception {
        User vendorOwner = createActiveUser("va-payouts-owner3@example.com", "Password1!");
        User operator = createActiveUser("va-payouts-op3@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        createPayout(vendor.getId(), marketplace.getId(), PayoutStatus.SCHEDULED, new BigDecimal("75.00"));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/payouts")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPaidOut").value(0))
                .andExpect(jsonPath("$.data.totalPayouts").value(0))
                .andExpect(jsonPath("$.data.recent").isEmpty());
    }

    @Test
    void getPayouts_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("va-payouts-owner4@example.com", "Password1!");
        User operator = createActiveUser("va-payouts-op4@example.com", "Password1!");
        User other = createActiveUser("va-payouts-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/analytics/payouts")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPayouts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/analytics/payouts"))
                .andExpect(status().isUnauthorized());
    }
}
