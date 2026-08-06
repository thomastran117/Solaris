package backend.integration.inventory;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.models.enums.LocationType;
import backend.repositories.CompanyRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketplaceAvailabilityIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryLocationRepository locationRepository;
    @Autowired private LocationStockRepository locationStockRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Avail Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private Product createProduct(Company company, UUID marketplaceId) {
        Product p = new Product();
        p.setCompany(company);
        p.setMarketplaceId(marketplaceId);
        p.setName("Test Product");
        p.setPrice(new BigDecimal("29.99"));
        // Availability is a public storefront endpoint: it only resolves products that are actually
        // surfaced in the marketplace (ACTIVE + marketplaceListed). Entity defaults are DRAFT/false.
        p.setStatus(backend.models.enums.ProductStatus.ACTIVE);
        p.setMarketplaceListed(true);
        return productRepository.save(p);
    }

    private InventoryLocation createLocation(Company company, Double lat, Double lng) {
        InventoryLocation loc = new InventoryLocation();
        loc.setCompany(company);
        loc.setName("Main Warehouse");
        loc.setCode("WH-" + UUID.randomUUID().toString().substring(0, 6));
        loc.setActive(true);
        loc.setHandlingDays(1);
        loc.setType(LocationType.WAREHOUSE);
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        return locationRepository.save(loc);
    }

    private LocationStock createStock(InventoryLocation location, Product product, int qty) {
        LocationStock ls = new LocationStock();
        ls.setLocation(location);
        ls.setProduct(product);
        ls.setStock(qty);
        return locationStockRepository.save(ls);
    }

    private String availabilityUrl(UUID marketplaceId, UUID productId) {
        return "/marketplaces/" + marketplaceId + "/catalog/products/" + productId + "/availability";
    }

    // ── GET /marketplaces/{mpId}/catalog/products/{productId}/availability ────

    @Test
    void getAvailability_inStock_noCoords_returns200() throws Exception {
        User owner = createActiveUser("avail-instock@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID marketplaceId = UUID.randomUUID();
        Product product = createProduct(company, marketplaceId);
        InventoryLocation location = createLocation(company, null, null);
        createStock(location, product, 100);

        mockMvc.perform(get(availabilityUrl(marketplaceId, product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inStock").value(true))
                .andExpect(jsonPath("$.data.etaDaysMin").isNumber())
                .andExpect(jsonPath("$.data.etaDaysMax").isNumber());
    }

    @Test
    void getAvailability_outOfStock_returns200WithInStockFalse() throws Exception {
        User owner = createActiveUser("avail-oos@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID marketplaceId = UUID.randomUUID();
        Product product = createProduct(company, marketplaceId);
        // No LocationStock — nothing in stock

        mockMvc.perform(get(availabilityUrl(marketplaceId, product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inStock").value(false));
    }

    @Test
    void getAvailability_zeroStock_returns200WithInStockFalse() throws Exception {
        User owner = createActiveUser("avail-zero@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID marketplaceId = UUID.randomUUID();
        Product product = createProduct(company, marketplaceId);
        InventoryLocation location = createLocation(company, null, null);
        createStock(location, product, 0); // stock = 0 not picked up by query

        mockMvc.perform(get(availabilityUrl(marketplaceId, product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inStock").value(false));
    }

    @Test
    void getAvailability_productInWrongMarketplace_returns404() throws Exception {
        User owner = createActiveUser("avail-wrongmp@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID marketplaceId = UUID.randomUUID();
        UUID otherMarketplaceId = UUID.randomUUID();
        Product product = createProduct(company, marketplaceId);

        mockMvc.perform(get(availabilityUrl(otherMarketplaceId, product.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvailability_productNotFound_returns404() throws Exception {
        UUID marketplaceId = UUID.randomUUID();

        mockMvc.perform(get(availabilityUrl(marketplaceId, UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvailability_delistedProduct_returns404() throws Exception {
        // A product in the marketplace but not marketplaceListed (delisted/hidden) must not leak
        // availability data via direct URL.
        User owner = createActiveUser("avail-delisted@example.com", "Password1!");
        Company company = createCompany(owner);
        UUID marketplaceId = UUID.randomUUID();
        Product product = createProduct(company, marketplaceId);
        product.setMarketplaceListed(false);
        productRepository.save(product);
        InventoryLocation location = createLocation(company, null, null);
        createStock(location, product, 100);

        mockMvc.perform(get(availabilityUrl(marketplaceId, product.getId())))
                .andExpect(status().isNotFound());
    }

    // NOTE: tests providing buyer lat/lng are omitted. The distance-ordered path calls
    // findByProductOrderedByDistance, a native query using POW/SIN/RADIANS/ASIN which
    // H2 (MySQL-compat mode) cannot parse. The query works correctly in production MySQL.

    @Test
    void getAvailability_onlyLatProvided_returns400() throws Exception {
        mockMvc.perform(get(availabilityUrl(UUID.randomUUID(), UUID.randomUUID()))
                        .param("lat", "40.7128"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAvailability_onlyLngProvided_returns400() throws Exception {
        mockMvc.perform(get(availabilityUrl(UUID.randomUUID(), UUID.randomUUID()))
                        .param("lng", "-74.0060"))
                .andExpect(status().isBadRequest());
    }
}
