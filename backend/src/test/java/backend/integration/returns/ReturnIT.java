package backend.integration.returns;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.OrderRepository;
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

class ReturnIT extends AbstractIntegrationIT {

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CompanyRepository companyRepository;

    @AfterEach
    void cleanReturns() {
        try { jdbcTemplate.execute("DELETE FROM return_evidence_urls"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM return_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM returns"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM risk_assessments"); } catch (Exception ignored) {}
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
        c.setName("Return Company " + UUID.randomUUID());
        return companyRepository.save(c);
    }

    private Product createProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Return Product " + UUID.randomUUID());
        p.setPrice(BigDecimal.valueOf(20.00));
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    /**
     * Creates a DELIVERED order with one OrderItem (fulfillmentStatus=DELIVERED) via cascade.
     * deliveredAt is intentionally null to skip the return-window check.
     */
    private OrderItem createDeliveredOrderWithItem(User buyer, Product product) {
        Order order = new Order();
        order.setUser(buyer);
        order.setTotalAmount(BigDecimal.valueOf(20.00));
        order.setStatus(OrderStatus.DELIVERED);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(product.getPrice());
        item.setFulfillmentStatus(FulfillmentStatus.DELIVERED);

        order.getItems().add(item);
        Order saved = orderRepository.save(order);
        return saved.getItems().get(0);
    }

    private String returnBody(UUID orderItemId, String reason) throws Exception {
        return returnBody(orderItemId, reason, null);
    }

    private String returnBody(UUID orderItemId, String reason, List<String> evidenceUrls) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderItemId", orderItemId.toString());
        item.put("quantityToReturn", 1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("reason", reason);
        body.put("buyerNote", "I want to return this item");
        if (evidenceUrls != null) {
            body.put("evidenceUrls", evidenceUrls);
        }
        return objectMapper.writeValueAsString(body);
    }

    // ── POST /orders/{orderId}/returns ────────────────────────────────────────

    @Test
    void requestReturn_returns201ForDeliveredOrder() throws Exception {
        User owner = createActiveUser("return-req-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        User buyer = createActiveUser("return-req-buyer@example.com", "Password1!");
        OrderItem item = createDeliveredOrderWithItem(buyer, product);
        UUID orderId = item.getOrder().getId();

        mockMvc.perform(post("/orders/{orderId}/returns", orderId)
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(item.getId(), "CHANGED_MIND")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.reason").value("CHANGED_MIND"))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()));
    }

    @Test
    void requestReturn_returns404ForUnknownOrder() throws Exception {
        User buyer = createActiveUser("return-404@example.com", "Password1!");

        mockMvc.perform(post("/orders/{orderId}/returns", UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(UUID.randomUUID(), "CHANGED_MIND")))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestReturn_returns409WhenOrderNotDelivered() throws Exception {
        User owner = createActiveUser("return-conflict-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        User buyer = createActiveUser("return-conflict-buyer@example.com", "Password1!");

        Order order = new Order();
        order.setUser(buyer);
        order.setTotalAmount(BigDecimal.valueOf(20.00));
        order.setStatus(OrderStatus.PAID);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(product.getPrice());
        item.setFulfillmentStatus(FulfillmentStatus.PACKED);

        order.getItems().add(item);
        Order savedOrder = orderRepository.save(order);
        UUID itemId = savedOrder.getItems().get(0).getId();

        mockMvc.perform(post("/orders/{orderId}/returns", savedOrder.getId())
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(itemId, "CHANGED_MIND")))
                .andExpect(status().isConflict());
    }

    @Test
    void requestReturn_returns400WhenEvidenceRequiredButMissing() throws Exception {
        User owner = createActiveUser("return-evid-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        User buyer = createActiveUser("return-evid-buyer@example.com", "Password1!");
        OrderItem item = createDeliveredOrderWithItem(buyer, product);
        UUID orderId = item.getOrder().getId();

        mockMvc.perform(post("/orders/{orderId}/returns", orderId)
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(item.getId(), "DEFECTIVE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestReturn_accepts201WhenEvidenceProvidedForDefective() throws Exception {
        User owner = createActiveUser("return-evid-ok-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        User buyer = createActiveUser("return-evid-ok-buyer@example.com", "Password1!");
        OrderItem item = createDeliveredOrderWithItem(buyer, product);
        UUID orderId = item.getOrder().getId();

        mockMvc.perform(post("/orders/{orderId}/returns", orderId)
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(item.getId(), "DEFECTIVE",
                                List.of("https://example.com/evidence.jpg"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reason").value("DEFECTIVE"));
    }

    @Test
    void requestReturn_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/returns", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(UUID.randomUUID(), "CHANGED_MIND")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /orders/{orderId}/returns ─────────────────────────────────────────

    @Test
    void getReturnsByOrder_ownerSeesOwnReturns() throws Exception {
        User owner = createActiveUser("ret-list-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        User buyer = createActiveUser("ret-list-buyer@example.com", "Password1!");
        OrderItem item = createDeliveredOrderWithItem(buyer, product);
        UUID orderId = item.getOrder().getId();

        mockMvc.perform(post("/orders/{orderId}/returns", orderId)
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnBody(item.getId(), "OTHER")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/orders/{orderId}/returns", orderId)
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderId").value(orderId.toString()));
    }

    @Test
    void getReturnsByOrder_returns404ForOtherUsersOrder() throws Exception {
        User owner = createActiveUser("ret-list-own2@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        User buyer = createActiveUser("ret-list-buyer2@example.com", "Password1!");
        User other = createActiveUser("ret-list-other@example.com", "Password1!");
        OrderItem item = createDeliveredOrderWithItem(buyer, product);
        UUID orderId = item.getOrder().getId();

        mockMvc.perform(get("/orders/{orderId}/returns", orderId)
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }
}
