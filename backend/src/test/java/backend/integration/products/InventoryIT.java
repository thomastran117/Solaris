package backend.integration.products;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InventoryIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void cleanInventory() {
        try { jdbcTemplate.execute("DELETE FROM inventory_adjustments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Inv Company " + UUID.randomUUID());
        return companyRepository.save(c);
    }

    private void addMember(User user, Company company, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Product createProduct(Company company, int stock) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Product " + UUID.randomUUID());
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setStatus(ProductStatus.ACTIVE);
        p.setStock(stock);
        return productRepository.save(p);
    }

    private Product createUntrackedProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Untracked " + UUID.randomUUID());
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setStatus(ProductStatus.ACTIVE);
        // stock intentionally left null → untracked
        return productRepository.save(p);
    }

    private String adjustBody(int delta, String reason) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delta", delta);
        body.put("reason", reason);
        return objectMapper.writeValueAsString(body);
    }

    // ── GET /companies/{companyId}/inventory ──────────────────────────────────

    @Test
    void getInventory_returnsEmptyWhenNoProducts() throws Exception {
        User owner = createActiveUser("inv-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/inventory", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
    }

    @Test
    void getInventory_returnsProductsForOwner() throws Exception {
        User owner = createActiveUser("inv-list-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createProduct(company, 10);

        mockMvc.perform(get("/companies/{companyId}/inventory", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].stock").value(10));
    }

    @Test
    void getInventory_managerCanAccess() throws Exception {
        User owner = createActiveUser("inv-list-mgr-owner@example.com", "Password1!");
        User manager = createActiveUser("inv-list-mgr@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(manager, company, CompanyRole.MANAGER);
        createProduct(company, 5);

        mockMvc.perform(get("/companies/{companyId}/inventory", company.getId())
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void getInventory_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("inv-list-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("inv-list-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(get("/companies/{companyId}/inventory", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInventory_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("inv-list-unauth-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/inventory", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/inventory/summary ──────────────────────────

    @Test
    void getSummary_returnsCountsForOwner() throws Exception {
        User owner = createActiveUser("inv-summary-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createProduct(company, 10);
        createUntrackedProduct(company);

        mockMvc.perform(get("/companies/{companyId}/inventory/summary", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProducts").value(2))
                .andExpect(jsonPath("$.data.trackedProducts").value(1));
    }

    @Test
    void getSummary_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("inv-sum-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("inv-sum-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(get("/companies/{companyId}/inventory/summary", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("inv-sum-unauth-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/inventory/summary", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/inventory/{productId} ──────────────────────

    @Test
    void getInventoryItem_returnsProductData() throws Exception {
        User owner = createActiveUser("inv-item-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 25);

        mockMvc.perform(get("/companies/{companyId}/inventory/{productId}", company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.stock").value(25))
                .andExpect(jsonPath("$.data.stockStatus").value("IN_STOCK"));
    }

    @Test
    void getInventoryItem_returns404WhenProductNotFound() throws Exception {
        User owner = createActiveUser("inv-item-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/inventory/{productId}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInventoryItem_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("inv-item-403-owner@example.com", "Password1!");
        User outsider = createActiveUser("inv-item-403-out@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 10);

        mockMvc.perform(get("/companies/{companyId}/inventory/{productId}", company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInventoryItem_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("inv-item-unauth-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, 5);

        mockMvc.perform(get("/companies/{companyId}/inventory/{productId}", company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/inventory/{productId}/history ─────────────

    @Test
    void getAdjustmentHistory_returnsEmptyWhenNoAdjustments() throws Exception {
        User owner = createActiveUser("inv-hist-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 10);

        mockMvc.perform(get("/companies/{companyId}/inventory/{productId}/history",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getAdjustmentHistory_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("inv-hist-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/inventory/{productId}/history",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAdjustmentHistory_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("inv-hist-403-owner@example.com", "Password1!");
        User employee = createActiveUser("inv-hist-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, 10);

        mockMvc.perform(get("/companies/{companyId}/inventory/{productId}/history",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── POST /companies/{companyId}/inventory/{productId}/adjust ─────────────

    @Test
    void adjustStock_increaseStockSuccessfully() throws Exception {
        User owner = createActiveUser("inv-adj-inc-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 10);

        mockMvc.perform(post("/companies/{companyId}/inventory/{productId}/adjust",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(5, "RESTOCK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(15))
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()));
    }

    @Test
    void adjustStock_decreaseStockSuccessfully() throws Exception {
        User owner = createActiveUser("inv-adj-dec-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 20);

        mockMvc.perform(post("/companies/{companyId}/inventory/{productId}/adjust",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(-3, "DAMAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(17));
    }

    @Test
    void adjustStock_returns400WhenStockNotTracked() throws Exception {
        User owner = createActiveUser("inv-adj-untrk-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createUntrackedProduct(company);

        mockMvc.perform(post("/companies/{companyId}/inventory/{productId}/adjust",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(5, "RESTOCK")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustStock_returns400WhenDeltaIsZero() throws Exception {
        User owner = createActiveUser("inv-adj-zero-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 10);

        mockMvc.perform(post("/companies/{companyId}/inventory/{productId}/adjust",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(0, "RESTOCK")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustStock_returns400WhenReasonMissing() throws Exception {
        User owner = createActiveUser("inv-adj-noreason-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 10);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delta", 5);

        mockMvc.perform(post("/companies/{companyId}/inventory/{productId}/adjust",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustStock_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("inv-adj-403-owner@example.com", "Password1!");
        User employee = createActiveUser("inv-adj-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, 10);

        mockMvc.perform(post("/companies/{companyId}/inventory/{productId}/adjust",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(5, "RESTOCK")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjustStock_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("inv-adj-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, 10);

        mockMvc.perform(post("/companies/{companyId}/inventory/{productId}/adjust",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(5, "RESTOCK")))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/inventory/bulk-adjust ────────────────────

    @Test
    void bulkAdjust_adjustsMultipleProductsSuccessfully() throws Exception {
        User owner = createActiveUser("inv-bulk-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product p1 = createProduct(company, 10);
        Product p2 = createProduct(company, 20);

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("productId", p1.getId().toString());
        item1.put("delta", 5);
        item1.put("reason", "RESTOCK");

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("productId", p2.getId().toString());
        item2.put("delta", -2);
        item2.put("reason", "DAMAGE");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item1, item2));

        mockMvc.perform(post("/companies/{companyId}/inventory/bulk-adjust", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void bulkAdjust_returns400WhenItemsEmpty() throws Exception {
        User owner = createActiveUser("inv-bulk-400-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of());

        mockMvc.perform(post("/companies/{companyId}/inventory/bulk-adjust", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkAdjust_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("inv-bulk-403-owner@example.com", "Password1!");
        User employee = createActiveUser("inv-bulk-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product p1 = createProduct(company, 10);

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("productId", p1.getId().toString());
        item1.put("delta", 5);
        item1.put("reason", "RESTOCK");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item1));

        mockMvc.perform(post("/companies/{companyId}/inventory/bulk-adjust", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /companies/{companyId}/inventory/{productId}/settings ──────────

    @Test
    void updateSettings_updatesLowStockThreshold() throws Exception {
        User owner = createActiveUser("inv-settings-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 50);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lowStockThreshold", 10);
        body.put("maxStock", 100);

        mockMvc.perform(patch("/companies/{companyId}/inventory/{productId}/settings",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockThreshold").value(10))
                .andExpect(jsonPath("$.data.maxStock").value(100));
    }

    @Test
    void updateSettings_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("inv-set-403-owner@example.com", "Password1!");
        User employee = createActiveUser("inv-set-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, 10);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lowStockThreshold", 5);

        mockMvc.perform(patch("/companies/{companyId}/inventory/{productId}/settings",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── GET /companies/{companyId}/inventory/adjustments ─────────────────────

    @Test
    void getCompanyAdjustmentHistory_returnsEmptyInitially() throws Exception {
        User owner = createActiveUser("inv-cadj-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/inventory/adjustments", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void getCompanyAdjustmentHistory_returnsAdjustmentAfterAdjust() throws Exception {
        User owner = createActiveUser("inv-cadj2-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, 10);

        // Perform an adjustment first
        mockMvc.perform(post("/companies/{companyId}/inventory/{productId}/adjust",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(5, "RESTOCK")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/companies/{companyId}/inventory/adjustments", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].delta").value(5))
                .andExpect(jsonPath("$.data[0].reason").value("RESTOCK"));
    }

    @Test
    void getCompanyAdjustmentHistory_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("inv-cadj-403-owner@example.com", "Password1!");
        User employee = createActiveUser("inv-cadj-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(get("/companies/{companyId}/inventory/adjustments", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }
}
