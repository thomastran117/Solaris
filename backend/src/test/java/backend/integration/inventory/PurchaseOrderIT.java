package backend.integration.inventory;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.InventoryAdjustment;
import backend.models.core.Product;
import backend.models.core.RestockRequest;
import backend.models.core.Supplier;
import backend.models.core.User;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.RestockStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.ProductRepository;
import backend.repositories.RestockRequestRepository;
import backend.repositories.SupplierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PurchaseOrderIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private RestockRequestRepository restockRepository;
    @Autowired private InventoryAdjustmentRepository adjustmentRepository;

    @AfterEach
    void cleanPO() {
        try { jdbcTemplate.execute("DELETE FROM inventory_adjustments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM purchase_order_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM purchase_orders"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM suppliers"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM restock_requests"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── POST /companies/{companyId}/purchase-orders ───────────────────────────

    @Test
    void createPO_returns201WithDraftStatusAndCorrectTotalCostCents() throws Exception {
        User owner = createActiveUser("po-create@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        mockMvc.perform(post("/companies/{cid}/purchase-orders", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPOBody(supplier, product, 5, 1000L)))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalCostCents").value(5000))
                .andExpect(jsonPath("$.data.supplierId").value(supplier.getId().toString()))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].orderedQty").value(5));
    }

    // ── POST /companies/{companyId}/purchase-orders/{id}/send ─────────────────

    @Test
    void sendPO_transitionsToSentAndSetsSentAt() throws Exception {
        User owner = createActiveUser("po-send@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        String poId = createPOAndGetId(company, owner, supplier, product, 5, 1000L);

        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/send", company.getId(), poId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.sentAt").isNotEmpty());
    }

    // ── POST /companies/{companyId}/purchase-orders/{id}/confirm ──────────────

    @Test
    void confirmPO_transitionsToConfirmed() throws Exception {
        User owner = createActiveUser("po-confirm@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        String poId = createPOAndGetId(company, owner, supplier, product, 5, 1000L);
        advancePO(company, owner, poId, "send");

        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/confirm", company.getId(), poId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    // ── POST /companies/{companyId}/purchase-orders/{id}/receive ──────────────

    @Test
    void receiveItems_fullReceipt_adjustsStockAndCreatesAdjustmentRecord() throws Exception {
        User owner = createActiveUser("po-recv-full@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        MvcResult createResult = mockMvc.perform(
                        post("/companies/{cid}/purchase-orders", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createPOBody(supplier, product, 5, 500L)))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                .andReturn();
        String poId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asText();
        String itemId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/items/0/id").asText();

        advancePO(company, owner, poId, "send");
        advancePO(company, owner, poId, "confirm");

        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/receive", company.getId(), poId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveBody(itemId, 5)))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.receivedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].receivedQty").value(5));

        Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(refreshed.getStock()).isEqualTo(5);

        List<InventoryAdjustment> adjs = adjustmentRepository.findAll();
        assertThat(adjs).hasSize(1);
        assertThat(adjs.get(0).getReason()).isEqualTo(AdjustmentReason.PURCHASE_ORDER_RECEIPT);
        assertThat(adjs.get(0).getPurchaseOrderId()).isEqualTo(UUID.fromString(poId));
        assertThat(adjs.get(0).getDelta()).isEqualTo(5);
    }

    @Test
    void receiveItems_partialReceipt_transitionsToPartiallyReceived() throws Exception {
        User owner = createActiveUser("po-recv-partial@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        MvcResult createResult = mockMvc.perform(
                        post("/companies/{cid}/purchase-orders", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createPOBody(supplier, product, 10, 200L)))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                .andReturn();
        String poId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asText();
        String itemId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/items/0/id").asText();

        advancePO(company, owner, poId, "send");
        advancePO(company, owner, poId, "confirm");

        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/receive", company.getId(), poId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveBody(itemId, 3)))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.data.items[0].receivedQty").value(3));
    }

    @Test
    void receiveItems_closesLinkedRestockRequestOnFullReceipt() throws Exception {
        User owner = createActiveUser("po-recv-rr@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        RestockRequest rr = createRestockRequest(company, product, owner);

        Map<String, Object> body = createPOBody(supplier, product, 5, 300L);
        body.put("restockRequestId", rr.getId().toString());

        MvcResult createResult = mockMvc.perform(
                        post("/companies/{cid}/purchase-orders", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                .andReturn();
        String poId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asText();
        String itemId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/items/0/id").asText();

        advancePO(company, owner, poId, "send");
        advancePO(company, owner, poId, "confirm");

        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/receive", company.getId(), poId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveBody(itemId, 5)))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));

        RestockRequest refreshed = restockRepository.findById(rr.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(RestockStatus.RECEIVED);
    }

    @Test
    void receiveItems_idempotency_returns400WhenOverageAttempted() throws Exception {
        User owner = createActiveUser("po-recv-overage@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        MvcResult createResult = mockMvc.perform(
                        post("/companies/{cid}/purchase-orders", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createPOBody(supplier, product, 5, 100L)))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                .andReturn();
        String poId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asText();
        String itemId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/items/0/id").asText();

        advancePO(company, owner, poId, "send");
        advancePO(company, owner, poId, "confirm");

        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/receive", company.getId(), poId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveBody(itemId, 6)))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── POST /companies/{companyId}/purchase-orders/{id}/cancel ──────────────

    @Test
    void cancelPO_returns409WhenPOIsAlreadyReceived() throws Exception {
        User owner = createActiveUser("po-cancel-recv@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        MvcResult createResult = mockMvc.perform(
                        post("/companies/{cid}/purchase-orders", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createPOBody(supplier, product, 2, 100L)))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                .andReturn();
        String poId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asText();
        String itemId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/items/0/id").asText();

        advancePO(company, owner, poId, "send");
        advancePO(company, owner, poId, "confirm");
        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/receive", company.getId(), poId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(receiveBody(itemId, 2)))
                .header("Authorization", bearer(accessTokenFor(owner)))
                .header("User-Agent", TEST_USER_AGENT));

        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/cancel", company.getId(), poId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Test
    void createPO_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("po-403-owner@example.com", "Password1!");
        User outsider = createActiveUser("po-403-out@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company, 0);
        Supplier supplier = createSupplier(company);

        mockMvc.perform(post("/companies/{cid}/purchase-orders", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPOBody(supplier, product, 5, 100L)))
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("PO Test Co " + UUID.randomUUID());
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addOwner(User user, Company company) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(CompanyRole.OWNER);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Product createProduct(Company company, int stock) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Widget " + UUID.randomUUID());
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 6));
        p.setPrice(BigDecimal.valueOf(9.99));
        p.setCurrency("USD");
        p.setStatus(ProductStatus.ACTIVE);
        p.setStock(stock);
        return productRepository.save(p);
    }

    private Supplier createSupplier(Company company) {
        Supplier s = new Supplier();
        s.setCompany(company);
        s.setName("Test Supplier");
        s.setEmail("supplier@test.com");
        return supplierRepository.save(s);
    }

    private RestockRequest createRestockRequest(Company company, Product product, User createdBy) {
        RestockRequest rr = new RestockRequest();
        rr.setCompany(company);
        rr.setProduct(product);
        rr.setRequestedQty(5);
        rr.setStatus(RestockStatus.PENDING);
        rr.setCreatedBy(createdBy);
        return restockRepository.save(rr);
    }

    private Map<String, Object> createPOBody(Supplier supplier, Product product, int qty, long unitCostCents) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", product.getId().toString());
        item.put("orderedQty", qty);
        item.put("unitCostCents", unitCostCents);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplierId", supplier.getId().toString());
        body.put("items", List.of(item));
        return body;
    }

    private Map<String, Object> receiveBody(String itemId, int qty) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("itemId", itemId);
        line.put("receivedQty", qty);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(line));
        return body;
    }

    private String createPOAndGetId(Company company, User owner, Supplier supplier, Product product,
                                     int qty, long unitCostCents) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/companies/{cid}/purchase-orders", company.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createPOBody(supplier, product, qty, unitCostCents)))
                                .header("Authorization", bearer(accessTokenFor(owner)))
                                .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
    }

    private void advancePO(Company company, User owner, String poId, String action) throws Exception {
        mockMvc.perform(post("/companies/{cid}/purchase-orders/{id}/" + action, company.getId(), poId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
    }
}
