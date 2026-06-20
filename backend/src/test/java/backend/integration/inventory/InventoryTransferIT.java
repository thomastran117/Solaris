package backend.integration.inventory;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InventoryTransferIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void cleanInventory() {
        try { jdbcTemplate.execute("DELETE FROM inventory_transfers"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM inventory_adjustments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM location_stocks"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM inventory_locations"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Acceptance criteria ──────────────────────────────────────────────────

    @Test
    void createTransfer_quantityExceedsSourceStock_returns422() throws Exception {
        Fixture f = fixture("trf-422@example.com");
        setStock(f, f.from, 3);

        mockMvc.perform(post(transfers(f.companyId))
                        .header("Authorization", bearer(f.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(f.productId, f.from, f.to, 5)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createTransfer_sameLocation_returns400() throws Exception {
        Fixture f = fixture("trf-same@example.com");
        setStock(f, f.from, 10);

        mockMvc.perform(post(transfers(f.companyId))
                        .header("Authorization", bearer(f.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(f.productId, f.from, f.from, 5)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiveTransfer_movesStockAtomicallyAcrossLocations() throws Exception {
        Fixture f = fixture("trf-move@example.com");
        setStock(f, f.from, 20);

        UUID transferId = createTransfer(f, 5);
        dispatch(f, transferId);
        mockMvc.perform(post(transfers(f.companyId) + "/" + transferId + "/receive")
                        .header("Authorization", bearer(f.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"));

        assertEquals(15, stockAt(f, f.from));
        assertEquals(5, stockAt(f, f.to));
    }

    @Test
    void receiveTransfer_writesBothAdjustmentsVisibleInHistory() throws Exception {
        Fixture f = fixture("trf-hist@example.com");
        setStock(f, f.from, 20);

        UUID transferId = createTransfer(f, 4);
        dispatch(f, transferId);
        mockMvc.perform(post(transfers(f.companyId) + "/" + transferId + "/receive")
                        .header("Authorization", bearer(f.token)))
                .andExpect(status().isOk());

        // Inventory history at the product shows both legs, each referencing the transfer id.
        mockMvc.perform(get("/companies/" + f.companyId + "/inventory/" + f.productId + "/history")
                        .header("Authorization", bearer(f.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].reason", hasItems("TRANSFER_OUT", "TRANSFER_IN")))
                .andExpect(jsonPath("$.data[?(@.reason=='TRANSFER_OUT')].note",
                        everyItem(containsString(transferId.toString()))))
                .andExpect(jsonPath("$.data[?(@.reason=='TRANSFER_IN')].note",
                        everyItem(containsString(transferId.toString()))));
    }

    @Test
    void cancelTransfer_pending_leavesStockUnchanged() throws Exception {
        Fixture f = fixture("trf-cancel@example.com");
        setStock(f, f.from, 12);

        UUID transferId = createTransfer(f, 5);
        mockMvc.perform(post(transfers(f.companyId) + "/" + transferId + "/cancel")
                        .header("Authorization", bearer(f.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertEquals(12, stockAt(f, f.from));
    }

    @Test
    void cancelTransfer_inTransit_returns409() throws Exception {
        Fixture f = fixture("trf-cancel409@example.com");
        setStock(f, f.from, 12);

        UUID transferId = createTransfer(f, 5);
        dispatch(f, transferId);

        mockMvc.perform(post(transfers(f.companyId) + "/" + transferId + "/cancel")
                        .header("Authorization", bearer(f.token)))
                .andExpect(status().isConflict());
    }

    @Test
    void receiveTransfer_concurrentReceipts_onlyOneSucceeds() throws Exception {
        Fixture f = fixture("trf-concurrent@example.com");
        // Seed EXACTLY enough for one receipt: the loser must be rejected at the transfer's
        // optimistic-lock check (409), not surface a misleading 422 from the source stock leg.
        setStock(f, f.from, 6);

        UUID transferId = createTransfer(f, 6);
        dispatch(f, transferId);

        String url = transfers(f.companyId) + "/" + transferId + "/receive";
        String token = f.token;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> call = () -> {
            start.await();
            return mockMvc.perform(post(url).header("Authorization", bearer(token)))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> a = pool.submit(call);
        Future<Integer> b = pool.submit(call);
        start.countDown();

        List<Integer> statuses = List.of(a.get(), b.get());
        pool.shutdown();

        long ok = statuses.stream().filter(s -> s == 200).count();
        long conflict = statuses.stream().filter(s -> s == 409).count();
        assertEquals(1, ok, "exactly one receipt should succeed");
        assertEquals(1, conflict, "the losing concurrent receipt should return 409, not 422");

        // Stock moved exactly once — no double application.
        assertEquals(0, stockAt(f, f.from));
        assertEquals(6, stockAt(f, f.to));
    }

    @Test
    void createTransfer_nonMember_returns403() throws Exception {
        Fixture f = fixture("trf-owner403@example.com");
        setStock(f, f.from, 10);
        User nonMember = createActiveUser("trf-nonmember@example.com", "Password1!");

        mockMvc.perform(post(transfers(f.companyId))
                        .header("Authorization", bearer(accessTokenFor(nonMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(f.productId, f.from, f.to, 5)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTransfers_filtersByStatus() throws Exception {
        Fixture f = fixture("trf-list@example.com");
        setStock(f, f.from, 30);
        createTransfer(f, 2);
        UUID dispatched = createTransfer(f, 3);
        dispatch(f, dispatched);

        mockMvc.perform(get(transfers(f.companyId) + "?status=IN_TRANSIT")
                        .header("Authorization", bearer(f.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("IN_TRANSIT"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record Fixture(User owner, String token, UUID companyId, UUID productId, UUID from, UUID to) {}

    private Fixture fixture(String email) throws Exception {
        User owner = createActiveUser(email, "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID from = createLocation(owner, company.getId(), "Source WH", "SRC-" + suffix());
        UUID to = createLocation(owner, company.getId(), "Dest Store", "DST-" + suffix());
        return new Fixture(owner, accessTokenFor(owner), company.getId(), product.getId(), from, to);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Test Co " + suffix());
        c.setStatus(CompanyStatus.ACTIVE);
        Company saved = companyRepository.save(c);
        CompanyMembership m = new CompanyMembership();
        m.setCompany(saved);
        m.setUser(owner);
        m.setRole(CompanyRole.OWNER);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
        return saved;
    }

    private Product createProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Test Product " + suffix());
        p.setSku("SKU-" + suffix());
        p.setPrice(new BigDecimal("9.99"));
        p.setCurrency("USD");
        p.setStock(0);
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        return productRepository.save(p);
    }

    private UUID createLocation(User owner, UUID companyId, String name, String code) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("code", code);
        String response = mockMvc.perform(post("/companies/" + companyId + "/inventory/locations")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    private void setStock(Fixture f, UUID locationId, int qty) throws Exception {
        mockMvc.perform(put("/companies/" + f.companyId + "/inventory/locations/" + locationId + "/stock/" + f.productId)
                        .header("Authorization", bearer(f.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stock", qty))))
                .andExpect(status().isOk());
    }

    private int stockAt(Fixture f, UUID locationId) throws Exception {
        String response = mockMvc.perform(get("/companies/" + f.companyId + "/inventory/locations/" + locationId + "/stock")
                        .header("Authorization", bearer(f.token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(response).path("data");
        for (JsonNode node : items) {
            if (f.productId.toString().equals(node.path("productId").asText())) {
                return node.path("stock").asInt();
            }
        }
        return 0; // no record yet => zero stock
    }

    private UUID createTransfer(Fixture f, int qty) throws Exception {
        MvcResult result = mockMvc.perform(post(transfers(f.companyId))
                        .header("Authorization", bearer(f.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(f.productId, f.from, f.to, qty)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    private void dispatch(Fixture f, UUID transferId) throws Exception {
        mockMvc.perform(post(transfers(f.companyId) + "/" + transferId + "/dispatch")
                        .header("Authorization", bearer(f.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
    }

    private String transferBody(UUID productId, UUID from, UUID to, int qty) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId.toString());
        body.put("fromLocationId", from.toString());
        body.put("toLocationId", to.toString());
        body.put("quantity", qty);
        return objectMapper.writeValueAsString(body);
    }

    private String transfers(UUID companyId) {
        return "/companies/" + companyId + "/inventory/transfers";
    }
}
