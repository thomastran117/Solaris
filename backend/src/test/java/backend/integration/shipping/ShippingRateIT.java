package backend.integration.shipping;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.dtos.shipping.ShippingRate;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.PaymentService.PaymentIntentResult;
import backend.services.intf.shipping.ShippingRateService;

class ShippingRateIT extends AbstractIntegrationIT {

    @Autowired private OrderRepository orderRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryLocationRepository locationRepository;

    @MockitoBean private ShippingRateService shippingRateService;
    @MockitoBean private PaymentService paymentService;

    private static final List<ShippingRate> RATES = List.of(
            new ShippingRate("rate_1", "USPS", "Priority", "Priority", 2, 799, "USD"),
            new ShippingRate("rate_2", "UPS", "Ground", "Ground", 5, 599, "USD"));

    @BeforeEach
    void stubProviders() {
        when(shippingRateService.getRates(any())).thenReturn(RATES);
        when(paymentService.updatePaymentIntentAmount(any(), anyLong()))
                .thenAnswer(inv -> new PaymentIntentResult(
                        inv.getArgument(0), "secret", inv.getArgument(1), "usd", "requires_payment_method", null));
    }

    @AfterEach
    void cleanShipping() {
        try { jdbcTemplate.execute("DELETE FROM order_status_history"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM order_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM orders"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM location_stocks"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM inventory_locations"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── GET /orders/{id}/shipping-rates ───────────────────────────────────────

    @Test
    void getShippingRates_returnsRateOptions() throws Exception {
        User user = createActiveUser("ship-rates@example.com", "Password1!");
        Order order = reservedDeliveryOrder(user);

        mockMvc.perform(get("/orders/{id}/shipping-rates", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].carrier").value("USPS"))
                .andExpect(jsonPath("$.data[0].estimatedDays").value(2))
                .andExpect(jsonPath("$.data[1].totalCents").value(599));
    }

    @Test
    void getShippingRates_returns200WithEmptyListWhenProviderDegraded() throws Exception {
        when(shippingRateService.getRates(any())).thenReturn(List.of());
        User user = createActiveUser("ship-rates-empty@example.com", "Password1!");
        Order order = reservedDeliveryOrder(user);

        mockMvc.perform(get("/orders/{id}/shipping-rates", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void getShippingRates_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/orders/{id}/shipping-rates", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getShippingRates_returns404ForOtherUsersOrder() throws Exception {
        User owner = createActiveUser("ship-owner@example.com", "Password1!");
        User other = createActiveUser("ship-other@example.com", "Password1!");
        Order order = reservedDeliveryOrder(owner);

        mockMvc.perform(get("/orders/{id}/shipping-rates", order.getId())
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /orders/{id}/shipping-rate ──────────────────────────────────────

    @Test
    void confirmShippingRate_updatesTotalAndPaymentIntent() throws Exception {
        User user = createActiveUser("ship-confirm@example.com", "Password1!");
        Order order = reservedDeliveryOrder(user); // total 20.00

        mockMvc.perform(patch("/orders/{id}/shipping-rate", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("rateId", "rate_2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippingCostCents").value(599))
                .andExpect(jsonPath("$.data.shippingCarrier").value("UPS"))
                .andExpect(jsonPath("$.data.totalAmount").value(25.99));

        // Stripe PaymentIntent moved to the new total (2000 base + 599 shipping).
        verify(paymentService).updatePaymentIntentAmount(eq("pi_ship_test"), eq(2599L));

        // The chosen rate must be committed to the order, not just returned in the response.
        Order confirmed = orderRepository.findById(order.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(599L, confirmed.getShippingCostCents());
        org.junit.jupiter.api.Assertions.assertEquals("UPS", confirmed.getShippingCarrier());
    }

    @Test
    void confirmShippingRate_returns400ForUnknownRate() throws Exception {
        User user = createActiveUser("ship-confirm-bad@example.com", "Password1!");
        Order order = reservedDeliveryOrder(user);

        mockMvc.perform(patch("/orders/{id}/shipping-rate", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("rateId", "rate_missing"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmShippingRate_returns400ForPaidOrder() throws Exception {
        User user = createActiveUser("ship-confirm-paid@example.com", "Password1!");
        Order order = reservedDeliveryOrder(user);
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        mockMvc.perform(patch("/orders/{id}/shipping-rate", order.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("rateId", "rate_2"))))
                .andExpect(status().isBadRequest());
    }

    // ── seed helpers ──────────────────────────────────────────────────────────

    private Order reservedDeliveryOrder(User user) {
        Company company = new Company();
        company.setOwner(user);
        company.setName("Ship Co " + suffix());
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        InventoryLocation location = new InventoryLocation();
        location.setCompany(company);
        location.setName("Origin WH");
        location.setCode("ORIG-" + suffix());
        location.setAddress("1 Vendor St");
        location.setCity("Seattle");
        location.setStateProvince("WA");
        location.setPostalCode("98101");
        location.setCountry("US");
        location.setActive(true);
        location.setDisplayOrder(0);
        location = locationRepository.save(location);

        Product product = new Product();
        product.setCompany(company);
        product.setName("Desk " + suffix());
        product.setSku("SKU-" + suffix());
        product.setPrice(new BigDecimal("20.00"));
        product.setCurrency("USD");
        product.setStock(0);
        product.setStatus(ProductStatus.ACTIVE);
        product.setWeightGrams(800);
        product.setLengthCm(40);
        product.setWidthCm(30);
        product.setHeightCm(10);
        product = productRepository.save(product);

        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(new BigDecimal("20.00"));
        order.setCurrency("USD");
        order.setStatus(OrderStatus.RESERVED);
        order.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        order.setPaymentIntentId("pi_ship_test");
        order.setShipRecipientName("Jane Buyer");
        order.setShipStreet("5 Buyer Ave");
        order.setShipCity("Boston");
        order.setShipState("MA");
        order.setShipPostalCode("02108");
        order.setShipCountry("US");

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("20.00"));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setFulfillmentStatus(FulfillmentStatus.PENDING);
        item.setFulfillmentLocation(location);
        order.setItems(List.of(item));

        return orderRepository.save(order);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
