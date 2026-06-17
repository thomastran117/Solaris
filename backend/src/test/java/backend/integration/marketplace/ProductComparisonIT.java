package backend.integration.marketplace;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductAttribute;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers GET /marketplaces/{marketplaceId}/catalog/products/compare — the public, DB-backed
 * product comparison matrix. The endpoint loads products by id + marketplaceId, filters to
 * ACTIVE + marketplaceListed, builds an aligned attribute matrix, and caches under
 * {@code marketplace:compare:{marketplaceId}:{sortedIds}} for 2 minutes.
 */
class ProductComparisonIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM product_attributes"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner, String name) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName(name + " " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private Product buildProduct(Company company, String name, BigDecimal price, ProductStatus status,
                                 UUID marketplaceId, boolean marketplaceListed) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(price);
        p.setStatus(status);
        p.setMarketplaceId(marketplaceId);
        p.setMarketplaceListed(marketplaceListed);
        return p;
    }

    private Product createProduct(Company company, String name, BigDecimal price, ProductStatus status,
                                  UUID marketplaceId, boolean marketplaceListed) {
        return productRepository.save(buildProduct(company, name, price, status, marketplaceId, marketplaceListed));
    }

    /** Adds an attribute to a still-transient product; persisted in one save via cascade. */
    private void addAttribute(Product p, String name, String value, int order) {
        ProductAttribute a = new ProductAttribute();
        a.setProduct(p);
        a.setName(name);
        a.setValue(value);
        a.setDisplayOrder(order);
        p.getAttributes().add(a);
    }

    private String sortedIds(UUID... ids) {
        return Stream.of(ids).sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(":"));
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    void compare_returnsMatrixWithAlignedAttributeRows() throws Exception {
        User owner = createActiveUser("cmp-owner1@example.com", "Password1!");
        UUID marketplaceId = createCompany(owner, "Compare Marketplace").getId();
        Company vendor = createCompany(owner, "Compare Vendor");

        Product p1 = buildProduct(vendor, "Alpha", new BigDecimal("19.99"), ProductStatus.ACTIVE, marketplaceId, true);
        addAttribute(p1, "Material", "Steel", 0);
        addAttribute(p1, "Weight", "2kg", 1);
        p1 = productRepository.save(p1);
        Product p2 = buildProduct(vendor, "Beta", new BigDecimal("29.99"), ProductStatus.ACTIVE, marketplaceId, true);
        addAttribute(p2, "Material", "Aluminium", 0); // no Weight attribute
        p2 = productRepository.save(p2);

        mockMvc.perform(get("/marketplaces/" + marketplaceId + "/catalog/products/compare")
                        .param("ids", p1.getId() + "," + p2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products", hasSize(2)))
                .andExpect(jsonPath("$.data.products[0].productId").value(p1.getId().toString()))
                .andExpect(jsonPath("$.data.products[0].name").value("Alpha"))
                .andExpect(jsonPath("$.data.products[0].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.attributes", hasSize(2)))
                .andExpect(jsonPath("$.data.attributes[0].attributeName").value("Material"))
                .andExpect(jsonPath("$.data.attributes[0].valuesByProductId['" + p1.getId() + "']").value("Steel"))
                .andExpect(jsonPath("$.data.attributes[0].valuesByProductId['" + p2.getId() + "']").value("Aluminium"))
                .andExpect(jsonPath("$.data.attributes[1].attributeName").value("Weight"))
                .andExpect(jsonPath("$.data.attributes[1].valuesByProductId['" + p2.getId() + "']").value(nullValue()));
    }

    @Test
    void compare_oneId_returns400() throws Exception {
        User owner = createActiveUser("cmp-owner2@example.com", "Password1!");
        UUID marketplaceId = createCompany(owner, "Marketplace").getId();

        mockMvc.perform(get("/marketplaces/" + marketplaceId + "/catalog/products/compare")
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void compare_fiveIds_returns400() throws Exception {
        User owner = createActiveUser("cmp-owner3@example.com", "Password1!");
        UUID marketplaceId = createCompany(owner, "Marketplace").getId();

        String ids = Stream.generate(() -> UUID.randomUUID().toString()).limit(5)
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(get("/marketplaces/" + marketplaceId + "/catalog/products/compare")
                        .param("ids", ids))
                .andExpect(status().isBadRequest());
    }

    @Test
    void compare_productNotInMarketplace_returns404() throws Exception {
        User owner = createActiveUser("cmp-owner4@example.com", "Password1!");
        UUID marketplaceId = createCompany(owner, "Marketplace").getId();
        Company vendor = createCompany(owner, "Vendor");
        Product p1 = createProduct(vendor, "Alpha", BigDecimal.TEN, ProductStatus.ACTIVE, marketplaceId, true);

        // Second id belongs to no marketplace product → 404.
        mockMvc.perform(get("/marketplaces/" + marketplaceId + "/catalog/products/compare")
                        .param("ids", p1.getId() + "," + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void compare_nonListedProduct_returns404() throws Exception {
        User owner = createActiveUser("cmp-owner5@example.com", "Password1!");
        UUID marketplaceId = createCompany(owner, "Marketplace").getId();
        Company vendor = createCompany(owner, "Vendor");
        Product listed = createProduct(vendor, "Listed", BigDecimal.TEN, ProductStatus.ACTIVE, marketplaceId, true);
        Product unlisted = createProduct(vendor, "Unlisted", BigDecimal.TEN, ProductStatus.ACTIVE, marketplaceId, false);

        mockMvc.perform(get("/marketplaces/" + marketplaceId + "/catalog/products/compare")
                        .param("ids", listed.getId() + "," + unlisted.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void compare_secondIdenticalRequest_populatesRedisCache() throws Exception {
        User owner = createActiveUser("cmp-owner6@example.com", "Password1!");
        UUID marketplaceId = createCompany(owner, "Marketplace").getId();
        Company vendor = createCompany(owner, "Vendor");
        Product p1 = createProduct(vendor, "Alpha", BigDecimal.TEN, ProductStatus.ACTIVE, marketplaceId, true);
        Product p2 = createProduct(vendor, "Beta", BigDecimal.TEN, ProductStatus.ACTIVE, marketplaceId, true);

        String url = "/marketplaces/" + marketplaceId + "/catalog/products/compare";
        mockMvc.perform(get(url).param("ids", p1.getId() + "," + p2.getId()))
                .andExpect(status().isOk());

        String cacheKey = "marketplace:compare:" + marketplaceId + ":" + sortedIds(p1.getId(), p2.getId());
        assertTrue(cacheService.exists(cacheKey), "comparison result should be cached in Redis");

        // A second identical request still succeeds (served from cache).
        mockMvc.perform(get(url).param("ids", p2.getId() + "," + p1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products", hasSize(2)));
    }
}
