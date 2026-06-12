package backend.integration.orders;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyOrderIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM order_status_history"); } catch (Exception ignored) {}
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
        c.setName("Test Company " + UUID.randomUUID());
        c.setStatus(CompanyStatus.ACTIVE);
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
        p.setName("Test Product");
        p.setPrice(BigDecimal.TEN);
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    /**
     * Creates an order with one item whose product belongs to the given company.
     * This is required for {@code findByIdAndProductCompanyId} to resolve the order.
     */
    private Order createOrderWithItem(User customer, Product product,
                                      OrderStatus orderStatus, FulfillmentStatus itemStatus) {
        Order order = new Order();
        order.setUser(customer);
        order.setTotalAmount(BigDecimal.valueOf(9.99));
        order.setStatus(orderStatus);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(9.99));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setPromotionSavings(BigDecimal.ZERO);
        item.setProductName(product.getName());
        item.setFulfillmentStatus(itemStatus);

        order.getItems().add(item);
        return orderRepository.save(order);
    }

    // ── GET /companies/{companyId}/orders ─────────────────────────────────────

    @Test
    void getCompanyOrders_returnsEmptyPageWhenNoOrders() throws Exception {
        User owner = createActiveUser("co-orders-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{cid}/orders", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getCompanyOrders_returnsOrdersLinkedToCompany() throws Exception {
        User owner = createActiveUser("co-orders-list@example.com", "Password1!");
        User customer = createActiveUser("co-orders-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        createOrderWithItem(customer, product, OrderStatus.PAID, FulfillmentStatus.PENDING);

        mockMvc.perform(get("/companies/{cid}/orders", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void getCompanyOrders_filtersByStatus() throws Exception {
        User owner = createActiveUser("co-orders-filter@example.com", "Password1!");
        User customer = createActiveUser("co-orders-filter-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        createOrderWithItem(customer, product, OrderStatus.PAID, FulfillmentStatus.PENDING);
        createOrderWithItem(customer, product, OrderStatus.SHIPPED, FulfillmentStatus.SHIPPED);

        mockMvc.perform(get("/companies/{cid}/orders", company.getId())
                        .param("status", "PAID")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderStatus").value("PAID"));
    }

    @Test
    void getCompanyOrders_returnsOrdersForEmployeeMember() throws Exception {
        User owner = createActiveUser("co-orders-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("co-orders-emp@example.com", "Password1!");
        User customer = createActiveUser("co-orders-emp-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        Product product = createProduct(company);
        createOrderWithItem(customer, product, OrderStatus.PAID, FulfillmentStatus.PENDING);

        mockMvc.perform(get("/companies/{cid}/orders", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void getCompanyOrders_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("co-orders-403-owner@example.com", "Password1!");
        User stranger = createActiveUser("co-orders-403-stranger@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{cid}/orders", company.getId())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCompanyOrders_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/companies/{cid}/orders", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/orders/{orderId} ───────────────────────────

    @Test
    void getCompanyOrder_returns200ForMember() throws Exception {
        User owner = createActiveUser("co-order-get@example.com", "Password1!");
        User customer = createActiveUser("co-order-get-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrderWithItem(customer, product, OrderStatus.PAID, FulfillmentStatus.PENDING);

        mockMvc.perform(get("/companies/{cid}/orders/{oid}", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(order.getId().toString()))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"));
    }

    @Test
    void getCompanyOrder_returns404ForUnknownOrder() throws Exception {
        User owner = createActiveUser("co-order-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{cid}/orders/{oid}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCompanyOrder_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("co-order-403-owner@example.com", "Password1!");
        User stranger = createActiveUser("co-order-403-stranger@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{cid}/orders/{oid}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCompanyOrder_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/companies/{cid}/orders/{oid}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/orders/{orderId}/pack ─────────────────────

    @Test
    void markAsPacked_returns200WithPackedStatus() throws Exception {
        User owner = createActiveUser("co-pack@example.com", "Password1!");
        User customer = createActiveUser("co-pack-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrderWithItem(customer, product, OrderStatus.PAID, FulfillmentStatus.PENDING);

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/pack", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PACKED"));
    }

    @Test
    void markAsPacked_returns409WhenNotInPaidStatus() throws Exception {
        User owner = createActiveUser("co-pack-409@example.com", "Password1!");
        User customer = createActiveUser("co-pack-409-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrderWithItem(customer, product, OrderStatus.DELIVERED, FulfillmentStatus.DELIVERED);

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/pack", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void markAsPacked_returns403ForNonMember() throws Exception {
        User stranger = createActiveUser("co-pack-403@example.com", "Password1!");

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/pack", UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAsPacked_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/companies/{cid}/orders/{oid}/pack", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/orders/{orderId}/ship ─────────────────────

    @Test
    void markAsShipped_returns200WithShippedStatus() throws Exception {
        User owner = createActiveUser("co-ship@example.com", "Password1!");
        User customer = createActiveUser("co-ship-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        // Items must be PACKED so ship logic marks them SHIPPED and sets order status SHIPPED
        Order order = createOrderWithItem(customer, product, OrderStatus.PACKED, FulfillmentStatus.PACKED);

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/ship", company.getId(), order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"TRACK123456\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("SHIPPED"));
    }

    @Test
    void markAsShipped_returns400WhenTrackingNumberMissing() throws Exception {
        User owner = createActiveUser("co-ship-400@example.com", "Password1!");
        User customer = createActiveUser("co-ship-400-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrderWithItem(customer, product, OrderStatus.PACKED, FulfillmentStatus.PACKED);

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/ship", company.getId(), order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markAsShipped_returns403ForNonMember() throws Exception {
        User stranger = createActiveUser("co-ship-403@example.com", "Password1!");

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/ship", UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"TRACK123456\"}")
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAsShipped_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/companies/{cid}/orders/{oid}/ship", UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"TRACK123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/orders/{orderId}/deliver ──────────────────

    @Test
    void markAsDelivered_returns200WithDeliveredStatus() throws Exception {
        User owner = createActiveUser("co-deliver@example.com", "Password1!");
        User customer = createActiveUser("co-deliver-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        // SHIPPED order with SHIPPED items — markAsDelivered transitions both to DELIVERED
        Order order = createOrderWithItem(customer, product, OrderStatus.SHIPPED, FulfillmentStatus.SHIPPED);

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/deliver", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"));
    }

    @Test
    void markAsDelivered_returns409WhenNotInShippedStatus() throws Exception {
        User owner = createActiveUser("co-deliver-409@example.com", "Password1!");
        User customer = createActiveUser("co-deliver-409-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrderWithItem(customer, product, OrderStatus.PAID, FulfillmentStatus.PENDING);

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/deliver", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void markAsDelivered_returns403ForNonMember() throws Exception {
        User stranger = createActiveUser("co-deliver-403@example.com", "Password1!");

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/deliver", UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAsDelivered_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/companies/{cid}/orders/{oid}/deliver", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/orders/{orderId}/cancel ───────────────────

    @Test
    void cancelByCompany_returns200WithCancelledStatus() throws Exception {
        User owner = createActiveUser("co-cancel@example.com", "Password1!");
        User customer = createActiveUser("co-cancel-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        // No paymentIntentId → no Stripe call; cancellation proceeds cleanly
        Order order = createOrderWithItem(customer, product, OrderStatus.PAID, FulfillmentStatus.PENDING);

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/cancel", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));
    }

    @Test
    void cancelByCompany_returns409WhenOrderIsAlreadyDelivered() throws Exception {
        User owner = createActiveUser("co-cancel-409@example.com", "Password1!");
        User customer = createActiveUser("co-cancel-409-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrderWithItem(customer, product, OrderStatus.DELIVERED, FulfillmentStatus.DELIVERED);

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/cancel", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelByCompany_returns403ForNonMember() throws Exception {
        User stranger = createActiveUser("co-cancel-403@example.com", "Password1!");

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/cancel", UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelByCompany_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/companies/{cid}/orders/{oid}/cancel", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
