package backend.integration.orders;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.*;
import backend.models.enums.*;
import backend.repositories.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubOrderIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private MarketplaceVendorRepository marketplaceVendorRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private SubOrderRepository subOrderRepository;
    @Autowired private CommissionRecordRepository commissionRecordRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Company " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private MarketplaceVendor createVendor(Company marketplace, Company vendorCompany) {
        MarketplaceVendor mv = new MarketplaceVendor();
        mv.setMarketplace(marketplace);
        mv.setVendorCompany(vendorCompany);
        mv.setStatus(VendorStatus.APPROVED);
        return marketplaceVendorRepository.save(mv);
    }

    private Order createOrder(User customer) {
        Order order = new Order();
        order.setUser(customer);
        order.setTotalAmount(BigDecimal.valueOf(100.00));
        order.setStatus(OrderStatus.PAID);
        return orderRepository.save(order);
    }

    private SubOrder createSubOrder(Order order, MarketplaceVendor vendor, SubOrderStatus status) {
        SubOrder so = new SubOrder();
        so.setOrder(order);
        so.setMarketplaceVendor(vendor);
        so.setMarketplaceId(vendor.getMarketplace().getId());
        so.setSubtotal(BigDecimal.valueOf(100.00));
        so.setTotalAmount(BigDecimal.valueOf(100.00));
        so.setStatus(status);
        return subOrderRepository.save(so);
    }

    private String base(UUID vendorId) {
        return "/vendors/" + vendorId + "/sub-orders";
    }

    // ── GET /vendors/{vendorId}/sub-orders ────────────────────────────────────

    @Test
    void list_emptyWhenNoSubOrders_returns200() throws Exception {
        User owner = createActiveUser("so-list-empty@example.com", "Password1!");
        User mpOwner = createActiveUser("so-list-mp@example.com", "Password1!");
        Company vendorCo = createCompany(owner);
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), vendorCo);

        mockMvc.perform(get(base(vendor.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void list_returnsSubOrdersForVendor() throws Exception {
        User owner = createActiveUser("so-list-ok@example.com", "Password1!");
        User customer = createActiveUser("so-list-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-list-mp2@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        createSubOrder(createOrder(customer), vendor, SubOrderStatus.PENDING);

        mockMvc.perform(get(base(vendor.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void list_filteredByStatus_returnsMatchingOnly() throws Exception {
        User owner = createActiveUser("so-list-filt@example.com", "Password1!");
        User customer = createActiveUser("so-list-filt-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-list-filt-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        Order order = createOrder(customer);
        createSubOrder(order, vendor, SubOrderStatus.PENDING);
        createSubOrder(order, vendor, SubOrderStatus.PACKED);

        mockMvc.perform(get(base(vendor.getId())).param("status", "PENDING")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void list_wrongOwner_returns403() throws Exception {
        User owner = createActiveUser("so-list-403-owner@example.com", "Password1!");
        User other = createActiveUser("so-list-403-other@example.com", "Password1!");
        User mpOwner = createActiveUser("so-list-403-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));

        mockMvc.perform(get(base(vendor.getId()))
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/vendors/" + UUID.randomUUID() + "/sub-orders"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /vendors/{vendorId}/sub-orders/{subOrderId} ───────────────────────

    @Test
    void get_returns200WithFields() throws Exception {
        User owner = createActiveUser("so-get@example.com", "Password1!");
        User customer = createActiveUser("so-get-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-get-mp@example.com", "Password1!");
        Company vendorCo = createCompany(owner);
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), vendorCo);
        Order order = createOrder(customer);
        SubOrder so = createSubOrder(order, vendor, SubOrderStatus.PENDING);

        mockMvc.perform(get(base(vendor.getId()) + "/" + so.getId())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(so.getId().toString()))
                .andExpect(jsonPath("$.data.orderId").value(order.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.vendorCompanyName").value(vendorCo.getName()))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void get_unknownSubOrder_returns404() throws Exception {
        User owner = createActiveUser("so-get-404@example.com", "Password1!");
        User mpOwner = createActiveUser("so-get-404-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));

        mockMvc.perform(get(base(vendor.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_subOrderBelongsToOtherVendor_returns404() throws Exception {
        User ownerA = createActiveUser("so-get-xv-a@example.com", "Password1!");
        User ownerB = createActiveUser("so-get-xv-b@example.com", "Password1!");
        User customer = createActiveUser("so-get-xv-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-get-xv-mp@example.com", "Password1!");
        Company marketplace = createCompany(mpOwner);
        MarketplaceVendor vendorA = createVendor(marketplace, createCompany(ownerA));
        MarketplaceVendor vendorB = createVendor(marketplace, createCompany(ownerB));
        SubOrder so = createSubOrder(createOrder(customer), vendorA, SubOrderStatus.PENDING);

        mockMvc.perform(get(base(vendorB.getId()) + "/" + so.getId())
                        .header("Authorization", bearer(accessTokenFor(ownerB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/vendors/" + UUID.randomUUID() + "/sub-orders/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{subOrderId}/pack ───────────────────────────────────────────────

    @Test
    void pack_returns200_statusBecomesPackedWithTimestamp() throws Exception {
        User owner = createActiveUser("so-pack-ok@example.com", "Password1!");
        User customer = createActiveUser("so-pack-ok-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-pack-ok-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PENDING);

        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/pack")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PACKED"))
                .andExpect(jsonPath("$.data.packedAt").isNotEmpty());

        assertEquals(SubOrderStatus.PACKED,
                subOrderRepository.findById(so.getId()).orElseThrow().getStatus());
    }

    @Test
    void pack_nonPendingStatus_returns400() throws Exception {
        User owner = createActiveUser("so-pack-400@example.com", "Password1!");
        User customer = createActiveUser("so-pack-400-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-pack-400-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PACKED);

        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/pack")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pack_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/vendors/" + UUID.randomUUID() + "/sub-orders/" + UUID.randomUUID() + "/pack"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{subOrderId}/ship ───────────────────────────────────────────────

    @Test
    void ship_returns200_statusBecomesShippedWithTrackingInfo() throws Exception {
        User owner = createActiveUser("so-ship-ok@example.com", "Password1!");
        User customer = createActiveUser("so-ship-ok-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-ship-ok-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PACKED);

        String body = objectMapper.writeValueAsString(Map.of(
                "trackingNumber", "TRACK123",
                "carrier", "FedEx",
                "fulfillmentNote", "Handle with care"));

        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/ship")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRACK123"))
                .andExpect(jsonPath("$.data.carrier").value("FedEx"))
                .andExpect(jsonPath("$.data.fulfillmentNote").value("Handle with care"))
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty());

        SubOrder shipped = subOrderRepository.findById(so.getId()).orElseThrow();
        assertEquals(SubOrderStatus.SHIPPED, shipped.getStatus());
        assertEquals("TRACK123", shipped.getTrackingNumber());
    }

    @Test
    void ship_nonPackedStatus_returns400() throws Exception {
        User owner = createActiveUser("so-ship-400@example.com", "Password1!");
        User customer = createActiveUser("so-ship-400-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-ship-400-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PENDING);

        String body = objectMapper.writeValueAsString(Map.of("trackingNumber", "TRACK123", "carrier", "FedEx"));
        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/ship")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ship_missingTrackingNumber_returns400() throws Exception {
        User owner = createActiveUser("so-ship-notrack@example.com", "Password1!");
        User customer = createActiveUser("so-ship-notrack-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-ship-notrack-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PACKED);

        String body = objectMapper.writeValueAsString(Map.of("carrier", "FedEx"));
        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/ship")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ship_missingCarrier_returns400() throws Exception {
        User owner = createActiveUser("so-ship-nocarrier@example.com", "Password1!");
        User customer = createActiveUser("so-ship-nocarrier-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-ship-nocarrier-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PACKED);

        String body = objectMapper.writeValueAsString(Map.of("trackingNumber", "TRACK123"));
        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/ship")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ship_unauthenticated_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("trackingNumber", "TRACK", "carrier", "FedEx"));
        mockMvc.perform(post("/vendors/" + UUID.randomUUID() + "/sub-orders/" + UUID.randomUUID() + "/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{subOrderId}/deliver ────────────────────────────────────────────

    @Test
    void deliver_returns200_statusBecomesDelivered() throws Exception {
        User owner = createActiveUser("so-deliver-ok@example.com", "Password1!");
        User customer = createActiveUser("so-deliver-ok-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-deliver-ok-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.SHIPPED);

        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/deliver")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());

        assertEquals(SubOrderStatus.DELIVERED,
                subOrderRepository.findById(so.getId()).orElseThrow().getStatus());
    }

    @Test
    void deliver_nonShippedStatus_returns400() throws Exception {
        User owner = createActiveUser("so-deliver-400@example.com", "Password1!");
        User customer = createActiveUser("so-deliver-400-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-deliver-400-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PACKED);

        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/deliver")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deliver_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/vendors/" + UUID.randomUUID() + "/sub-orders/" + UUID.randomUUID() + "/deliver"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{subOrderId}/cancel ─────────────────────────────────────────────

    @Test
    void cancel_fromPending_returns200() throws Exception {
        User owner = createActiveUser("so-cancel-pending@example.com", "Password1!");
        User customer = createActiveUser("so-cancel-pending-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-cancel-pending-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PENDING);

        String body = objectMapper.writeValueAsString(Map.of("reason", "Customer requested cancellation"));
        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/cancel")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("Customer requested cancellation"))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty());

        assertEquals(SubOrderStatus.CANCELLED,
                subOrderRepository.findById(so.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancel_fromPacked_returns200() throws Exception {
        User owner = createActiveUser("so-cancel-packed@example.com", "Password1!");
        User customer = createActiveUser("so-cancel-packed-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-cancel-packed-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PACKED);

        String body = objectMapper.writeValueAsString(Map.of("reason", "Stock issue"));
        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/cancel")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancel_shippedSubOrder_returns400() throws Exception {
        User owner = createActiveUser("so-cancel-shipped@example.com", "Password1!");
        User customer = createActiveUser("so-cancel-shipped-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-cancel-shipped-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.SHIPPED);

        String body = objectMapper.writeValueAsString(Map.of("reason", "Too late to cancel"));
        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/cancel")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_deliveredSubOrder_returns400() throws Exception {
        User owner = createActiveUser("so-cancel-delivered@example.com", "Password1!");
        User customer = createActiveUser("so-cancel-delivered-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-cancel-delivered-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.DELIVERED);

        String body = objectMapper.writeValueAsString(Map.of("reason", "Already delivered"));
        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/cancel")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_missingReason_returns400() throws Exception {
        User owner = createActiveUser("so-cancel-noreason@example.com", "Password1!");
        User customer = createActiveUser("so-cancel-noreason-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-cancel-noreason-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PENDING);

        mockMvc.perform(post(base(vendor.getId()) + "/" + so.getId() + "/cancel")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_unauthenticated_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("reason", "test"));
        mockMvc.perform(post("/vendors/" + UUID.randomUUID() + "/sub-orders/" + UUID.randomUUID() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{subOrderId}/commission ──────────────────────────────────────────

    @Test
    void commission_returns200WithFields() throws Exception {
        User owner = createActiveUser("so-comm-ok@example.com", "Password1!");
        User customer = createActiveUser("so-comm-ok-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-comm-ok-mp@example.com", "Password1!");
        Company marketplace = createCompany(mpOwner);
        MarketplaceVendor vendor = createVendor(marketplace, createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.DELIVERED);

        CommissionRecord record = new CommissionRecord();
        record.setSubOrder(so);
        record.setVendorId(vendor.getId());
        record.setMarketplaceId(marketplace.getId());
        record.setCommissionRate(new BigDecimal("0.1500"));
        record.setGrossAmount(new BigDecimal("100.00"));
        record.setCommissionAmount(new BigDecimal("15.00"));
        record.setNetVendorAmount(new BigDecimal("85.00"));
        record.setCurrency("USD");
        commissionRecordRepository.save(record);

        mockMvc.perform(get(base(vendor.getId()) + "/" + so.getId() + "/commission")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subOrderId").value(so.getId().toString()))
                .andExpect(jsonPath("$.data.commissionRate").value(0.15))
                .andExpect(jsonPath("$.data.grossAmount").value(100.0))
                .andExpect(jsonPath("$.data.commissionAmount").value(15.0))
                .andExpect(jsonPath("$.data.netVendorAmount").value(85.0))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void commission_noRecord_returns404() throws Exception {
        User owner = createActiveUser("so-comm-404@example.com", "Password1!");
        User customer = createActiveUser("so-comm-404-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-comm-404-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.DELIVERED);

        mockMvc.perform(get(base(vendor.getId()) + "/" + so.getId() + "/commission")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void commission_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/vendors/" + UUID.randomUUID() + "/sub-orders/" + UUID.randomUUID() + "/commission"))
                .andExpect(status().isUnauthorized());
    }

    // ── Full lifecycle: PENDING → PACKED → SHIPPED → DELIVERED ───────────────

    @Test
    void fullLifecycle_pendingToDelivered() throws Exception {
        User owner = createActiveUser("so-lifecycle@example.com", "Password1!");
        User customer = createActiveUser("so-lifecycle-cust@example.com", "Password1!");
        User mpOwner = createActiveUser("so-lifecycle-mp@example.com", "Password1!");
        MarketplaceVendor vendor = createVendor(createCompany(mpOwner), createCompany(owner));
        SubOrder so = createSubOrder(createOrder(customer), vendor, SubOrderStatus.PENDING);

        String token = bearer(accessTokenFor(owner));
        String path = base(vendor.getId()) + "/" + so.getId();

        mockMvc.perform(post(path + "/pack").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PACKED"));

        String shipBody = objectMapper.writeValueAsString(Map.of("trackingNumber", "XYZ999", "carrier", "UPS"));
        mockMvc.perform(post(path + "/ship")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));

        mockMvc.perform(post(path + "/deliver").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());
    }
}
