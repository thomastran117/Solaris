package backend.integration.products;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.models.core.CompanyMembership;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BundleIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void cleanBundles() {
        try { jdbcTemplate.execute("DELETE FROM bundle_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM bundles"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Bundle Test Company " + UUID.randomUUID());
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

    private Product createProduct(Company company, String name, BigDecimal price) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(price);
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    private Map<String, Object> validBundleBody(String name, List<Map<String, Object>> items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("items", items);
        return body;
    }

    private Map<String, Object> bundleItem(UUID productId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", productId.toString());
        item.put("quantity", 1);
        return item;
    }

    private String createBundleAndGetId(Company company, User owner, String name, UUID productId) throws Exception {
        Map<String, Object> body = validBundleBody(name, List.of(bundleItem(productId)));
        return objectMapper.readTree(
                mockMvc.perform(post("/companies/{companyId}/bundles", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).path("data").path("id").asText();
    }

    // ── GET /companies/{companyId}/bundles ────────────────────────────────────

    @Test
    void listBundles_returnsEmptyPageWhenNone() throws Exception {
        User owner = createActiveUser("bundle-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/bundles", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listBundles_returnsEmptyPageForUnknownCompany() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/bundles", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── POST /companies/{companyId}/bundles ───────────────────────────────────

    @Test
    void createBundle_returns201ForOwner() throws Exception {
        User owner = createActiveUser("bundle-create-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));

        Map<String, Object> body = validBundleBody("Starter Bundle", List.of(bundleItem(product.getId())));

        mockMvc.perform(post("/companies/{companyId}/bundles", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Starter Bundle"));
    }

    @Test
    void createBundle_returns201ForManager() throws Exception {
        User owner = createActiveUser("bundle-create-mgr-owner@example.com", "Password1!");
        User manager = createActiveUser("bundle-create-mgr@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(manager, company, CompanyRole.MANAGER);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));

        Map<String, Object> body = validBundleBody("Manager Bundle", List.of(bundleItem(product.getId())));

        mockMvc.perform(post("/companies/{companyId}/bundles", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated());
    }

    @Test
    void createBundle_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("bundle-create-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("bundle-create-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));

        Map<String, Object> body = validBundleBody("Forbidden Bundle", List.of(bundleItem(product.getId())));

        mockMvc.perform(post("/companies/{companyId}/bundles", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void createBundle_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("bundle-create-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));

        Map<String, Object> body = validBundleBody("Unauth Bundle", List.of(bundleItem(product.getId())));

        mockMvc.perform(post("/companies/{companyId}/bundles", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBundle_returns400WhenNameMissing() throws Exception {
        User owner = createActiveUser("bundle-create-noname@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(bundleItem(product.getId())));

        mockMvc.perform(post("/companies/{companyId}/bundles", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBundle_returns400WhenItemsEmpty() throws Exception {
        User owner = createActiveUser("bundle-create-noitems@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Empty Bundle");
        body.put("items", new ArrayList<>());

        mockMvc.perform(post("/companies/{companyId}/bundles", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/bundles/{bundleId} ─────────────────────────

    @Test
    void getBundle_returns200ForExistingBundle() throws Exception {
        User owner = createActiveUser("bundle-get@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));
        String bundleId = createBundleAndGetId(company, owner, "Fetched Bundle", product.getId());

        // Bundles are created as DRAFT; publish to ACTIVE so the public GET endpoint returns it
        mockMvc.perform(patch("/companies/{companyId}/bundles/{bundleId}", company.getId(), bundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        mockMvc.perform(get("/companies/{companyId}/bundles/{bundleId}", company.getId(), bundleId)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(bundleId))
                .andExpect(jsonPath("$.data.name").value("Fetched Bundle"));
    }

    @Test
    void getBundle_returns404ForUnknownBundle() throws Exception {
        User owner = createActiveUser("bundle-get-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/bundles/{bundleId}", company.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /companies/{companyId}/bundles/{bundleId} ───────────────────────

    @Test
    void updateBundle_returns200WithUpdatedName() throws Exception {
        User owner = createActiveUser("bundle-update@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));
        String bundleId = createBundleAndGetId(company, owner, "Old Name", product.getId());

        Map<String, Object> body = Map.of("name", "New Name");

        mockMvc.perform(patch("/companies/{companyId}/bundles/{bundleId}", company.getId(), bundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"));
    }

    @Test
    void updateBundle_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("bundle-upd-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("bundle-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));
        String bundleId = createBundleAndGetId(company, owner, "Bundle", product.getId());

        mockMvc.perform(patch("/companies/{companyId}/bundles/{bundleId}", company.getId(), bundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}")
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateBundle_returns404ForUnknownBundle() throws Exception {
        User owner = createActiveUser("bundle-upd-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(patch("/companies/{companyId}/bundles/{bundleId}", company.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /companies/{companyId}/bundles/{bundleId} ──────────────────────

    @Test
    void deleteBundle_returns204ForOwner() throws Exception {
        User owner = createActiveUser("bundle-del@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));
        String bundleId = createBundleAndGetId(company, owner, "To Delete", product.getId());

        mockMvc.perform(delete("/companies/{companyId}/bundles/{bundleId}", company.getId(), bundleId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/companies/{companyId}/bundles/{bundleId}", company.getId(), bundleId)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBundle_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("bundle-del-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("bundle-del-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));
        String bundleId = createBundleAndGetId(company, owner, "Protected Bundle", product.getId());

        mockMvc.perform(delete("/companies/{companyId}/bundles/{bundleId}", company.getId(), bundleId)
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBundle_returns404ForUnknownBundle() throws Exception {
        User owner = createActiveUser("bundle-del-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{companyId}/bundles/{bundleId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{companyId}/bundles/batch-update ─────────────────────

    @Test
    void batchUpdateBundles_returns200WithUpdatedStatus() throws Exception {
        User owner = createActiveUser("bundle-batch-upd@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));
        String bundleId = createBundleAndGetId(company, owner, "Batch Bundle", product.getId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(bundleId));
        body.put("status", "ARCHIVED");

        mockMvc.perform(post("/companies/{companyId}/bundles/batch-update", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void batchUpdateBundles_returns400WhenNoFieldsProvided() throws Exception {
        User owner = createActiveUser("bundle-batch-upd-nofield@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(UUID.randomUUID().toString()));

        mockMvc.perform(post("/companies/{companyId}/bundles/batch-update", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchUpdateBundles_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("bundle-batch-upd-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("bundle-batch-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(UUID.randomUUID().toString()));
        body.put("status", "ARCHIVED");

        mockMvc.perform(post("/companies/{companyId}/bundles/batch-update", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── POST /companies/{companyId}/bundles/batch-delete ─────────────────────

    @Test
    void batchDeleteBundles_returns204ForOwner() throws Exception {
        User owner = createActiveUser("bundle-batch-del@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Widget", BigDecimal.valueOf(10));
        String bundleId = createBundleAndGetId(company, owner, "To Batch Delete", product.getId());

        Map<String, Object> body = Map.of("ids", List.of(bundleId));

        mockMvc.perform(post("/companies/{companyId}/bundles/batch-delete", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void batchDeleteBundles_returns400WhenIdsEmpty() throws Exception {
        User owner = createActiveUser("bundle-batch-del-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = Map.of("ids", new ArrayList<>());

        mockMvc.perform(post("/companies/{companyId}/bundles/batch-delete", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchDeleteBundles_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("bundle-batch-del-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        Map<String, Object> body = Map.of("ids", List.of(UUID.randomUUID().toString()));

        mockMvc.perform(post("/companies/{companyId}/bundles/batch-delete", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/bundles/compare ────────────────────────────

    @Test
    void compareBundles_returns200WithBundleList() throws Exception {
        User owner = createActiveUser("bundle-compare@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product p1 = createProduct(company, "Widget A", BigDecimal.valueOf(10));
        Product p2 = createProduct(company, "Widget B", BigDecimal.valueOf(15));
        String bundleId1 = createBundleAndGetId(company, owner, "Compare Bundle A", p1.getId());
        String bundleId2 = createBundleAndGetId(company, owner, "Compare Bundle B", p2.getId());

        // compareBundles requires 2–4 IDs
        mockMvc.perform(get("/companies/{companyId}/bundles/compare", company.getId())
                        .param("ids", bundleId1)
                        .param("ids", bundleId2)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }
}
