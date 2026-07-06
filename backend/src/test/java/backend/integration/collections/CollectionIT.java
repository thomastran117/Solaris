package backend.integration.collections;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollectionIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void cleanCollections() {
        try { jdbcTemplate.execute("DELETE FROM collection_products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM collections"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Test Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        Company saved = companyRepository.save(c);
        CompanyMembership m = new CompanyMembership();
        m.setCompany(saved);
        m.setUser(owner);
        m.setRole(CompanyRole.OWNER);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
        return saved;
    }

    private Product createProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Test Product " + UUID.randomUUID().toString().substring(0, 8));
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("19.99"));
        p.setCurrency("USD");
        p.setStock(100);
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        return productRepository.save(p);
    }

    private String base(UUID companyId) {
        return "/companies/" + companyId + "/collections";
    }

    private String collectionBody(String name, String slug) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("slug", slug);
        body.put("type", "STATIC");
        return objectMapper.writeValueAsString(body);
    }

    private UUID createCollectionViaApi(User owner, Company company, String name, String slug) throws Exception {
        String response = mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collectionBody(name, slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    private UUID createDynamicCollectionViaApi(User owner, Company company, String name, String slug) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("slug", slug);
        body.put("type", "DYNAMIC");
        body.put("rulesJson", "{\"tagsAnyOf\":[\"sale\"],\"categoriesAnyOf\":[],\"brandsAnyOf\":[]}");
        String response = mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    // ── GET /companies/{companyId}/collections ────────────────────────────────

    @Test
    void listCollections_emptyForExistingCompany_returns200() throws Exception {
        User owner = createActiveUser("list-empty@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listCollections_afterCreate_returnsCollection() throws Exception {
        User owner = createActiveUser("list-after@example.com", "Password1!");
        Company company = createCompany(owner);
        createCollectionViaApi(owner, company, "Summer Sale", "summer-sale");

        mockMvc.perform(get(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Summer Sale"))
                .andExpect(jsonPath("$.data[0].slug").value("summer-sale"))
                .andExpect(jsonPath("$.data[0].type").value("STATIC"));
    }

    @Test
    void listCollections_unknownCompany_returns403() throws Exception {
        // Admin route: an authenticated user with no role on the (unknown) company is rejected by
        // the access check (Forbidden) before company existence is even evaluated.
        User stranger = createActiveUser("list-unknown-co@example.com", "Password1!");
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/collections")
                        .header("Authorization", bearer(accessTokenFor(stranger))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCollections_negativePageParam_returns400() throws Exception {
        User owner = createActiveUser("list-negpage@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listCollections_oversizeParam_returns400() throws Exception {
        User owner = createActiveUser("list-bigsize@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/collections/{collectionId} ─────────────────

    @Test
    void getCollection_returns200WithFields() throws Exception {
        User owner = createActiveUser("get-fields@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Winter Collection", "winter-collection");

        mockMvc.perform(get(base(company.getId()) + "/" + collectionId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(collectionId.toString()))
                .andExpect(jsonPath("$.data.name").value("Winter Collection"))
                .andExpect(jsonPath("$.data.slug").value("winter-collection"))
                .andExpect(jsonPath("$.data.type").value("STATIC"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.productCount").value(0))
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void getCollection_unknownId_returns404() throws Exception {
        User owner = createActiveUser("get-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCollection_unknownCompany_returns403() throws Exception {
        User stranger = createActiveUser("get-unknown-co@example.com", "Password1!");
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/collections/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(stranger))))
                .andExpect(status().isForbidden());
    }

    // ── POST /companies/{companyId}/collections ───────────────────────────────

    @Test
    void createCollection_returns201WithFields() throws Exception {
        User owner = createActiveUser("create-fields@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collectionBody("New Arrivals", "new-arrivals")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("New Arrivals"))
                .andExpect(jsonPath("$.data.slug").value("new-arrivals"))
                .andExpect(jsonPath("$.data.type").value("STATIC"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM collections WHERE slug = 'new-arrivals'", Integer.class);
        assertEquals(1, rows, "Collection should be persisted");
    }

    @Test
    void createCollection_slugIsNormalised() throws Exception {
        User owner = createActiveUser("create-slug@example.com", "Password1!");
        Company company = createCompany(owner);

        // Slug with uppercase letters — normaliseSlug() trims and lowercases
        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collectionBody("Featured Items", "Featured-Items")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("featured-items"));
    }

    @Test
    void createCollection_duplicateSlug_returns409() throws Exception {
        User owner = createActiveUser("create-dup@example.com", "Password1!");
        Company company = createCompany(owner);
        createCollectionViaApi(owner, company, "First", "same-slug");

        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collectionBody("Second", "same-slug")))
                .andExpect(status().isConflict());
    }

    @Test
    void createCollection_missingName_returns400() throws Exception {
        User owner = createActiveUser("create-noname@example.com", "Password1!");
        Company company = createCompany(owner);

        String body = objectMapper.writeValueAsString(Map.of("slug", "no-name", "type", "STATIC"));
        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCollection_missingSlug_returns400() throws Exception {
        User owner = createActiveUser("create-noslug@example.com", "Password1!");
        Company company = createCompany(owner);

        String body = objectMapper.writeValueAsString(Map.of("name", "No Slug", "type", "STATIC"));
        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCollection_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("create-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        User nonMember = createActiveUser("create-nonmember@example.com", "Password1!");

        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(nonMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collectionBody("Blocked", "blocked-slug")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCollection_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("create-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post(base(company.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collectionBody("Anon", "anon-slug")))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /companies/{companyId}/collections/{collectionId} ───────────────

    @Test
    void updateCollection_updatesName_returns200() throws Exception {
        User owner = createActiveUser("update-name@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Old Name", "old-name");

        String body = objectMapper.writeValueAsString(Map.of("name", "New Name"));
        mockMvc.perform(patch(base(company.getId()) + "/" + collectionId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.slug").value("old-name"));

        String name = jdbcTemplate.queryForObject("SELECT name FROM collections", String.class);
        assertEquals("New Name", name, "Collection rename should be persisted");
    }

    @Test
    void updateCollection_updatesStatus_returns200() throws Exception {
        User owner = createActiveUser("update-status@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Go Live", "go-live");

        String body = objectMapper.writeValueAsString(Map.of("status", "ACTIVE"));
        mockMvc.perform(patch(base(company.getId()) + "/" + collectionId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        String status = jdbcTemplate.queryForObject("SELECT status FROM collections", String.class);
        assertEquals("ACTIVE", status, "Collection status change should be persisted");
    }

    @Test
    void updateCollection_duplicateSlug_returns409() throws Exception {
        User owner = createActiveUser("update-dupslug@example.com", "Password1!");
        Company company = createCompany(owner);
        createCollectionViaApi(owner, company, "Alpha", "alpha-slug");
        UUID betaId = createCollectionViaApi(owner, company, "Beta", "beta-slug");

        String body = objectMapper.writeValueAsString(Map.of("slug", "alpha-slug"));
        mockMvc.perform(patch(base(company.getId()) + "/" + betaId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void updateCollection_unknownId_returns404() throws Exception {
        User owner = createActiveUser("update-404@example.com", "Password1!");
        Company company = createCompany(owner);

        String body = objectMapper.writeValueAsString(Map.of("name", "New"));
        mockMvc.perform(patch(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCollection_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("update-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Owned", "owned");
        User nonMember = createActiveUser("update-nonmember@example.com", "Password1!");

        String body = objectMapper.writeValueAsString(Map.of("name", "Hijacked"));
        mockMvc.perform(patch(base(company.getId()) + "/" + collectionId)
                        .header("Authorization", bearer(accessTokenFor(nonMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCollection_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("update-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Auth Required", "auth-required");

        String body = objectMapper.writeValueAsString(Map.of("name", "No Auth"));
        mockMvc.perform(patch(base(company.getId()) + "/" + collectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /companies/{companyId}/collections/{collectionId} ──────────────

    @Test
    void deleteCollection_returns204() throws Exception {
        User owner = createActiveUser("delete-ok@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Deletable", "deletable");

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM collections", Integer.class);
        assertEquals(0, rows, "Deleted collection should be removed from the database");
    }

    @Test
    void deleteCollection_deletedCollectionIsGone_returns404() throws Exception {
        User owner = createActiveUser("delete-gone@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Gone Soon", "gone-soon");

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(base(company.getId()) + "/" + collectionId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCollection_unknownId_returns404() throws Exception {
        User owner = createActiveUser("delete-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(delete(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCollection_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("delete-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Protected", "protected");
        User nonMember = createActiveUser("delete-nonmember@example.com", "Password1!");

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId)
                        .header("Authorization", bearer(accessTokenFor(nonMember))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCollection_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("delete-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Secure Delete", "secure-delete");

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/collections/{collectionId}/products ─────────

    @Test
    void listProducts_emptyCollection_returns200() throws Exception {
        User owner = createActiveUser("prods-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Empty Coll", "empty-coll");

        mockMvc.perform(get(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listProducts_afterAddProduct_returnsProduct() throws Exception {
        User owner = createActiveUser("prods-list@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID collectionId = createCollectionViaApi(owner, company, "Has Products", "has-products");

        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data[0].productName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].source").value("MANUAL"));
    }

    @Test
    void listProducts_unknownCollection_returns404() throws Exception {
        User owner = createActiveUser("prods-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()) + "/" + UUID.randomUUID() + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{companyId}/collections/{collectionId}/products ────────

    @Test
    void addProduct_returns201WithFields() throws Exception {
        User owner = createActiveUser("addprod-ok@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID collectionId = createCollectionViaApi(owner, company, "Add Prod Coll", "add-prod-coll");

        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.productName").isNotEmpty())
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.collectionId").value(collectionId.toString()));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM collection_products", Integer.class);
        assertEquals(1, rows, "Collection-product membership should be persisted");
    }

    @Test
    void addProduct_duplicateProduct_returns409() throws Exception {
        User owner = createActiveUser("addprod-dup@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID collectionId = createCollectionViaApi(owner, company, "Dup Prod Coll", "dup-prod-coll");

        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void addProduct_unknownProduct_returns404() throws Exception {
        User owner = createActiveUser("addprod-unknown@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Unknown Prod Coll", "unknown-prod-coll");

        String body = objectMapper.writeValueAsString(Map.of("productId", UUID.randomUUID().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void addProduct_missingProductId_returns400() throws Exception {
        User owner = createActiveUser("addprod-noid@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "No Id Coll", "no-id-coll");

        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addProduct_toDynamicCollection_returns400() throws Exception {
        User owner = createActiveUser("addprod-dynamic@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID dynamicId = createDynamicCollectionViaApi(owner, company, "Dynamic Coll", "dynamic-coll");

        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + dynamicId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addProduct_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("addprod-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Members Only", "members-only");
        User nonMember = createActiveUser("addprod-nonmember@example.com", "Password1!");

        String body = objectMapper.writeValueAsString(Map.of("productId", UUID.randomUUID().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(nonMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void addProduct_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("addprod-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Auth Prod Coll", "auth-prod-coll");

        String body = objectMapper.writeValueAsString(Map.of("productId", UUID.randomUUID().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /companies/{companyId}/collections/{collectionId}/products/{productId} ──

    @Test
    void updateProduct_returnsUpdatedBoostAndRank() throws Exception {
        User owner = createActiveUser("updprod-ok@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID collectionId = createCollectionViaApi(owner, company, "Boost Coll", "boost-coll");

        String addBody = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody))
                .andExpect(status().isCreated());

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("boostWeight", 5);
        updateBody.put("pinnedRank", 1);
        mockMvc.perform(patch(base(company.getId()) + "/" + collectionId + "/products/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boostWeight").value(5))
                .andExpect(jsonPath("$.data.pinnedRank").value(1));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT boost_weight, pinned_rank FROM collection_products");
        assertEquals(5, ((Number) row.get("boost_weight")).intValue());
        assertEquals(1, ((Number) row.get("pinned_rank")).intValue());
    }

    @Test
    void updateProduct_productNotInCollection_returns404() throws Exception {
        User owner = createActiveUser("updprod-404@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Empty Update", "empty-update");

        String body = objectMapper.writeValueAsString(Map.of("boostWeight", 3));
        mockMvc.perform(patch(base(company.getId()) + "/" + collectionId + "/products/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("updprod-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Secure Upd", "secure-upd");
        User nonMember = createActiveUser("updprod-nonmember@example.com", "Password1!");

        String body = objectMapper.writeValueAsString(Map.of("boostWeight", 3));
        mockMvc.perform(patch(base(company.getId()) + "/" + collectionId + "/products/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(nonMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("updprod-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Auth Upd", "auth-upd");

        String body = objectMapper.writeValueAsString(Map.of("boostWeight", 3));
        mockMvc.perform(patch(base(company.getId()) + "/" + collectionId + "/products/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /companies/{companyId}/collections/{collectionId}/products/{productId} ──

    @Test
    void removeProduct_returns204() throws Exception {
        User owner = createActiveUser("rmprod-ok@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID collectionId = createCollectionViaApi(owner, company, "Remove Prod Coll", "remove-prod-coll");

        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId + "/products/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM collection_products", Integer.class);
        assertEquals(0, rows, "Removed collection-product row should be gone from the database");
    }

    @Test
    void removeProduct_removedProductNoLongerInList() throws Exception {
        User owner = createActiveUser("rmprod-gone@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID collectionId = createCollectionViaApi(owner, company, "Gone Prod Coll", "gone-prod-coll");

        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId + "/products/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(base(company.getId()) + "/" + collectionId + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void removeProduct_productNotInCollection_returns404() throws Exception {
        User owner = createActiveUser("rmprod-404@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Empty Remove", "empty-remove");

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId + "/products/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeProduct_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("rmprod-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Secure Rm", "secure-rm");
        User nonMember = createActiveUser("rmprod-nonmember@example.com", "Password1!");

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId + "/products/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(nonMember))))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeProduct_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("rmprod-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Auth Rm Coll", "auth-rm-coll");

        mockMvc.perform(delete(base(company.getId()) + "/" + collectionId + "/products/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/collections/{collectionId}/refresh ─────────

    @Test
    void refreshCollection_dynamicCollection_returns200() throws Exception {
        User owner = createActiveUser("refresh-ok@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID dynamicId = createDynamicCollectionViaApi(owner, company, "Refresh Coll", "refresh-coll");

        mockMvc.perform(post(base(company.getId()) + "/" + dynamicId + "/refresh")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("DYNAMIC"))
                .andExpect(jsonPath("$.data.id").value(dynamicId.toString()));
    }

    @Test
    void refreshCollection_staticCollection_returns400() throws Exception {
        User owner = createActiveUser("refresh-static@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Static Coll", "static-coll");

        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/refresh")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshCollection_unknownCollection_returns404() throws Exception {
        User owner = createActiveUser("refresh-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post(base(company.getId()) + "/" + UUID.randomUUID() + "/refresh")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshCollection_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("refresh-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Member Refresh", "member-refresh");
        User nonMember = createActiveUser("refresh-nonmember@example.com", "Password1!");

        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/refresh")
                        .header("Authorization", bearer(accessTokenFor(nonMember))))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshCollection_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("refresh-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID collectionId = createCollectionViaApi(owner, company, "Refresh Auth", "refresh-auth");

        mockMvc.perform(post(base(company.getId()) + "/" + collectionId + "/refresh"))
                .andExpect(status().isUnauthorized());
    }
}
