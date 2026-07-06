package backend.integration.products;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Product;
import backend.models.core.RestockRequest;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.ProductStatus;
import backend.models.enums.RestockStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.RestockRequestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RestockIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private RestockRequestRepository restockRepository;

    @AfterEach
    void cleanRestock() {
        try { jdbcTemplate.execute("DELETE FROM inventory_adjustments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM restock_requests"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Restock Company " + UUID.randomUUID());
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

    private Product createProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Product " + UUID.randomUUID());
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setStatus(ProductStatus.ACTIVE);
        p.setStock(10);
        return productRepository.save(p);
    }

    private RestockRequest createRestockDirect(Company company, Product product, User createdBy) {
        RestockRequest rr = new RestockRequest();
        rr.setCompany(company);
        rr.setProduct(product);
        rr.setRequestedQty(50);
        rr.setStatus(RestockStatus.PENDING);
        rr.setCreatedBy(createdBy);
        return restockRepository.save(rr);
    }

    private String createRestockBody(UUID productId, int qty) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId.toString());
        body.put("requestedQty", qty);
        return objectMapper.writeValueAsString(body);
    }

    // ── GET /companies/{companyId}/inventory/restock ──────────────────────────

    @Test
    void listRestockRequests_returnsEmptyWhenNone() throws Exception {
        User owner = createActiveUser("restock-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/inventory/restock", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listRestockRequests_returnsCreatedRequest() throws Exception {
        User owner = createActiveUser("restock-list-one@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        createRestockDirect(company, product, owner);

        mockMvc.perform(get("/companies/{companyId}/inventory/restock", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].requestedQty").value(50));
    }

    @Test
    void listRestockRequests_filterByStatus() throws Exception {
        User owner = createActiveUser("restock-list-status@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);
        rr.setStatus(RestockStatus.IN_TRANSIT);
        restockRepository.save(rr);
        createRestockDirect(company, product, owner); // second one stays PENDING

        mockMvc.perform(get("/companies/{companyId}/inventory/restock", company.getId())
                        .param("status", "IN_TRANSIT")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("IN_TRANSIT"));
    }

    @Test
    void listRestockRequests_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("restock-list-403-owner@example.com", "Password1!");
        User employee = createActiveUser("restock-list-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(get("/companies/{companyId}/inventory/restock", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRestockRequests_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("restock-list-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/inventory/restock", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/inventory/restock ─────────────────────────

    @Test
    void createRestock_returns201WithValidRequest() throws Exception {
        User owner = createActiveUser("restock-create-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);

        MvcResult result = mockMvc.perform(post("/companies/{companyId}/inventory/restock", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRestockBody(product.getId(), 100)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestedQty").value(100))
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
        RestockRequest persisted = restockRepository.findById(id).orElseThrow();
        assertEquals(RestockStatus.PENDING, persisted.getStatus());
        assertEquals(100, persisted.getRequestedQty());
        assertEquals(product.getId(), persisted.getProduct().getId());
    }

    @Test
    void createRestock_returns400WhenProductIdMissing() throws Exception {
        User owner = createActiveUser("restock-create-400-pid@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestedQty", 50);

        mockMvc.perform(post("/companies/{companyId}/inventory/restock", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRestock_returns400WhenQtyIsZero() throws Exception {
        User owner = createActiveUser("restock-create-400-qty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);

        mockMvc.perform(post("/companies/{companyId}/inventory/restock", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRestockBody(product.getId(), 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRestock_returns404WhenProductNotFound() throws Exception {
        User owner = createActiveUser("restock-create-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/inventory/restock", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRestockBody(UUID.randomUUID(), 50)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRestock_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("restock-create-403-owner@example.com", "Password1!");
        User employee = createActiveUser("restock-create-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company);

        mockMvc.perform(post("/companies/{companyId}/inventory/restock", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRestockBody(product.getId(), 50)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRestock_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("restock-create-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);

        mockMvc.perform(post("/companies/{companyId}/inventory/restock", company.getId())
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRestockBody(product.getId(), 50)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/inventory/restock/{id} ────────────────────

    @Test
    void getRestockRequest_returns200ForOwner() throws Exception {
        User owner = createActiveUser("restock-get-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        mockMvc.perform(get("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(rr.getId().toString()))
                .andExpect(jsonPath("$.data.requestedQty").value(50));
    }

    @Test
    void getRestockRequest_returns404WhenNotFound() throws Exception {
        User owner = createActiveUser("restock-get-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRestockRequest_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("restock-get-403-owner@example.com", "Password1!");
        User employee = createActiveUser("restock-get-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        mockMvc.perform(get("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRestockRequest_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("restock-get-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        mockMvc.perform(get("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /companies/{companyId}/inventory/restock/{id} ──────────────────

    @Test
    void updateRestock_updatesSupplierNote() throws Exception {
        User owner = createActiveUser("restock-patch-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplierNote", "Updated supplier note");

        mockMvc.perform(patch("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierNote").value("Updated supplier note"));

        assertEquals("Updated supplier note",
                restockRepository.findById(rr.getId()).orElseThrow().getSupplierNote());
    }

    @Test
    void updateRestock_returns400WhenStatusReceivedButQtyMissing() throws Exception {
        User owner = createActiveUser("restock-patch-400-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "RECEIVED");
        // receivedQty intentionally omitted

        mockMvc.perform(patch("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRestock_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("restock-patch-403-owner@example.com", "Password1!");
        User employee = createActiveUser("restock-patch-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplierNote", "Note");

        mockMvc.perform(patch("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /companies/{companyId}/inventory/restock/{id} ─────────────────

    @Test
    void deleteRestock_returns204ForPendingRequest() throws Exception {
        User owner = createActiveUser("restock-del-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        mockMvc.perform(delete("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        assertTrue(restockRepository.findById(rr.getId()).isEmpty(),
                "Deleted restock request should be removed from the database");
    }

    @Test
    void deleteRestock_returns404WhenNotFound() throws Exception {
        User owner = createActiveUser("restock-del-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRestock_returns400WhenStatusIsInTransit() throws Exception {
        User owner = createActiveUser("restock-del-400-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);
        rr.setStatus(RestockStatus.IN_TRANSIT);
        restockRepository.save(rr);

        mockMvc.perform(delete("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRestock_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("restock-del-403-owner@example.com", "Password1!");
        User employee = createActiveUser("restock-del-403-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        mockMvc.perform(delete("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRestock_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("restock-del-401-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        RestockRequest rr = createRestockDirect(company, product, owner);

        mockMvc.perform(delete("/companies/{companyId}/inventory/restock/{id}",
                        company.getId(), rr.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }
}
