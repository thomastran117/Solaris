package backend.integration.analytics;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultMatcher;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers ForecastingController (/companies/{companyId}/forecasting/*) — company demand
 * forecast, per-product forecast, reorder suggestions and seasonal-prep summary.
 */
class ForecastingControllerIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM order_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM orders"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Forecast Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addMember(Company company, User user, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Product createProduct(Company company, String name, Integer stock, Integer lowStockThreshold) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(BigDecimal.valueOf(19.99));
        p.setStatus(ProductStatus.ACTIVE);
        p.setStock(stock);
        p.setLowStockThreshold(lowStockThreshold);
        return productRepository.save(p);
    }

    /** Creates a PAID order with one item, then backdates created_at so it falls inside the lookback window. */
    private void createPaidSale(User customer, Product product, int quantity, Instant createdAt) {
        Order order = new Order();
        order.setUser(customer);
        order.setStatus(OrderStatus.PAID);
        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        order.setTotalAmount(total);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setPromotionSavings(BigDecimal.ZERO);
        item.setProductName(product.getName());
        item.setFulfillmentStatus(FulfillmentStatus.DELIVERED);

        order.getItems().add(item);
        orderRepository.save(order);

        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE total_amount = ?",
                Timestamp.from(createdAt), total);
    }

    private String forecasting(UUID companyId, String path) {
        return "/companies/" + companyId + "/forecasting" + path;
    }

    /**
     * jsonPath() values for high-precision doubles (e.g. 1.0/6.0) are parsed by json-smart
     * as BigDecimal rather than Double, which breaks Hamcrest's greaterThan(0.0). Compare
     * via Number.doubleValue() instead so the matcher works for either representation.
     */
    private static ResultMatcher jsonPathPositive(String path) {
        return result -> {
            Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
            org.junit.jupiter.api.Assertions.assertTrue(((Number) value).doubleValue() > 0,
                    "expected " + path + " > 0 but was " + value);
        };
    }

    // ── GET /companies/{companyId}/forecasting ─────────────────────────────────

    @Test
    void getCompanyForecast_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(forecasting(UUID.randomUUID(), "")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCompanyForecast_noMembership_returns403() throws Exception {
        User owner = createActiveUser("fc-nomem-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User outsider = createActiveUser("fc-outsider@example.com", "Password1!");

        mockMvc.perform(get(forecasting(company.getId(), ""))
                        .header("Authorization", bearer(accessTokenFor(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCompanyForecast_employeeWithoutAnalytics_returns403() throws Exception {
        User owner = createActiveUser("fc-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("fc-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        mockMvc.perform(get(forecasting(company.getId(), ""))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCompanyForecast_owner_noProducts_returnsEmptySummary() throws Exception {
        User owner = createActiveUser("fc-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(forecasting(company.getId(), ""))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.windowDays").value(56))
                .andExpect(jsonPath("$.data.productCount").value(0))
                .andExpect(jsonPath("$.data.productsNeedingReorder").value(0))
                .andExpect(jsonPath("$.data.productsWithImminentStockout").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void getCompanyForecast_owner_withLowStockProduct_flagsReorderUrgent() throws Exception {
        User owner = createActiveUser("fc-lowstock-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Low Stock Widget", 5, 10);

        mockMvc.perform(get(forecasting(company.getId(), ""))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCount").value(1))
                .andExpect(jsonPath("$.data.productsNeedingReorder").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].currentStock").value(5))
                .andExpect(jsonPath("$.data.items[0].avgDailyDemand").value(0.0))
                .andExpect(jsonPath("$.data.items[0].reorderSuggestedQty").value(0))
                .andExpect(jsonPath("$.data.items[0].reorderUrgent").value(true))
                .andExpect(jsonPath("$.data.items[0].daysOfCoverage").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].likelyStockoutDate").value(nullValue()));
    }

    @Test
    void getCompanyForecast_owner_withSalesHistory_returnsPositiveDemand() throws Exception {
        User owner = createActiveUser("fc-sales-owner@example.com", "Password1!");
        User shopper = createActiveUser("fc-sales-shopper@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Selling Widget", 100, null);

        createPaidSale(shopper, product, 5, Instant.now().minus(2, ChronoUnit.DAYS));

        mockMvc.perform(get(forecasting(company.getId(), ""))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(product.getId().toString()))
                .andExpect(jsonPathPositive("$.data.items[0].avgDailyDemand"));
    }

    @Test
    void getCompanyForecast_lookbackDaysTooLow_returns400() throws Exception {
        User owner = createActiveUser("fc-badwindow-low@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(forecasting(company.getId(), ""))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("lookbackDays", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCompanyForecast_lookbackDaysTooHigh_returns400() throws Exception {
        User owner = createActiveUser("fc-badwindow-high@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(forecasting(company.getId(), ""))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("lookbackDays", "400"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/forecasting/products/{productId} ────────────

    @Test
    void getProductForecast_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(forecasting(UUID.randomUUID(), "/products/" + UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductForecast_owner_noSales_returnsZeroDemand() throws Exception {
        User owner = createActiveUser("pf-zero-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Quiet Widget", 5, 10);

        mockMvc.perform(get(forecasting(company.getId(), "/products/" + product.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.currentStock").value(5))
                .andExpect(jsonPath("$.data.avgDailyDemand").value(0.0))
                .andExpect(jsonPath("$.data.predictedWeeklyDemand").value(0.0))
                .andExpect(jsonPath("$.data.predictedWeeklyLow").value(0.0))
                .andExpect(jsonPath("$.data.predictedWeeklyHigh").value(0.0))
                .andExpect(jsonPath("$.data.daysOfCoverage").value(nullValue()))
                .andExpect(jsonPath("$.data.likelyStockoutDate").value(nullValue()))
                .andExpect(jsonPath("$.data.reorderSuggestedQty").value(0))
                .andExpect(jsonPath("$.data.reorderUrgent").value(true));
    }

    @Test
    void getProductForecast_owner_withSales_returnsPositiveDemand() throws Exception {
        User owner = createActiveUser("pf-sales-owner@example.com", "Password1!");
        User shopper = createActiveUser("pf-sales-shopper@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Popular Widget", 100, null);

        createPaidSale(shopper, product, 8, Instant.now().minus(3, ChronoUnit.DAYS));

        mockMvc.perform(get(forecasting(company.getId(), "/products/" + product.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andExpect(jsonPathPositive("$.data.avgDailyDemand"));
    }

    @Test
    void getProductForecast_owner_productNotFound_returns404() throws Exception {
        User owner = createActiveUser("pf-notfound-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(forecasting(company.getId(), "/products/" + UUID.randomUUID()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    // ── GET /companies/{companyId}/forecasting/reorder-suggestions ──────────────

    @Test
    void getReorderSuggestions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(forecasting(UUID.randomUUID(), "/reorder-suggestions")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReorderSuggestions_owner_noUrgentProducts_returnsEmptyList() throws Exception {
        User owner = createActiveUser("rs-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        createProduct(company, "Well Stocked Widget", 100, null);

        mockMvc.perform(get(forecasting(company.getId(), "/reorder-suggestions"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getReorderSuggestions_limitTooHigh_returns400() throws Exception {
        User owner = createActiveUser("rs-badlimit-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(forecasting(company.getId(), "/reorder-suggestions"))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("limit", "200"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/forecasting/seasonal-prep ─────────────────────

    @Test
    void getSeasonalPrep_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(forecasting(UUID.randomUUID(), "/seasonal-prep")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSeasonalPrep_employeeWithoutAnalytics_returns403() throws Exception {
        User owner = createActiveUser("sp-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("sp-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        mockMvc.perform(get(forecasting(company.getId(), "/seasonal-prep"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSeasonalPrep_owner_insufficientHistory_returnsEmptyWithReason() throws Exception {
        User owner = createActiveUser("sp-insufficient-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        createProduct(company, "New Widget", 50, null);

        mockMvc.perform(get(forecasting(company.getId(), "/seasonal-prep"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.reason").value("INSUFFICIENT_HISTORY"));
    }
}
