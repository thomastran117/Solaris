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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers AnalyticsController (/companies/{companyId}/analytics/*) — hot products,
 * revenue summary, category sales, slow movers and product performance.
 */
class AnalyticsControllerIT extends AbstractIntegrationIT {

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
        c.setName("Analytics Co " + UUID.randomUUID().toString().substring(0, 8));
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

    private Product createProduct(Company company, String name, Integer stock) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(BigDecimal.valueOf(19.99));
        p.setStatus(ProductStatus.ACTIVE);
        p.setStock(stock);
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

    private String analytics(UUID companyId, String path) {
        return "/companies/" + companyId + "/analytics" + path;
    }

    /**
     * jsonPath() values for high-precision doubles are parsed by json-smart as BigDecimal
     * rather than Double, which breaks Hamcrest's greaterThan(0.0). Compare via
     * Number.doubleValue() instead so the matcher works for either representation.
     */
    private static ResultMatcher jsonPathPositive(String path) {
        return result -> {
            Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
            org.junit.jupiter.api.Assertions.assertTrue(((Number) value).doubleValue() > 0,
                    "expected " + path + " > 0 but was " + value);
        };
    }

    // ── GET /companies/{companyId}/analytics/hot-products ──────────────────────

    @Test
    void getHotProducts_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(analytics(UUID.randomUUID(), "/hot-products")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHotProducts_noMembership_returns403() throws Exception {
        User owner = createActiveUser("hp-nomem-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User outsider = createActiveUser("hp-outsider@example.com", "Password1!");

        mockMvc.perform(get(analytics(company.getId(), "/hot-products"))
                        .header("Authorization", bearer(accessTokenFor(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getHotProducts_employee_noSales_returnsEmptyList() throws Exception {
        User owner = createActiveUser("hp-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("hp-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);
        createProduct(company, "Quiet Widget", 50);

        mockMvc.perform(get(analytics(company.getId(), "/hot-products"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window").value("1h"))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products").isEmpty());
    }

    @Test
    void getHotProducts_owner_withRecentSale_returnsPositiveVelocity() throws Exception {
        User owner = createActiveUser("hp-sales-owner@example.com", "Password1!");
        User shopper = createActiveUser("hp-sales-shopper@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Trending Widget", 100);

        createPaidSale(shopper, product, 3, Instant.now());

        mockMvc.perform(get(analytics(company.getId(), "/hot-products"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[0].productId").value(product.getId().toString()))
                .andExpect(jsonPathPositive("$.data.products[0].velocityPerHour"))
                .andExpect(jsonPathPositive("$.data.products[0].accelerationRatio"))
                .andExpect(jsonPath("$.data.products[0].rank").value(1));
    }

    @Test
    void getHotProducts_invalidWindow_returns400() throws Exception {
        User owner = createActiveUser("hp-badwindow-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/hot-products"))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("window", "7d"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHotProducts_limitTooHigh_returns400() throws Exception {
        User owner = createActiveUser("hp-badlimit-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/hot-products"))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("limit", "100"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/analytics/revenue-summary ────────────────────

    @Test
    void getRevenueSummary_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(analytics(UUID.randomUUID(), "/revenue-summary")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRevenueSummary_employeeWithoutAnalytics_returns403() throws Exception {
        User owner = createActiveUser("rs-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("rs-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        mockMvc.perform(get(analytics(company.getId(), "/revenue-summary"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRevenueSummary_owner_noSales_returnsZeroTotals() throws Exception {
        User owner = createActiveUser("rs-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/revenue-summary"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.lookbackDays").value(30))
                .andExpect(jsonPath("$.data.totalRevenue").value(0))
                .andExpect(jsonPath("$.data.totalOrders").value(0))
                .andExpect(jsonPath("$.data.daily").isArray())
                .andExpect(jsonPath("$.data.daily").isEmpty());
    }

    @Test
    void getRevenueSummary_owner_withSales_returnsPositiveTotals() throws Exception {
        User owner = createActiveUser("rs-sales-owner@example.com", "Password1!");
        User shopper = createActiveUser("rs-sales-shopper@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Selling Widget", 100);

        createPaidSale(shopper, product, 4, Instant.now().minus(2, ChronoUnit.DAYS));

        mockMvc.perform(get(analytics(company.getId(), "/revenue-summary"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(1))
                .andExpect(jsonPathPositive("$.data.totalRevenue"))
                .andExpect(jsonPath("$.data.daily").isNotEmpty())
                .andExpect(jsonPathPositive("$.data.daily[0].totalRevenue"));
    }

    @Test
    void getRevenueSummary_lookbackDaysTooLow_returns400() throws Exception {
        User owner = createActiveUser("rs-badwindow-low@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/revenue-summary"))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("lookbackDays", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRevenueSummary_lookbackDaysTooHigh_returns400() throws Exception {
        User owner = createActiveUser("rs-badwindow-high@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/revenue-summary"))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("lookbackDays", "400"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/analytics/category-sales ─────────────────────

    @Test
    void getCategorySales_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(analytics(UUID.randomUUID(), "/category-sales")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCategorySales_employeeWithoutAnalytics_returns403() throws Exception {
        User owner = createActiveUser("cs-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("cs-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        mockMvc.perform(get(analytics(company.getId(), "/category-sales"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCategorySales_owner_noSales_returnsEmptyList() throws Exception {
        User owner = createActiveUser("cs-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/category-sales"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.categories").isEmpty());
    }

    @Test
    void getCategorySales_owner_withSales_groupsUnderUncategorised() throws Exception {
        User owner = createActiveUser("cs-sales-owner@example.com", "Password1!");
        User shopper = createActiveUser("cs-sales-shopper@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Uncategorised Widget", 100);

        createPaidSale(shopper, product, 2, Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(get(analytics(company.getId(), "/category-sales"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].category").value("Uncategorised"))
                .andExpect(jsonPath("$.data.categories[0].totalUnits").value(2))
                .andExpect(jsonPath("$.data.categories[0].revenueSharePercent").value(100.0))
                .andExpect(jsonPathPositive("$.data.categories[0].totalRevenue"));
    }

    // ── GET /companies/{companyId}/analytics/slow-movers ─────────────────────────

    @Test
    void getSlowMovers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(analytics(UUID.randomUUID(), "/slow-movers")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSlowMovers_employeeWithoutAnalytics_returns403() throws Exception {
        User owner = createActiveUser("sm-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("sm-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        mockMvc.perform(get(analytics(company.getId(), "/slow-movers"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSlowMovers_owner_neverSoldProduct_isFlagged() throws Exception {
        User owner = createActiveUser("sm-neversold-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Dusty Widget", 100);

        mockMvc.perform(get(analytics(company.getId(), "/slow-movers"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].unitsSold").value(0))
                .andExpect(jsonPath("$.data.items[0].dailyVelocity").value(0.0))
                .andExpect(jsonPath("$.data.items[0].neverSold").value(true));
    }

    @Test
    void getSlowMovers_daysTooHigh_returns400() throws Exception {
        User owner = createActiveUser("sm-badwindow-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/slow-movers"))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("days", "400"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/analytics/product-performance ─────────────────

    @Test
    void getProductPerformance_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(analytics(UUID.randomUUID(), "/product-performance")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductPerformance_employeeWithoutAnalytics_returns403() throws Exception {
        User owner = createActiveUser("pp-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("pp-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        mockMvc.perform(get(analytics(company.getId(), "/product-performance"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProductPerformance_owner_noProducts_returnsEmptyList() throws Exception {
        User owner = createActiveUser("pp-empty-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/product-performance"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products").isEmpty());
    }

    @Test
    void getProductPerformance_owner_withSales_ranksTopProduct() throws Exception {
        User owner = createActiveUser("pp-sales-owner@example.com", "Password1!");
        User shopper = createActiveUser("pp-sales-shopper@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);
        Product product = createProduct(company, "Top Seller Widget", 100);

        createPaidSale(shopper, product, 6, Instant.now().minus(3, ChronoUnit.DAYS));

        mockMvc.perform(get(analytics(company.getId(), "/product-performance"))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[0].productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.products[0].currentUnits").value(6))
                .andExpect(jsonPath("$.data.products[0].revenueRank").value(1))
                .andExpect(jsonPathPositive("$.data.products[0].currentRevenue"))
                .andExpect(jsonPathPositive("$.data.products[0].revenueGrowthPercent"));
    }

    @Test
    void getProductPerformance_lookbackDaysTooLow_returns400() throws Exception {
        User owner = createActiveUser("pp-badwindow-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(company, owner, CompanyRole.OWNER);

        mockMvc.perform(get(analytics(company.getId(), "/product-performance"))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("lookbackDays", "1"))
                .andExpect(status().isBadRequest());
    }
}
