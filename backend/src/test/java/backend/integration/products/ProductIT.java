package backend.integration.products;

import backend.documents.ProductDocument;
import backend.integration.fullinfra.AbstractSearchKafkaIT;
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
import backend.repositories.search.ProductSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Runs against live Elasticsearch + Kafka (via {@link AbstractSearchKafkaIT}). Alongside the HTTP,
 * DB, and authorization coverage, a few tests exercise the real search-indexing pipeline in-place:
 * a marketplace product mutation flows through the product-events Kafka topic into the ES index,
 * a delete flows through as a removal, and the reindex endpoint drives the bulk indexing path.
 *
 * <p><b>Why the whole class extends the full-infra base rather than isolating those cases:</b> this
 * is a deliberate choice. The alternative — keeping ProductIT on {@code AbstractIntegrationIT} and
 * splitting the live-indexing cases into a separate full-infra class — was rejected because it
 * re-creates the search/event "silo" we set out to remove: duplicated company/product/membership
 * fixtures, duplicated auth setup, and two places to keep in sync for one domain. Folding the
 * assertions in keeps each scenario expressed once next to the endpoint it exercises. The cost is
 * that this class's shard starts ES+Kafka containers; that is accepted (each IT class is its own
 * JVM fork, so the containers are scoped to this class and the fast H2+Redis suite is untouched).
 * The container startup is a fixed per-fork cost, not per-test. See {@link AbstractSearchKafkaIT}.
 */
