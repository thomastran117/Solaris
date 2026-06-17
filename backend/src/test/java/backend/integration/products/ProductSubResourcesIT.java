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

/**
 * Tests for ProductController sub-resource endpoints not covered by ProductIT:
 * images (delete, reorder), options (update, delete), variants (getOne, update, delete),
 * attributes (set), relationships (get, add, remove), similar, marketplace, merchandising,
 * history, revert, compare, reindex, and bundles discovery endpoints.
 */
class ProductSubResourcesIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void cleanProducts() {
        try { jdbcTemplate.execute("DELETE FROM product_relationships"); } catch (Exception ignored) {}
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Sub-resource Test Company " + UUID.randomUUID());
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

    private Product createProduct(Company company, String name) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(10));
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    private String addImageAndGetId(Company company, Product product, User owner) throws Exception {
        Map<String, Object> body = Map.of("imageUrl", "https://cdn.example.com/img.jpg");
        return objectMapper.readTree(
                mockMvc.perform(post("/companies/{cId}/products/{pId}/images",
                                company.getId(), product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).path("data").path("id").asText();
    }

    private String addOptionAndGetId(Company company, Product product, User owner, String name) throws Exception {
        Map<String, Object> body = Map.of("name", name);
        return objectMapper.readTree(
                mockMvc.perform(post("/companies/{cId}/products/{pId}/options",
                                company.getId(), product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).path("data").path("id").asText();
    }

    private String addVariantAndGetId(Company company, Product product, User owner) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("price", 14.99);
        body.put("option1", "Medium");
        body.put("stock", 5);
        return objectMapper.readTree(
                mockMvc.perform(post("/companies/{cId}/products/{pId}/variants",
                                company.getId(), product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).path("data").path("id").asText();
    }

    // ── DELETE /images/{imageId} ──────────────────────────────────────────────

    @Test
    void deleteProductImage_returns204ForOwner() throws Exception {
        User owner = createActiveUser("sub-img-del@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Image Delete Product");
        String imageId = addImageAndGetId(company, product, owner);

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/images/{imgId}",
                        company.getId(), product.getId(), imageId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProductImage_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("sub-img-del-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("sub-img-del-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Image Del Employee");
        String imageId = addImageAndGetId(company, product, owner);

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/images/{imgId}",
                        company.getId(), product.getId(), imageId)
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProductImage_returns404ForUnknownImage() throws Exception {
        User owner = createActiveUser("sub-img-del-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Image Del 404");

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/images/{imgId}",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /images/reorder ─────────────────────────────────────────────────

    @Test
    void reorderProductImages_returns400WhenImageIdsEmpty() throws Exception {
        User owner = createActiveUser("sub-img-reorder-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Reorder Empty");

        Map<String, Object> body = Map.of("imageIds", List.of());

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/images/reorder",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reorderProductImages_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("sub-img-reorder-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Reorder Unauth");

        Map<String, Object> body = Map.of("imageIds", List.of(UUID.randomUUID().toString()));

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/images/reorder",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /options/{optionId} ─────────────────────────────────────────────

    @Test
    void updateProductOption_returns200WithUpdatedName() throws Exception {
        User owner = createActiveUser("sub-opt-upd@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Option Update Product");
        String optionId = addOptionAndGetId(company, product, owner, "Size");

        Map<String, Object> body = Map.of("name", "Colour");

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/options/{optId}",
                        company.getId(), product.getId(), optionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Colour"));
    }

    @Test
    void updateProductOption_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("sub-opt-upd-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("sub-opt-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Option Emp Update");
        String optionId = addOptionAndGetId(company, product, owner, "Weight");

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/options/{optId}",
                        company.getId(), product.getId(), optionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}")
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProductOption_returns404ForUnknownOption() throws Exception {
        User owner = createActiveUser("sub-opt-upd-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Option 404");

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/options/{optId}",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /options/{optionId} ────────────────────────────────────────────

    @Test
    void deleteProductOption_returns204ForOwner() throws Exception {
        User owner = createActiveUser("sub-opt-del@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Option Delete Product");
        String optionId = addOptionAndGetId(company, product, owner, "Material");

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/options/{optId}",
                        company.getId(), product.getId(), optionId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProductOption_returns404ForUnknownOption() throws Exception {
        User owner = createActiveUser("sub-opt-del-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Option Del 404");

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/options/{optId}",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── GET /variants/{variantId} ─────────────────────────────────────────────

    @Test
    void getProductVariant_returns200ForExistingVariant() throws Exception {
        User owner = createActiveUser("sub-var-get@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Variant Get Product");
        String variantId = addVariantAndGetId(company, product, owner);

        mockMvc.perform(get("/companies/{cId}/products/{pId}/variants/{vId}",
                        company.getId(), product.getId(), variantId)
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(variantId));
    }

    @Test
    void getProductVariant_returns404ForUnknownVariant() throws Exception {
        User owner = createActiveUser("sub-var-get-404@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Variant 404");

        mockMvc.perform(get("/companies/{cId}/products/{pId}/variants/{vId}",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /variants/{variantId} ───────────────────────────────────────────

    @Test
    void updateProductVariant_returns200WithUpdatedPrice() throws Exception {
        User owner = createActiveUser("sub-var-upd@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Variant Update Product");
        String variantId = addVariantAndGetId(company, product, owner);

        Map<String, Object> body = Map.of("price", 19.99);

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/variants/{vId}",
                        company.getId(), product.getId(), variantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(19.99));
    }

    @Test
    void updateProductVariant_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("sub-var-upd-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("sub-var-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Variant Emp Update");
        String variantId = addVariantAndGetId(company, product, owner);

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/variants/{vId}",
                        company.getId(), product.getId(), variantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":1.00}")
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProductVariant_returns404ForUnknownVariant() throws Exception {
        User owner = createActiveUser("sub-var-upd-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Variant Upd 404");

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/variants/{vId}",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":5.00}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /variants/{variantId} ──────────────────────────────────────────

    @Test
    void deleteProductVariant_returns204ForOwner() throws Exception {
        User owner = createActiveUser("sub-var-del@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Variant Delete Product");
        String variantId = addVariantAndGetId(company, product, owner);

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/variants/{vId}",
                        company.getId(), product.getId(), variantId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProductVariant_returns404ForUnknownVariant() throws Exception {
        User owner = createActiveUser("sub-var-del-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Variant Del 404");

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/variants/{vId}",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PUT /attributes ───────────────────────────────────────────────────────

    @Test
    void setProductAttributes_returns200ForOwner() throws Exception {
        User owner = createActiveUser("sub-attr-set@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Attribute Set Product");

        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("name", "Material");
        attr.put("value", "Cotton");
        Map<String, Object> body = Map.of("attributes", List.of(attr));

        mockMvc.perform(put("/companies/{cId}/products/{pId}/attributes",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Material"))
                .andExpect(jsonPath("$.data[0].value").value("Cotton"));
    }

    @Test
    void setProductAttributes_returns400WhenAttributeNameBlank() throws Exception {
        User owner = createActiveUser("sub-attr-blank@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Blank Attr Product");

        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("name", "");
        attr.put("value", "Something");
        Map<String, Object> body = Map.of("attributes", List.of(attr));

        mockMvc.perform(put("/companies/{cId}/products/{pId}/attributes",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setProductAttributes_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("sub-attr-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("sub-attr-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Emp Attr Product");

        Map<String, Object> attr = Map.of("name", "Color", "value", "Red");
        Map<String, Object> body = Map.of("attributes", List.of(attr));

        mockMvc.perform(put("/companies/{cId}/products/{pId}/attributes",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── GET /relationships ────────────────────────────────────────────────────

    @Test
    void getProductRelationships_returnsEmptyListWhenNone() throws Exception {
        User owner = createActiveUser("sub-rel-list@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Relationship Product");

        mockMvc.perform(get("/companies/{cId}/products/{pId}/relationships",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── POST /relationships ───────────────────────────────────────────────────

    @Test
    void addProductRelationship_returns201ForOwner() throws Exception {
        User owner = createActiveUser("sub-rel-add@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product source = createProduct(company, "Source Product");
        Product target = createProduct(company, "Target Product");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetProductId", target.getId().toString());
        body.put("type", "ACCESSORY");

        mockMvc.perform(post("/companies/{cId}/products/{pId}/relationships",
                        company.getId(), source.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated());
    }

    @Test
    void addProductRelationship_returns400WhenTargetIdMissing() throws Exception {
        User owner = createActiveUser("sub-rel-notarget@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "No Target Product");

        Map<String, Object> body = Map.of("type", "ACCESSORY");

        mockMvc.perform(post("/companies/{cId}/products/{pId}/relationships",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addProductRelationship_returns403ForEmployee() throws Exception {
        // Relationship writes require MANAGE_PRODUCTS, like every other product-write op — an
        // EMPLOYEE (read-only product access) must be rejected.
        User owner = createActiveUser("sub-rel-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("sub-rel-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product source = createProduct(company, "Source Emp");
        Product target = createProduct(company, "Target Emp");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetProductId", target.getId().toString());
        body.put("type", "ACCESSORY");

        mockMvc.perform(post("/companies/{cId}/products/{pId}/relationships",
                        company.getId(), source.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /relationships/{targetProductId} ───────────────────────────────

    @Test
    void removeProductRelationship_returns204AfterRemoval() throws Exception {
        User owner = createActiveUser("sub-rel-del@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product source = createProduct(company, "Source Del");
        Product target = createProduct(company, "Target Del");

        Map<String, Object> addBody = new LinkedHashMap<>();
        addBody.put("targetProductId", target.getId().toString());
        addBody.put("type", "REPLACEMENT");
        mockMvc.perform(post("/companies/{cId}/products/{pId}/relationships",
                        company.getId(), source.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addBody))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/relationships/{targetId}",
                        company.getId(), source.getId(), target.getId())
                        .param("type", "REPLACEMENT")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeProductRelationship_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("sub-rel-del-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Del Unauth");

        mockMvc.perform(delete("/companies/{cId}/products/{pId}/relationships/{targetId}",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .param("type", "ACCESSORY"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /similar ──────────────────────────────────────────────────────────

    @Test
    void getSimilarProducts_returnsListForActiveProduct() throws Exception {
        User owner = createActiveUser("sub-similar@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Similar Source");

        mockMvc.perform(get("/companies/{cId}/products/{pId}/similar",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getSimilarProducts_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("sub-similar-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{cId}/products/{pId}/similar",
                        company.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /merchandising ──────────────────────────────────────────────────

    @Test
    void updateProductMerchandising_returns200ForOwner() throws Exception {
        User owner = createActiveUser("sub-merch@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Merchandising Product");

        Map<String, Object> body = Map.of("boostWeight", 5);

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/merchandising",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
    }

    @Test
    void updateProductMerchandising_returns400WhenBoostWeightOutOfRange() throws Exception {
        User owner = createActiveUser("sub-merch-invalid@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Invalid Boost");

        Map<String, Object> body = Map.of("boostWeight", 99);

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/merchandising",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProductMerchandising_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("sub-merch-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("sub-merch-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Emp Merch");

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/merchandising",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boostWeight\":3}")
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /marketplace ────────────────────────────────────────────────────

    @Test
    void updateMarketplaceListing_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("sub-mkt-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Marketplace Unauth");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("marketplaceId", UUID.randomUUID().toString());
        body.put("listed", true);

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/marketplace",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMarketplaceListing_returns400WhenMarketplaceIdMissing() throws Exception {
        User owner = createActiveUser("sub-mkt-noid@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "No Marketplace ID");

        Map<String, Object> body = Map.of("listed", true);

        mockMvc.perform(patch("/companies/{cId}/products/{pId}/marketplace",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── GET /history ──────────────────────────────────────────────────────────

    @Test
    void getProductHistory_returns200WithEmptyPageForOwner() throws Exception {
        User owner = createActiveUser("sub-hist@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "History Product");

        mockMvc.perform(get("/companies/{cId}/products/{pId}/history",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void getProductHistory_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("sub-hist-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "History Unauth");

        mockMvc.perform(get("/companies/{cId}/products/{pId}/history",
                company.getId(), product.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductHistory_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("sub-hist-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{cId}/products/{pId}/history",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /revert ──────────────────────────────────────────────────────────

    @Test
    void revertProductChanges_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("sub-revert-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Revert Unauth");

        Map<String, Object> body = Map.of("logEntryIds", List.of(UUID.randomUUID().toString()));

        mockMvc.perform(post("/companies/{cId}/products/{pId}/revert",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revertProductChanges_returns400WhenLogEntryIdsEmpty() throws Exception {
        User owner = createActiveUser("sub-revert-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Revert Empty");

        Map<String, Object> body = Map.of("logEntryIds", List.of());

        mockMvc.perform(post("/companies/{cId}/products/{pId}/revert",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── POST /reindex ─────────────────────────────────────────────────────────

    @Test
    void triggerReindex_returns202ForMember() throws Exception {
        User owner = createActiveUser("sub-reindex@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{cId}/products/reindex", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isAccepted());
    }

    @Test
    void triggerReindex_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("sub-reindex-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post("/companies/{cId}/products/reindex", company.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void triggerReindex_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("sub-reindex-nm-owner@example.com", "Password1!");
        User stranger = createActiveUser("sub-reindex-nm@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post("/companies/{cId}/products/reindex", company.getId())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── GET /bundles (via ProductController) ─────────────────────────────────

    @Test
    void listBundlesViaProducts_returnsEmptyPage() throws Exception {
        User owner = createActiveUser("sub-bundles-list@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{cId}/products/bundles", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── GET /bundles/{bundleId} (via ProductController) ───────────────────────

    @Test
    void getBundleViaProducts_returns404ForUnknownBundle() throws Exception {
        User owner = createActiveUser("sub-bundles-get-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{cId}/products/bundles/{bundleId}",
                        company.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }
}
