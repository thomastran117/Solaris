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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocationInventoryIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void cleanInventory() {
        try { jdbcTemplate.execute("DELETE FROM inventory_adjustments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM location_stocks"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM inventory_locations"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Test Co " + UUID.randomUUID().toString().substring(0, 8));
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
        p.setName("Test Product " + UUID.randomUUID().toString().substring(0, 8));
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("9.99"));
        p.setCurrency("USD");
        p.setStock(0);
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        return productRepository.save(p);
    }

    private String base(UUID companyId) {
        return "/companies/" + companyId + "/inventory/locations";
    }

    private String locationBody(String name, String code) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("code", code);
        return objectMapper.writeValueAsString(body);
    }

    private UUID createLocationViaApi(User owner, Company company, String name, String code) throws Exception {
        String response = mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody(name, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    // ── GET /companies/{companyId}/inventory/locations ────────────────────────

    @Test
    void getLocations_emptyForNewCompany_returns200() throws Exception {
        User owner = createActiveUser("locs-empty@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getLocations_afterCreate_returnsLocation() throws Exception {
        User owner = createActiveUser("locs-list@example.com", "Password1!");
        Company company = createCompany(owner);
        createLocationViaApi(owner, company, "Main Warehouse", "MAIN");

        mockMvc.perform(get(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Main Warehouse"))
                .andExpect(jsonPath("$.data[0].code").value("MAIN"))
                .andExpect(jsonPath("$.data[0].type").value("WAREHOUSE"));
    }

    @Test
    void getLocations_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("locs-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        User nonMember = createActiveUser("locs-nonmember@example.com", "Password1!");

        mockMvc.perform(get(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(nonMember))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLocations_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("locs-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId())))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/inventory/locations ───────────────────────

    @Test
    void createLocation_returns201WithFields() throws Exception {
        User owner = createActiveUser("create-loc@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("East Warehouse", "EAST-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("East Warehouse"))
                .andExpect(jsonPath("$.data.code").value("EAST-01"))
                .andExpect(jsonPath("$.data.type").value("WAREHOUSE"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_locations WHERE code = 'EAST-01'", Integer.class);
        assertEquals(1, rows, "Location should be persisted");
    }

    @Test
    void createLocation_codeIsUppercased() throws Exception {
        User owner = createActiveUser("create-upper@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("West Store", "west-store")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("WEST-STORE"));
    }

    @Test
    void createLocation_duplicateCode_returns409() throws Exception {
        User owner = createActiveUser("create-dupcode@example.com", "Password1!");
        Company company = createCompany(owner);
        createLocationViaApi(owner, company, "First", "DUP-CODE");

        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("Second", "DUP-CODE")))
                .andExpect(status().isConflict());
    }

    @Test
    void createLocation_missingName_returns400() throws Exception {
        User owner = createActiveUser("create-noname@example.com", "Password1!");
        Company company = createCompany(owner);

        String body = objectMapper.writeValueAsString(Map.of("code", "NO-NAME"));
        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLocation_missingCode_returns400() throws Exception {
        User owner = createActiveUser("create-nocode@example.com", "Password1!");
        Company company = createCompany(owner);

        String body = objectMapper.writeValueAsString(Map.of("name", "No Code"));
        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLocation_warehouseWithPickupReadyHours_returns400() throws Exception {
        User owner = createActiveUser("create-bad-pickup@example.com", "Password1!");
        Company company = createCompany(owner);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Bad Warehouse");
        body.put("code", "BAD-WH");
        body.put("type", "WAREHOUSE");
        body.put("pickupReadyHours", 4);
        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLocation_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("create-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        User nonMember = createActiveUser("create-nonmember@example.com", "Password1!");

        mockMvc.perform(post(base(company.getId()))
                        .header("Authorization", bearer(accessTokenFor(nonMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("Blocked", "BLOCKED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createLocation_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("create-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(post(base(company.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("Anon", "ANON")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/inventory/locations/{locationId} ───────────

    @Test
    void getLocation_returns200WithFields() throws Exception {
        User owner = createActiveUser("getloc-ok@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "North Hub", "NORTH-HUB");

        mockMvc.perform(get(base(company.getId()) + "/" + locationId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(locationId.toString()))
                .andExpect(jsonPath("$.data.name").value("North Hub"))
                .andExpect(jsonPath("$.data.code").value("NORTH-HUB"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void getLocation_unknownId_returns404() throws Exception {
        User owner = createActiveUser("getloc-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLocation_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("getloc-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        User nonMember = createActiveUser("getloc-nonmember@example.com", "Password1!");

        mockMvc.perform(get(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(nonMember))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLocation_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("getloc-unauth@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()) + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /companies/{companyId}/inventory/locations/{locationId} ─────────

    @Test
    void updateLocation_updatesName_returns200() throws Exception {
        User owner = createActiveUser("updloc-name@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Old Name", "OLD-NAME");

        String body = objectMapper.writeValueAsString(Map.of("name", "New Name"));
        mockMvc.perform(patch(base(company.getId()) + "/" + locationId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.code").value("OLD-NAME"));

        String name = jdbcTemplate.queryForObject("SELECT name FROM inventory_locations", String.class);
        assertEquals("New Name", name, "Location rename should be persisted");
    }

    @Test
    void updateLocation_duplicateCode_returns409() throws Exception {
        User owner = createActiveUser("updloc-dupcode@example.com", "Password1!");
        Company company = createCompany(owner);
        createLocationViaApi(owner, company, "Alpha", "ALPHA");
        UUID betaId = createLocationViaApi(owner, company, "Beta", "BETA");

        String body = objectMapper.writeValueAsString(Map.of("code", "ALPHA"));
        mockMvc.perform(patch(base(company.getId()) + "/" + betaId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void updateLocation_unknownId_returns404() throws Exception {
        User owner = createActiveUser("updloc-404@example.com", "Password1!");
        Company company = createCompany(owner);

        String body = objectMapper.writeValueAsString(Map.of("name", "New"));
        mockMvc.perform(patch(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateLocation_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("updloc-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        User nonMember = createActiveUser("updloc-nonmember@example.com", "Password1!");

        String body = objectMapper.writeValueAsString(Map.of("name", "Hijacked"));
        mockMvc.perform(patch(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(nonMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateLocation_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("updloc-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Secure Loc", "SEC-LOC");

        String body = objectMapper.writeValueAsString(Map.of("name", "No Auth"));
        mockMvc.perform(patch(base(company.getId()) + "/" + locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /companies/{companyId}/inventory/locations/{locationId} ────────

    @Test
    void deleteLocation_returns204() throws Exception {
        User owner = createActiveUser("delloc-ok@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Deletable", "DEL");

        mockMvc.perform(delete(base(company.getId()) + "/" + locationId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_locations", Integer.class);
        assertEquals(0, rows, "Deleted location should be removed from the database");
    }

    @Test
    void deleteLocation_deletedLocationIsGone_returns404() throws Exception {
        User owner = createActiveUser("delloc-gone@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Gone", "GONE");

        mockMvc.perform(delete(base(company.getId()) + "/" + locationId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(base(company.getId()) + "/" + locationId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteLocation_withStockRemaining_returns409() throws Exception {
        User owner = createActiveUser("delloc-stock@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID locationId = createLocationViaApi(owner, company, "Stocked", "STOCKED");

        // Set stock > 0 so delete is blocked
        String setBody = objectMapper.writeValueAsString(Map.of("stock", 10));
        mockMvc.perform(put(base(company.getId()) + "/" + locationId + "/stock/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete(base(company.getId()) + "/" + locationId)
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteLocation_unknownId_returns404() throws Exception {
        User owner = createActiveUser("delloc-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(delete(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteLocation_nonMemberUser_returns403() throws Exception {
        User owner = createActiveUser("delloc-owner403@example.com", "Password1!");
        Company company = createCompany(owner);
        User nonMember = createActiveUser("delloc-nonmember@example.com", "Password1!");

        mockMvc.perform(delete(base(company.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(nonMember))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteLocation_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("delloc-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Secure Del", "SEC-DEL");

        mockMvc.perform(delete(base(company.getId()) + "/" + locationId))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/inventory/locations/{locationId}/stock ─────

    @Test
    void getLocationStock_emptyForNewLocation_returns200() throws Exception {
        User owner = createActiveUser("getstk-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Empty Stock", "EMPTY-STK");

        mockMvc.perform(get(base(company.getId()) + "/" + locationId + "/stock")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getLocationStock_afterSetStock_returnsRecord() throws Exception {
        User owner = createActiveUser("getstk-after@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID locationId = createLocationViaApi(owner, company, "Stocked Hub", "STOCKED-HUB");

        String setBody = objectMapper.writeValueAsString(Map.of("stock", 25));
        mockMvc.perform(put(base(company.getId()) + "/" + locationId + "/stock/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setBody))
                .andExpect(status().isOk());

        mockMvc.perform(get(base(company.getId()) + "/" + locationId + "/stock")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data[0].stock").value(25))
                .andExpect(jsonPath("$.data[0].stockStatus").value("IN_STOCK"));
    }

    @Test
    void getLocationStock_unknownLocation_returns404() throws Exception {
        User owner = createActiveUser("getstk-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(base(company.getId()) + "/" + UUID.randomUUID() + "/stock")
                        .header("Authorization", bearer(accessTokenFor(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLocationStock_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("getstk-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Auth Stock", "AUTH-STK");

        mockMvc.perform(get(base(company.getId()) + "/" + locationId + "/stock"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /companies/{companyId}/inventory/locations/{locationId}/stock/{productId} ──

    @Test
    void setLocationStock_returnsStockRecord() throws Exception {
        User owner = createActiveUser("setstk-ok@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID locationId = createLocationViaApi(owner, company, "Set Stock Loc", "SET-STK");

        String body = objectMapper.writeValueAsString(Map.of("stock", 50));
        mockMvc.perform(put(base(company.getId()) + "/" + locationId + "/stock/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.locationId").value(locationId.toString()))
                .andExpect(jsonPath("$.data.stock").value(50))
                .andExpect(jsonPath("$.data.stockStatus").value("IN_STOCK"));

        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM location_stocks", Integer.class);
        assertEquals(50, stock, "Location stock should be persisted");
    }

    @Test
    void setLocationStock_zeroStock_statusIsOutOfStock() throws Exception {
        User owner = createActiveUser("setstk-zero@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID locationId = createLocationViaApi(owner, company, "Zero Stock", "ZERO-STK");

        String body = objectMapper.writeValueAsString(Map.of("stock", 0));
        mockMvc.perform(put(base(company.getId()) + "/" + locationId + "/stock/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockStatus").value("OUT_OF_STOCK"));
    }

    @Test
    void setLocationStock_unknownLocation_returns404() throws Exception {
        User owner = createActiveUser("setstk-404loc@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);

        String body = objectMapper.writeValueAsString(Map.of("stock", 10));
        mockMvc.perform(put(base(company.getId()) + "/" + UUID.randomUUID() + "/stock/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void setLocationStock_unknownProduct_returns404() throws Exception {
        User owner = createActiveUser("setstk-404prod@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Unknown Prod Stock", "UNK-PROD");

        String body = objectMapper.writeValueAsString(Map.of("stock", 10));
        mockMvc.perform(put(base(company.getId()) + "/" + locationId + "/stock/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void setLocationStock_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("setstk-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Auth Set", "AUTH-SET");

        String body = objectMapper.writeValueAsString(Map.of("stock", 10));
        mockMvc.perform(put(base(company.getId()) + "/" + locationId + "/stock/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/inventory/locations/{locationId}/stock/{productId}/adjust ──

    @Test
    void adjustLocationStock_positiveDelta_increasesStock() throws Exception {
        User owner = createActiveUser("adjstk-pos@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID locationId = createLocationViaApi(owner, company, "Adjust Loc", "ADJ-LOC");

        // Set initial stock
        mockMvc.perform(put(base(company.getId()) + "/" + locationId + "/stock/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stock", 10))))
                .andExpect(status().isOk());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delta", 5);
        body.put("reason", "RESTOCK");
        mockMvc.perform(post(base(company.getId()) + "/" + locationId + "/stock/" + product.getId() + "/adjust")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(15))
                .andExpect(jsonPath("$.data.stockStatus").value("IN_STOCK"));

        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM location_stocks", Integer.class);
        assertEquals(15, stock, "Adjusted location stock should be persisted");
    }

    @Test
    void adjustLocationStock_negativeDeltaBeyondStock_returns400() throws Exception {
        User owner = createActiveUser("adjstk-neg@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID locationId = createLocationViaApi(owner, company, "Neg Adjust", "NEG-ADJ");

        // Set stock to 5
        mockMvc.perform(put(base(company.getId()) + "/" + locationId + "/stock/" + product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stock", 5))))
                .andExpect(status().isOk());

        // Try to subtract 10 from 5 — would go negative
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delta", -10);
        body.put("reason", "DAMAGE");
        mockMvc.perform(post(base(company.getId()) + "/" + locationId + "/stock/" + product.getId() + "/adjust")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustLocationStock_noStockRecord_returns404() throws Exception {
        User owner = createActiveUser("adjstk-norecord@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company);
        UUID locationId = createLocationViaApi(owner, company, "No Record", "NO-REC");

        // Never called setLocationStock, so no LocationStock row exists
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delta", 5);
        body.put("reason", "RESTOCK");
        mockMvc.perform(post(base(company.getId()) + "/" + locationId + "/stock/" + product.getId() + "/adjust")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adjustLocationStock_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("adjstk-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID locationId = createLocationViaApi(owner, company, "Auth Adj", "AUTH-ADJ");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delta", 5);
        body.put("reason", "RESTOCK");
        mockMvc.perform(post(base(company.getId()) + "/" + locationId + "/stock/" + UUID.randomUUID() + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
