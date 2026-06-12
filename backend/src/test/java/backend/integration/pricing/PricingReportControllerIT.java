package backend.integration.pricing;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Order;
import backend.models.core.PromotionRedemption;
import backend.models.core.PromotionRule;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.models.enums.PromotionRuleType;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.OrderRepository;
import backend.repositories.PromotionRedemptionRepository;
import backend.repositories.PromotionRuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers PricingReportController (GET /admin/pricing/payout-attribution) — admin-only
 * settlement report aggregating PromotionRedemption rows by funding company.
 */
class PricingReportControllerIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PromotionRuleRepository promotionRuleRepository;
    @Autowired private PromotionRedemptionRepository promotionRedemptionRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM promotion_redemptions"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM promotion_rules"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM orders"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createAdmin(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Password1!"));
        u.setRole(UserRole.ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private Company createCompany(User owner, String name) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName(name + " " + java.util.UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private Order createOrder(User user) {
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(BigDecimal.valueOf(99.99));
        return orderRepository.save(order);
    }

    private PromotionRule createRule(Company company, Company fundedBy, String name) {
        PromotionRule rule = new PromotionRule();
        rule.setCompany(company);
        rule.setName(name);
        rule.setRuleType(PromotionRuleType.PERCENTAGE_OFF);
        rule.setConfigJson("{}");
        rule.setFundedByCompany(fundedBy);
        return promotionRuleRepository.save(rule);
    }

    private void createRedemption(PromotionRule rule, Order order, User user, Company fundedBy,
                                   BigDecimal discount, Instant redeemedAt) {
        PromotionRedemption pr = new PromotionRedemption();
        pr.setRule(rule);
        pr.setOrder(order);
        pr.setUser(user);
        pr.setFundedByCompany(fundedBy);
        pr.setDiscountAmount(discount);
        pr.setRedeemedAt(redeemedAt);
        promotionRedemptionRepository.save(pr);
    }

    // ── GET /admin/pricing/payout-attribution ──────────────────────────────────

    @Test
    void getPayoutAttribution_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/pricing/payout-attribution"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPayoutAttribution_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("pra-user-403@example.com", "Password1!");

        mockMvc.perform(get("/admin/pricing/payout-attribution")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPayoutAttribution_admin_noRedemptions_returnsEmptyReport() throws Exception {
        User admin = createAdmin("pra-admin-empty@example.com");

        mockMvc.perform(get("/admin/pricing/payout-attribution")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(jsonPath("$.data.rows").isEmpty())
                .andExpect(jsonPath("$.data.grandTotalSavings").value(0))
                .andExpect(jsonPath("$.data.totalRedemptions").value(0));
    }

    @Test
    void getPayoutAttribution_admin_withRedemptions_returnsAggregatedRowsSortedBySavings() throws Exception {
        User admin = createAdmin("pra-admin-agg@example.com");
        User shopper = createActiveUser("pra-shopper-agg@example.com", "Password1!");
        Company vendor = createCompany(admin, "Vendor Co");
        Company funderSmall = createCompany(admin, "Small Funder");
        Company funderLarge = createCompany(admin, "Large Funder");

        PromotionRule ruleSmall = createRule(vendor, funderSmall, "Small Promo");
        PromotionRule ruleLarge = createRule(vendor, funderLarge, "Large Promo");

        Order order1 = createOrder(shopper);
        Order order2 = createOrder(shopper);
        Order order3 = createOrder(shopper);

        Instant now = Instant.now();
        createRedemption(ruleSmall, order1, shopper, funderSmall, new BigDecimal("5.00"), now);
        createRedemption(ruleLarge, order2, shopper, funderLarge, new BigDecimal("20.00"), now);
        createRedemption(ruleLarge, order3, shopper, funderLarge, new BigDecimal("10.00"), now);

        mockMvc.perform(get("/admin/pricing/payout-attribution")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                // Largest total savings (30.00, funderLarge) ordered first
                .andExpect(jsonPath("$.data.rows[0].fundedByCompanyId").value(funderLarge.getId().toString()))
                .andExpect(jsonPath("$.data.rows[0].companyName").value(funderLarge.getName()))
                .andExpect(jsonPath("$.data.rows[0].totalSavings").value(30.00))
                .andExpect(jsonPath("$.data.rows[0].redemptionCount").value(2))
                .andExpect(jsonPath("$.data.rows[0].uniqueOrderCount").value(2))
                .andExpect(jsonPath("$.data.rows[1].fundedByCompanyId").value(funderSmall.getId().toString()))
                .andExpect(jsonPath("$.data.rows[1].totalSavings").value(5.00))
                .andExpect(jsonPath("$.data.grandTotalSavings").value(35.00))
                .andExpect(jsonPath("$.data.totalRedemptions").value(3));
    }

    @Test
    void getPayoutAttribution_admin_withDateRange_excludesOutOfRangeRedemptions() throws Exception {
        User admin = createAdmin("pra-admin-range@example.com");
        User shopper = createActiveUser("pra-shopper-range@example.com", "Password1!");
        Company vendor = createCompany(admin, "Vendor Co Range");
        Company funder = createCompany(admin, "Funder Range");

        PromotionRule rule = createRule(vendor, funder, "Range Promo");
        Order orderInRange = createOrder(shopper);
        Order orderOutOfRange = createOrder(shopper);

        Instant now = Instant.now();
        createRedemption(rule, orderInRange, shopper, funder, new BigDecimal("12.00"), now);
        createRedemption(rule, orderOutOfRange, shopper, funder, new BigDecimal("8.00"), now.minus(30, ChronoUnit.DAYS));

        String from = now.minus(1, ChronoUnit.DAYS).toString();
        String to = now.plus(1, ChronoUnit.DAYS).toString();

        mockMvc.perform(get("/admin/pricing/payout-attribution")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].totalSavings").value(12.00))
                .andExpect(jsonPath("$.data.totalRedemptions").value(1));
    }

    @Test
    void getPayoutAttribution_fromAfterTo_returns400() throws Exception {
        User admin = createAdmin("pra-admin-badrange@example.com");
        Instant now = Instant.now();

        mockMvc.perform(get("/admin/pricing/payout-attribution")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .param("from", now.toString())
                        .param("to", now.minus(1, ChronoUnit.DAYS).toString()))
                .andExpect(status().isBadRequest());
    }
}