class ProductIT extends AbstractSearchKafkaIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSearchRepository productSearchRepository;

    @AfterEach
    void cleanProducts() {
        try { productSearchRepository.deleteAll(); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_attributes"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_images"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_option_values"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_options"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_variants"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_similarities"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Test Company " + UUID.randomUUID());
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

    private Product createProduct(Company company, String name, BigDecimal price, ProductStatus status) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(price);
        p.setStatus(status);
        return productRepository.save(p);
    }

    /** Extracts the {@code $.data.id} field from a JSON response envelope as a UUID. */
    private UUID dataId(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    private Map<String, Object> validProductBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("price", 19.99);
        body.put("stock", 10);
        return body;
    }

    /**
     * Seeds a marketplace-listed product. Only marketplace products flow through the incremental
     * Kafka indexing path — {@code ProductChangedPublisher} guards on {@code marketplaceId != null} —
     * so a mutation on one of these reaches the live Elasticsearch index.
     */
    private Product createMarketplaceProduct(Company company, String name) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(new BigDecimal("19.99"));
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        p.setMarketplaceId(UUID.randomUUID());
        p.setMarketplaceListed(true);
        return productRepository.save(p);
    }

    // ── GET /companies/{companyId}/products ────────────────────────────────────

    @Test
    void getProducts_returnsEmptyPageWhenNoProducts() throws Exception {
        User owner = createActiveUser("prod-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/products", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getProducts_anonymousUserSeesOnlyActiveProducts() throws Exception {
        User owner = createActiveUser("prod-anon@example.com", "Password1!");
        Company company = createCompany(owner);
        createProduct(company, "Active Product", BigDecimal.valueOf(10), ProductStatus.ACTIVE);
        createProduct(company, "Draft Product", BigDecimal.valueOf(10), ProductStatus.DRAFT);

        // Anonymous — no Authorization header
        mockMvc.perform(get("/companies/{companyId}/products", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Active Product"));
    }

    @Test
    void getProducts_memberSeesAllStatuses() throws Exception {
        User owner = createActiveUser("prod-member-all@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createProduct(company, "Active Product", BigDecimal.valueOf(10), ProductStatus.ACTIVE);
        createProduct(company, "Draft Product", BigDecimal.valueOf(10), ProductStatus.DRAFT);

        mockMvc.perform(get("/companies/{companyId}/products", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void getProducts_returns404ForUnknownCompany() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/products", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProducts_returns400ForInvalidSortField() throws Exception {
        User owner = createActiveUser("prod-sort@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/products", company.getId())
                        .param("sort", "DROP TABLE--")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProducts_returns400ForInvalidDirection() throws Exception {
        User owner = createActiveUser("prod-dir@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/products", company.getId())
                        .param("direction", "sideways")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/products/{id} ───────────────────────────────

    @Test
    void getProduct_returnsActiveProductForAnonymousUser() throws Exception {
        User owner = createActiveUser("prod-get-anon@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Visible", BigDecimal.valueOf(25), ProductStatus.ACTIVE);

        mockMvc.perform(get("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Visible"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getProduct_returns404ForDraftProductAsAnonymousUser() throws Exception {
        User owner = createActiveUser("prod-get-draft-anon@example.com", "Password1!");
        Company company = createCompany(owner);
        Product draft = createProduct(company, "Hidden Draft", BigDecimal.valueOf(25), ProductStatus.DRAFT);

        mockMvc.perform(get("/companies/{companyId}/products/{id}", company.getId(), draft.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProduct_returnsDraftForCompanyMember() throws Exception {
        User owner = createActiveUser("prod-get-draft-member@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product draft = createProduct(company, "Draft Only", BigDecimal.valueOf(25), ProductStatus.DRAFT);

        mockMvc.perform(get("/companies/{companyId}/products/{id}", company.getId(), draft.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void getProduct_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("prod-get-unknown@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/products/{id}", company.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProduct_returns404ForUnknownCompany() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/products/{id}", UUID.randomUUID(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{companyId}/products ───────────────────────────────────

    @Test
    void createProduct_returns201ForOwner() throws Exception {
        User owner = createActiveUser("prod-create-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        MvcResult result = mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProductBody("New Widget")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("New Widget"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        // The row must actually exist in the database, not just be echoed in the response.
        Product persisted = productRepository.findById(dataId(result)).orElseThrow();
        assertEquals("New Widget", persisted.getName());
        assertEquals(ProductStatus.DRAFT, persisted.getStatus());
        assertEquals(0, BigDecimal.valueOf(19.99).compareTo(persisted.getPrice()));
        assertEquals(company.getId(), persisted.getCompany().getId());
    }

    @Test
    void createProduct_returns201ForManager() throws Exception {
        User owner = createActiveUser("prod-create-mgr-owner@example.com", "Password1!");
        User manager = createActiveUser("prod-create-mgr@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(manager, company, CompanyRole.MANAGER);

        mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProductBody("Manager Product")))
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Manager Product"));
    }

    @Test
    void createProduct_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("prod-create-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("prod-create-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProductBody("Employee Try")))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("prod-create-nm-owner@example.com", "Password1!");
        User stranger = createActiveUser("prod-create-stranger@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProductBody("Stranger Try")))
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("prod-create-unauth-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProductBody("Unauth Try"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_returns400WhenNameMissing() throws Exception {
        User owner = createActiveUser("prod-create-noname@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("price", 19.99);

        mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_returns400WhenPriceMissing() throws Exception {
        User owner = createActiveUser("prod-create-noprice@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "No Price Product");

        mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_returns400WhenPriceIsNegative() throws Exception {
        User owner = createActiveUser("prod-create-negprice@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Negative Price");
        body.put("price", -5.00);

        mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_returns400WhenCompareAtPriceLessThanPrice() throws Exception {
        User owner = createActiveUser("prod-create-compare@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Bad Compare Price");
        body.put("price", 50.00);
        body.put("compareAtPrice", 30.00); // must be greater than price

        mockMvc.perform(post("/companies/{companyId}/products", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /companies/{companyId}/products/{id} ─────────────────────────────

    @Test
    void updateProduct_returns200ForOwner() throws Exception {
        User owner = createActiveUser("prod-update-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Old Name", BigDecimal.valueOf(10), ProductStatus.DRAFT);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Updated Name");
        body.put("price", 29.99);

        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"));

        // The update must be persisted, not merely reflected in the response payload.
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertEquals("Updated Name", updated.getName());
        assertEquals(0, BigDecimal.valueOf(29.99).compareTo(updated.getPrice()));
    }

    @Test
    void updateProduct_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("prod-update-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("prod-update-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Some Product", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Employee Edit");

        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("prod-update-nm-owner@example.com", "Password1!");
        User stranger = createActiveUser("prod-update-stranger@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Some Product", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Stranger Edit");

        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("prod-update-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Ghost Update");

        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_returns400ForBoostWeightOutOfRange() throws Exception {
        User owner = createActiveUser("prod-update-boost@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Boost Test", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("boostWeight", 99); // valid range 1–10

        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /companies/{companyId}/products/{id} ────────────────────────────

    @Test
    void deleteProduct_returns204ForOwner() throws Exception {
        User owner = createActiveUser("prod-delete-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "To Delete", BigDecimal.valueOf(10), ProductStatus.DRAFT);

        mockMvc.perform(delete("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        // The row must be removed from the database, not just hidden from the read API.
        assertTrue(productRepository.findById(product.getId()).isEmpty(),
                "Deleted product should no longer exist in the database");

        // Verify it's gone — anon can't see it (was DRAFT), so use member call
        mockMvc.perform(get("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("prod-delete-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("prod-delete-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Employee Cannot Delete", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        mockMvc.perform(delete("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProduct_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("prod-delete-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{companyId}/products/{id}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── GET /companies/{companyId}/products/{productId}/images ─────────────────

    @Test
    void getProductImages_returnsEmptyListWhenNoImages() throws Exception {
        User owner = createActiveUser("prod-img-list@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "No Images", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/images",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── POST /companies/{companyId}/products/{productId}/images ────────────────

    @Test
    void addProductImage_returns201WithCreatedImage() throws Exception {
        User owner = createActiveUser("prod-img-add@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "With Image", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("imageUrl", "https://cdn.example.com/image.jpg");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/images",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/image.jpg"));

        Integer imageRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_images WHERE image_url = ?",
                Integer.class, "https://cdn.example.com/image.jpg");
        assertEquals(1, imageRows, "Image row should be persisted");
    }

    @Test
    void addProductImage_returns400WhenImageUrlMissing() throws Exception {
        User owner = createActiveUser("prod-img-nourl@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Missing URL", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/images",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addProductImage_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("prod-img-nm-owner@example.com", "Password1!");
        User stranger = createActiveUser("prod-img-nm-stranger@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Strangers Image", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("imageUrl", "https://cdn.example.com/image.jpg");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/images",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── GET /companies/{companyId}/products/{productId}/options ────────────────

    @Test
    void getProductOptions_returnsEmptyListWhenNone() throws Exception {
        User owner = createActiveUser("prod-opt-list@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "No Options", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/options",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── POST /companies/{companyId}/products/{productId}/options ───────────────

    @Test
    void addProductOption_returns201WithCreatedOption() throws Exception {
        User owner = createActiveUser("prod-opt-add@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "With Options", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Color");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/options",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Color"));

        Integer optionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_options WHERE name = ?", Integer.class, "Color");
        assertEquals(1, optionRows, "Option row should be persisted");
    }

    @Test
    void addProductOption_returns400WhenNameMissing() throws Exception {
        User owner = createActiveUser("prod-opt-noname@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Missing Option Name", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/options",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/products/{productId}/variants ───────────────

    @Test
    void getProductVariants_returnsEmptyListWhenNone() throws Exception {
        User owner = createActiveUser("prod-var-list@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "No Variants", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/variants",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── POST /companies/{companyId}/products/{productId}/variants ──────────────

    @Test
    void createProductVariant_returns201WithCreatedVariant() throws Exception {
        User owner = createActiveUser("prod-var-add@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "With Variants", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("price", 24.99);
        body.put("option1", "Red");
        body.put("stock", 5);

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/variants",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.price").value(24.99))
                .andExpect(jsonPath("$.data.option1").value("Red"));

        Integer variantRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_variants WHERE option1 = ? AND stock = ?",
                Integer.class, "Red", 5);
        assertEquals(1, variantRows, "Variant row should be persisted with the given stock");
    }

    @Test
    void createProductVariant_returns400WhenPriceMissing() throws Exception {
        User owner = createActiveUser("prod-var-noprice@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Missing Variant Price", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/variants",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/products/{productId}/attributes ─────────────

    @Test
    void getProductAttributes_returnsEmptyListWhenNone() throws Exception {
        User owner = createActiveUser("prod-attr-list@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "No Attributes", BigDecimal.valueOf(10), ProductStatus.ACTIVE);

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/attributes",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── Live search indexing (real Kafka + Elasticsearch) ──────────────────────
    //
    // These prove the request didn't just return a green HTTP status but actually drove the
    // real indexing pipeline end-to-end: API mutation → ProductIndexEvent (AFTER_COMMIT) →
    // product-events Kafka topic → ProductIndexingKafkaConsumer → indexing worker → live ES,
    // read back through the real ProductSearchRepository.

    @Test
    void updateMarketplaceProduct_indexesUpdatedDocumentIntoElasticsearchViaKafka() throws Exception {
        User owner = createActiveUser("prod-es-index@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createMarketplaceProduct(company, "Original Name");

        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Indexed Widget")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        // DB is the source of truth — confirm the update landed there first.
        Product persisted = productRepository.findById(product.getId()).orElseThrow();
        assertEquals("Indexed Widget", persisted.getName());

        // Then confirm the async Kafka→ES pipeline propagated the same change to the live index.
        Optional<ProductDocument> indexed = await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()),
                Optional::isPresent);
        assertTrue(indexed.isPresent(),
                "Product should have been indexed into Elasticsearch via the Kafka pipeline");
        assertEquals("Indexed Widget", indexed.get().getName(),
                "Indexed document should reflect the updated product name");
    }

    @Test
    void deleteMarketplaceProduct_removesDocumentFromElasticsearchViaKafka() throws Exception {
        User owner = createActiveUser("prod-es-remove@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createMarketplaceProduct(company, "To Be Removed");

        // Get it indexed first.
        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Still Here")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
        // Assert the document actually landed in ES first — otherwise the later "removed" check
        // would pass trivially against a document that was never indexed, hiding a broken delete.
        Optional<ProductDocument> indexed = await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()), Optional::isPresent);
        assertTrue(indexed.isPresent(),
                "Product should be indexed into Elasticsearch before we exercise the delete path");

        mockMvc.perform(delete("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        assertTrue(productRepository.findById(product.getId()).isEmpty(),
                "Product row should be gone from the database after delete");
        Optional<ProductDocument> afterDelete = await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()),
                Optional::isEmpty);
        assertTrue(afterDelete.isEmpty(),
                "Product document should have been removed from Elasticsearch via the Kafka pipeline");
    }

    @Test
    void reindexEndpoint_bulkIndexesCompanyProductsIntoElasticsearch() throws Exception {
        User owner = createActiveUser("prod-es-reindex@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        // Plain products are not picked up by the incremental Kafka path; the reindex endpoint
        // drives ProductIndexingService.reindexCompany — the bulk ES write path — directly.
        Product a = createProduct(company, "Reindex Widget A", new BigDecimal("29.99"), ProductStatus.ACTIVE);
        Product b = createProduct(company, "Reindex Widget B", new BigDecimal("39.99"), ProductStatus.ACTIVE);

        mockMvc.perform(post("/companies/{companyId}/products/reindex", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isAccepted());

        await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(a.getId()), Optional::isPresent);
        await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(b.getId()), Optional::isPresent);
        assertTrue(productSearchRepository.findById(a.getId()).isPresent()
                        && productSearchRepository.findById(b.getId()).isPresent(),
                "Both products should be bulk-indexed into Elasticsearch by the reindex endpoint");
    }
}
