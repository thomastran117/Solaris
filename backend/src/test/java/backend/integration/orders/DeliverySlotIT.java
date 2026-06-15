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
import backend.models.enums.DeliverySlotStatus;
import backend.models.enums.FulfillmentMethod;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the scheduled / time-slot delivery feature (Feature 06):
 * the customer slot PATCH, the vendor confirm / unavailable transitions, and the
 * fulfillment-queue delivery-date filter.
 */
class DeliverySlotIT extends AbstractIntegrationIT {

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

    private Order createOrder(User customer, Product product, OrderStatus status, FulfillmentMethod method) {
        Order order = new Order();
        order.setUser(customer);
        order.setTotalAmount(BigDecimal.valueOf(9.99));
        order.setStatus(status);
        order.setFulfillmentMethod(method);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(9.99));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setPromotionSavings(BigDecimal.ZERO);
        item.setProductName(product.getName());
        item.setFulfillmentStatus(FulfillmentStatus.PENDING);

        order.getItems().add(item);
        return orderRepository.save(order);
    }

    private String slotBody(LocalDate date, String window) {
        return "{\"preferredDeliveryDate\":\"" + date + "\""
                + (window != null ? ",\"preferredDeliveryWindow\":\"" + window + "\"" : "")
                + "}";
    }

    // ── PATCH /orders/{orderId}/delivery-slot (customer) ──────────────────────

    @Test
    void shouldSetSlotWhenCustomerRequestsValidDateOnPaidDeliveryOrder() throws Exception {
        User customer = createActiveUser("slot-set@example.com", "Password1!");
        User owner = createActiveUser("slot-set-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);

        mockMvc.perform(patch("/orders/{oid}/delivery-slot", order.getId())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotBody(LocalDate.now().plusDays(3), "MORNING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliverySlotStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.preferredDeliveryWindow").value("MORNING"));
    }

    @Test
    void shouldReturn400WhenDeliveryDateInPast() throws Exception {
        User customer = createActiveUser("slot-past@example.com", "Password1!");
        User owner = createActiveUser("slot-past-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);

        mockMvc.perform(patch("/orders/{oid}/delivery-slot", order.getId())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotBody(LocalDate.now().minusDays(1), "MORNING")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenDeliveryDateBeyond14Days() throws Exception {
        User customer = createActiveUser("slot-far@example.com", "Password1!");
        User owner = createActiveUser("slot-far-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);

        mockMvc.perform(patch("/orders/{oid}/delivery-slot", order.getId())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotBody(LocalDate.now().plusDays(15), "MORNING")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenSlotRequestedOnPickupOrder() throws Exception {
        User customer = createActiveUser("slot-pickup@example.com", "Password1!");
        User owner = createActiveUser("slot-pickup-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.PICKUP);

        mockMvc.perform(patch("/orders/{oid}/delivery-slot", order.getId())
                        .header("Authorization", bearer(accessTokenFor(customer)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotBody(LocalDate.now().plusDays(3), "MORNING")))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /companies/{companyId}/orders/{orderId}/delivery-slot/confirm ────

    @Test
    void shouldTransitionToConfirmedWhenVendorConfirms() throws Exception {
        User owner = createActiveUser("slot-confirm-owner@example.com", "Password1!");
        User customer = createActiveUser("slot-confirm-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);
        order.setPreferredDeliveryDate(LocalDate.now().plusDays(4));
        order.setDeliverySlotStatus(DeliverySlotStatus.REQUESTED);
        orderRepository.save(order);

        mockMvc.perform(patch("/companies/{cid}/orders/{oid}/delivery-slot/confirm", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliverySlotStatus").value("CONFIRMED"));
    }

    @Test
    void shouldReturn400WhenConfirmingOrderWithoutSlot() throws Exception {
        User owner = createActiveUser("slot-confirm-noslot-owner@example.com", "Password1!");
        User customer = createActiveUser("slot-confirm-noslot-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);

        mockMvc.perform(patch("/companies/{cid}/orders/{oid}/delivery-slot/confirm", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /companies/{companyId}/orders/{orderId}/delivery-slot/unavailable ─

    @Test
    void shouldTransitionToUnavailableWhenVendorMarksUnavailable() throws Exception {
        User owner = createActiveUser("slot-unavail-owner@example.com", "Password1!");
        User customer = createActiveUser("slot-unavail-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);
        order.setPreferredDeliveryDate(LocalDate.now().plusDays(4));
        order.setDeliverySlotStatus(DeliverySlotStatus.REQUESTED);
        orderRepository.save(order);

        mockMvc.perform(patch("/companies/{cid}/orders/{oid}/delivery-slot/unavailable", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Outside delivery zone\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliverySlotStatus").value("UNAVAILABLE"));
    }

    @Test
    void shouldReturn409WhenConfirmingAnAlreadyUnavailableSlot() throws Exception {
        User owner = createActiveUser("slot-confirm-conflict-owner@example.com", "Password1!");
        User customer = createActiveUser("slot-confirm-conflict-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);
        order.setPreferredDeliveryDate(LocalDate.now().plusDays(4));
        order.setDeliverySlotStatus(DeliverySlotStatus.UNAVAILABLE);
        orderRepository.save(order);

        mockMvc.perform(patch("/companies/{cid}/orders/{oid}/delivery-slot/confirm", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldBeIdempotentWhenMarkingAnAlreadyUnavailableSlot() throws Exception {
        User owner = createActiveUser("slot-unavail-idem-owner@example.com", "Password1!");
        User customer = createActiveUser("slot-unavail-idem-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);
        order.setPreferredDeliveryDate(LocalDate.now().plusDays(4));
        order.setDeliverySlotStatus(DeliverySlotStatus.UNAVAILABLE);
        orderRepository.save(order);

        mockMvc.perform(patch("/companies/{cid}/orders/{oid}/delivery-slot/unavailable", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"retry\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliverySlotStatus").value("UNAVAILABLE"));
    }

    // ── GET /companies/{companyId}/orders?deliveryDate= ───────────────────────

    @Test
    void shouldFilterFulfillmentQueueByDeliveryDate() throws Exception {
        User owner = createActiveUser("slot-filter-owner@example.com", "Password1!");
        User customer = createActiveUser("slot-filter-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);

        LocalDate target = LocalDate.now().plusDays(2);
        Order match = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);
        match.setPreferredDeliveryDate(target);
        match.setDeliverySlotStatus(DeliverySlotStatus.REQUESTED);
        orderRepository.save(match);

        Order other = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);
        other.setPreferredDeliveryDate(LocalDate.now().plusDays(6));
        other.setDeliverySlotStatus(DeliverySlotStatus.REQUESTED);
        orderRepository.save(other);

        mockMvc.perform(get("/companies/{cid}/orders", company.getId())
                        .param("deliveryDate", target.toString())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderId").value(match.getId().toString()))
                .andExpect(jsonPath("$.data[0].preferredDeliveryDate").value(target.toString()));
    }

    @Test
    void shouldLeaveOrderWithoutSlotUnaffected() throws Exception {
        User owner = createActiveUser("slot-none-owner@example.com", "Password1!");
        User customer = createActiveUser("slot-none-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createProduct(company);
        Order order = createOrder(customer, product, OrderStatus.PAID, FulfillmentMethod.DELIVERY);

        // Order has no slot — it still appears in the queue and packs normally.
        mockMvc.perform(get("/companies/{cid}/orders/{oid}", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliverySlotStatus").value(nullValue()));

        mockMvc.perform(post("/companies/{cid}/orders/{oid}/pack", company.getId(), order.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PACKED"));
    }
}
