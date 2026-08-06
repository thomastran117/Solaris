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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductBatchIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Batch Test Company " + UUID.randomUUID());
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

    private Map<String, Object> validProductBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("price", 9.99);
        body.put("stock", 5);
        return body;
    }

    // ── POST /companies/{companyId}/products/batch-create ─────────────────────

    @Test
    void batchCreateProducts_returns201ForOwner() throws Exception {
        User owner = createActiveUser("batch-create-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = Map.of("products", List.of(
                validProductBody("Batch Product A"),
                validProductBody("Batch Product B")
        ));

        mockMvc.perform(post("/companies/{companyId}/products/batch-create", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data", hasSize(2)));

        assertEquals(2, productRepository.count(), "Both products should be persisted");
    }

    @Test
    void batchCreateProducts_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("batch-create-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("batch-create-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        Map<String, Object> body = Map.of("products", List.of(validProductBody("Forbidden")));

        mockMvc.perform(post("/companies/{companyId}/products/batch-create", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchCreateProducts_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("batch-create-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        Map<String, Object> body = Map.of("products", List.of(validProductBody("Unauth")));

        mockMvc.perform(post("/companies/{companyId}/products/batch-create", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchCreateProducts_returns400WhenProductsListEmpty() throws Exception {
        User owner = createActiveUser("batch-create-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = Map.of("products", new ArrayList<>());

        mockMvc.perform(post("/companies/{companyId}/products/batch-create", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchCreateProducts_returns400WhenProductHasNoName() throws Exception {
        User owner = createActiveUser("batch-create-noname@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> badProduct = Map.of("price", 5.0);
        Map<String, Object> body = Map.of("products", List.of(badProduct));

        mockMvc.perform(post("/companies/{companyId}/products/batch-create", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── POST /companies/{companyId}/products/batch-delete ─────────────────────

    @Test
    void batchDeleteProducts_returns204ForOwner() throws Exception {
        User owner = createActiveUser("batch-del-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product p1 = createProduct(company, "Delete Me 1");
        Product p2 = createProduct(company, "Delete Me 2");

        Map<String, Object> body = Map.of("ids", List.of(p1.getId().toString(), p2.getId().toString()));

        mockMvc.perform(post("/companies/{companyId}/products/batch-delete", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        assertTrue(productRepository.findById(p1.getId()).isEmpty());
        assertTrue(productRepository.findById(p2.getId()).isEmpty());
    }

    @Test
    void batchDeleteProducts_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("batch-del-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("batch-del-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Employee Cannot Delete");

        Map<String, Object> body = Map.of("ids", List.of(product.getId().toString()));

        mockMvc.perform(post("/companies/{companyId}/products/batch-delete", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchDeleteProducts_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("batch-del-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Delete Unauth");

        Map<String, Object> body = Map.of("ids", List.of(product.getId().toString()));

        mockMvc.perform(post("/companies/{companyId}/products/batch-delete", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchDeleteProducts_returns400WhenIdsEmpty() throws Exception {
        User owner = createActiveUser("batch-del-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = Map.of("ids", new ArrayList<>());

        mockMvc.perform(post("/companies/{companyId}/products/batch-delete", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── POST /companies/{companyId}/products/batch-update ─────────────────────

    @Test
    void batchUpdateProducts_returns200ForOwner() throws Exception {
        User owner = createActiveUser("batch-upd-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Update Me");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(product.getId().toString()));
        body.put("status", "ARCHIVED");

        mockMvc.perform(post("/companies/{companyId}/products/batch-update", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        assertEquals(ProductStatus.ARCHIVED,
                productRepository.findById(product.getId()).orElseThrow().getStatus());
    }

    @Test
    void batchUpdateProducts_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("batch-upd-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("batch-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Emp Cannot Update");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(product.getId().toString()));
        body.put("status", "ARCHIVED");

        mockMvc.perform(post("/companies/{companyId}/products/batch-update", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchUpdateProducts_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("batch-upd-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Update Unauth");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(product.getId().toString()));
        body.put("status", "ARCHIVED");

        mockMvc.perform(post("/companies/{companyId}/products/batch-update", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchUpdateProducts_returns400WhenNoFieldsProvided() throws Exception {
        User owner = createActiveUser("batch-upd-nofield@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "No Field Update");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(product.getId().toString()));

        mockMvc.perform(post("/companies/{companyId}/products/batch-update", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── POST /companies/{companyId}/products/{productId}/duplicate ────────────

    @Test
    void duplicateProduct_returns201WithCopiedProduct() throws Exception {
        User owner = createActiveUser("batch-dup-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company, "Original Product");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/duplicate",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(containsString("Original Product")));

        // The duplicate must be persisted alongside the original.
        assertEquals(2, productRepository.count(), "Duplicated product should be persisted");
    }

    @Test
    void duplicateProduct_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("batch-dup-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("batch-dup-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company, "Cannot Dup");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/duplicate",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateProduct_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("batch-dup-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Dup Unauth");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/duplicate",
                        company.getId(), product.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateProduct_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("batch-dup-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/duplicate",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{companyId}/products/batch ────────────────────────────

    @Test
    void getProductsByIds_returns200WithRequestedProducts() throws Exception {
        User owner = createActiveUser("batch-get-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product p1 = createProduct(company, "Batch Get A");
        Product p2 = createProduct(company, "Batch Get B");

        List<String> ids = List.of(p1.getId().toString(), p2.getId().toString());

        mockMvc.perform(post("/companies/{companyId}/products/batch", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void getProductsByIds_returns200WithEmptyListForUnknownIds() throws Exception {
        User owner = createActiveUser("batch-get-unknown@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        List<String> ids = List.of(UUID.randomUUID().toString());

        mockMvc.perform(post("/companies/{companyId}/products/batch", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void getProductsByIds_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("batch-get-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post("/companies/{companyId}/products/batch", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductsByIds_returns404ForUnknownCompany() throws Exception {
        User owner = createActiveUser("batch-get-404@example.com", "Password1!");
        addMember(owner, createCompany(owner), CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/products/batch", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }
}
