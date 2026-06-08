package backend.integration.returns;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.CompanyReturnLocation;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.Return;
import backend.models.core.ReturnItem;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.ReturnReason;
import backend.models.enums.ReturnStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.CompanyReturnLocationRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ReturnItemRepository;
import backend.repositories.ReturnRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyReturnIT extends AbstractIntegrationIT {

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ReturnRepository returnRepository;
    @Autowired private ReturnItemRepository returnItemRepository;
    @Autowired private CompanyReturnLocationRepository locationRepository;

    @AfterEach
    void cleanReturns() {
        try { jdbcTemplate.execute("DELETE FROM return_evidence_urls"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM return_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM returns"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM risk_assessments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM order_compensation_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM order_compensations"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM order_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM orders"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_return_locations"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Company " + UUID.randomUUID());
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

    private void createReturnLocation(Company company) {
        CompanyReturnLocation loc = new CompanyReturnLocation();
        loc.setCompany(company);
        loc.setAddress("123 Warehouse Rd");
        loc.setCity("Austin");
        loc.setCountry("US");
        loc.setPrimary(true);
        locationRepository.save(loc);
    }

    private Product createProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Product " + UUID.randomUUID());
        p.setPrice(BigDecimal.valueOf(25.00));
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    /**
     * Sets up the full chain: Order → OrderItem (product linked to company) → Return → ReturnItem.
     * Returns the persisted Return with its ReturnItem referencing the company's product.
     */
    private Return createReturnForCompany(User buyer, Company company, Product product) {
        Order order = new Order();
        order.setUser(buyer);
        order.setTotalAmount(BigDecimal.valueOf(25.00));
        order.setStatus(OrderStatus.DELIVERED);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setProductName(product.getName());
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(product.getPrice());
        orderItem.setFulfillmentStatus(FulfillmentStatus.DELIVERED);

        order.getItems().add(orderItem);
        Order savedOrder = orderRepository.save(order);
        OrderItem savedItem = savedOrder.getItems().get(0);

        Return ret = new Return();
        ret.setOrder(savedOrder);
        ret.setRequestedBy(buyer);
        ret.setStatus(ReturnStatus.REQUESTED);
        ret.setReason(ReturnReason.CHANGED_MIND);
        ret.setBuyerNote("I changed my mind");
        Return savedReturn = returnRepository.save(ret);

        ReturnItem ri = new ReturnItem();
        ri.setReturnRequest(savedReturn);
        ri.setOrderItem(savedItem);
        ri.setQuantityReturned(1);
        returnItemRepository.save(ri);

        return savedReturn;
    }

    // ── GET /companies/{companyId}/returns/{returnId} ─────────────────────────

    @Test
    void getCompanyReturn_ownerCanViewReturn() throws Exception {
        User owner = createActiveUser("comp-ret-get-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        User buyer = createActiveUser("comp-ret-get-buyer@example.com", "Password1!");
        Product product = createProduct(company);
        Return ret = createReturnForCompany(buyer, company, product);

        mockMvc.perform(get("/companies/{companyId}/returns/{returnId}", company.getId(), ret.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ret.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));
    }

    @Test
    void getCompanyReturn_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("comp-ret-403-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        User outsider = createActiveUser("comp-ret-403-outsider@example.com", "Password1!");
        User buyer = createActiveUser("comp-ret-403-buyer@example.com", "Password1!");
        Product product = createProduct(company);
        Return ret = createReturnForCompany(buyer, company, product);

        mockMvc.perform(get("/companies/{companyId}/returns/{returnId}", company.getId(), ret.getId())
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCompanyReturn_returns404ForUnknownReturnId() throws Exception {
        User owner = createActiveUser("comp-ret-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/returns/{returnId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{companyId}/returns/{returnId}/approve ────────────────

    @Test
    void approveReturn_ownerCanApproveRequestedReturn() throws Exception {
        User owner = createActiveUser("comp-ret-approve-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createReturnLocation(company);
        User buyer = createActiveUser("comp-ret-approve-buyer@example.com", "Password1!");
        Product product = createProduct(company);
        Return ret = createReturnForCompany(buyer, company, product);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantNote", "Approved");

        mockMvc.perform(post("/companies/{companyId}/returns/{returnId}/approve",
                        company.getId(), ret.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ret.getId().toString()));
    }

    @Test
    void approveReturn_returns409WhenAlreadyApproved() throws Exception {
        User owner = createActiveUser("comp-ret-approve2-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        User buyer = createActiveUser("comp-ret-approve2-buyer@example.com", "Password1!");
        Product product = createProduct(company);
        Return ret = createReturnForCompany(buyer, company, product);

        // Manually mark as already APPROVED
        ret.setStatus(ReturnStatus.APPROVED);
        returnRepository.save(ret);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantNote", "Approve again");

        mockMvc.perform(post("/companies/{companyId}/returns/{returnId}/approve",
                        company.getId(), ret.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void approveReturn_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("comp-ret-approve3-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        User outsider = createActiveUser("comp-ret-approve3-out@example.com", "Password1!");
        User buyer = createActiveUser("comp-ret-approve3-buyer@example.com", "Password1!");
        Product product = createProduct(company);
        Return ret = createReturnForCompany(buyer, company, product);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantNote", "Approve");

        mockMvc.perform(post("/companies/{companyId}/returns/{returnId}/approve",
                        company.getId(), ret.getId())
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── POST /companies/{companyId}/returns/{returnId}/reject ─────────────────

    @Test
    void rejectReturn_ownerCanRejectRequestedReturn() throws Exception {
        User owner = createActiveUser("comp-ret-reject-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        User buyer = createActiveUser("comp-ret-reject-buyer@example.com", "Password1!");
        Product product = createProduct(company);
        Return ret = createReturnForCompany(buyer, company, product);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantNote", "Return window expired on our end");

        mockMvc.perform(post("/companies/{companyId}/returns/{returnId}/reject",
                        company.getId(), ret.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void rejectReturn_returns400WhenMerchantNoteBlank() throws Exception {
        User owner = createActiveUser("comp-ret-reject2-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        User buyer = createActiveUser("comp-ret-reject2-buyer@example.com", "Password1!");
        Product product = createProduct(company);
        Return ret = createReturnForCompany(buyer, company, product);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantNote", "");

        mockMvc.perform(post("/companies/{companyId}/returns/{returnId}/reject",
                        company.getId(), ret.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectReturn_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("comp-ret-reject3-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        User outsider = createActiveUser("comp-ret-reject3-out@example.com", "Password1!");
        User buyer = createActiveUser("comp-ret-reject3-buyer@example.com", "Password1!");
        Product product = createProduct(company);
        Return ret = createReturnForCompany(buyer, company, product);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantNote", "Rejected");

        mockMvc.perform(post("/companies/{companyId}/returns/{returnId}/reject",
                        company.getId(), ret.getId())
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }
}
