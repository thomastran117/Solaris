package backend.integration.marketplace;

import backend.documents.ProductDocument;
import backend.integration.fullinfra.AbstractSearchKafkaIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.VendorStatus;
import backend.models.enums.VendorTier;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.ProductRepository;
import backend.repositories.search.ProductSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers MarketplaceCatalogController:
 *  - POST /marketplaces/{marketplaceId}/catalog/products/{productId}/view  — always 204
 *    (the published UserActivityEvent always carries marketplaceId=null per the
 *    controller, so ActivityEventPublisherImpl short-circuits before touching Kafka)
 *  - GET  /marketplaces/{marketplaceId}/catalog/feed                       — @RequireAuth;
 *    with no purchase/wishlist history, falls back to findFeaturedFeed
 *    (status=ACTIVE, marketplaceListed=true, featured=true)
 *  - GET  /marketplaces/{marketplaceId}/catalog/products                   — 404 if no
 *    MarketplaceProfile exists for the company; otherwise queries live Elasticsearch
 *    (this class runs on real ES+Kafka via {@link AbstractSearchKafkaIT}), so the search
 *    happy-path indexes a product and asserts it comes back through the real query path
 *  - GET  /marketplaces/{marketplaceId}/catalog/products/{productId}       — DB-backed,
 *    matches on Product.marketplaceId
 *  - GET  /marketplaces/{marketplaceId}/catalog/vendors/{vendorId}/storefront — DB-backed,
 *    matches MarketplaceVendor.id + MarketplaceVendor.marketplace.id
 */
class MarketplaceCatalogControllerIT extends AbstractSearchKafkaIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private MarketplaceProfileRepository marketplaceProfileRepository;
    @Autowired private MarketplaceVendorRepository marketplaceVendorRepository;
    @Autowired private ProductSearchRepository productSearchRepository;

    @AfterEach
    void clean() {
        try { productSearchRepository.deleteAll(); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM marketplace_vendors"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM marketplace_profiles"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
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

    private MarketplaceProfile createMarketplaceProfile(Company company) {
        MarketplaceProfile mp = new MarketplaceProfile();
        mp.setCompany(company);
        mp.setSlug("mp-" + UUID.randomUUID().toString().substring(0, 8));
        return marketplaceProfileRepository.save(mp);
    }

    private MarketplaceVendor createVendor(Company marketplace, Company vendorCompany, VendorTier tier, VendorStatus status) {
        MarketplaceVendor mv = new MarketplaceVendor();
        mv.setMarketplace(marketplace);
        mv.setVendorCompany(vendorCompany);
        mv.setTier(tier);
        mv.setStatus(status);
        return marketplaceVendorRepository.save(mv);
    }

    private Product createProduct(Company company, String name, BigDecimal price, ProductStatus status,
                                   UUID marketplaceId, boolean marketplaceListed, boolean featured) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(price);
        p.setStatus(status);
        p.setMarketplaceId(marketplaceId);
        p.setMarketplaceListed(marketplaceListed);
        p.setFeatured(featured);
        return productRepository.save(p);
    }

    private void addMember(User user, Company company, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    /** Polls up to {@code timeout} for the async indexing pipeline to reach the expected state. */
    private <T> T await(Duration timeout, Callable<T> check, java.util.function.Predicate<T> done) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        T last = null;
        while (System.currentTimeMillis() < deadline) {
            last = check.call();
            if (done.test(last)) return last;
            Thread.sleep(500);
        }
        return last;
    }

    // ── POST /marketplaces/{marketplaceId}/catalog/products/{productId}/view ──

    @Test
    void trackView_noBody_returns204() throws Exception {
        mockMvc.perform(post("/marketplaces/" + UUID.randomUUID()
                        + "/catalog/products/" + UUID.randomUUID() + "/view"))
                .andExpect(status().isNoContent());
    }

    @Test
    void trackView_withDntHeader_returns204() throws Exception {
        mockMvc.perform(post("/marketplaces/" + UUID.randomUUID()
                        + "/catalog/products/" + UUID.randomUUID() + "/view")
                        .header("DNT", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void trackView_withSessionIdBody_returns204() throws Exception {
        mockMvc.perform(post("/marketplaces/" + UUID.randomUUID()
                        + "/catalog/products/" + UUID.randomUUID() + "/view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"sess-123\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void trackView_authenticatedUser_returns204() throws Exception {
        User user = createActiveUser("mkt-view-user@example.com", "Password1!");

        mockMvc.perform(post("/marketplaces/" + UUID.randomUUID()
                        + "/catalog/products/" + UUID.randomUUID() + "/view")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());
    }

    // ── GET /marketplaces/{marketplaceId}/catalog/feed ─────────────────────────

    @Test
    void getFeed_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/feed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFeed_noPurchaseHistory_returnsFeaturedListedActiveProducts() throws Exception {
        User owner = createActiveUser("mkt-feed-owner1@example.com", "Password1!");
        User shopper = createActiveUser("mkt-feed-shopper1@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Feed Marketplace");
        Company vendor = createCompany(owner, "Feed Vendor");
        Product featured = createProduct(vendor, "Featured Widget", new BigDecimal("19.99"),
                ProductStatus.ACTIVE, marketplace.getId(), true, true);
        createProduct(vendor, "Non-featured Widget", new BigDecimal("9.99"),
                ProductStatus.ACTIVE, marketplace.getId(), true, false);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/feed")
                        .header("Authorization", bearer(accessTokenFor(shopper))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(featured.getId().toString()))
                .andExpect(jsonPath("$.data[0].featured").value(true))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void getFeed_excludesUnlistedAndInactiveProducts() throws Exception {
        User owner = createActiveUser("mkt-feed-owner2@example.com", "Password1!");
        User shopper = createActiveUser("mkt-feed-shopper2@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Feed Marketplace 2");
        Company vendor = createCompany(owner, "Feed Vendor 2");
        // featured but not listed on the marketplace
        createProduct(vendor, "Unlisted Featured", new BigDecimal("19.99"),
                ProductStatus.ACTIVE, marketplace.getId(), false, true);
        // featured and listed, but not yet ACTIVE
        createProduct(vendor, "Draft Featured", new BigDecimal("19.99"),
                ProductStatus.DRAFT, marketplace.getId(), true, true);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/feed")
                        .header("Authorization", bearer(accessTokenFor(shopper))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getFeed_pagination_respectsSizeParam() throws Exception {
        User owner = createActiveUser("mkt-feed-owner3@example.com", "Password1!");
        User shopper = createActiveUser("mkt-feed-shopper3@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Feed Marketplace 3");
        Company vendor = createCompany(owner, "Feed Vendor 3");
        createProduct(vendor, "Item 1", BigDecimal.ONE, ProductStatus.ACTIVE, marketplace.getId(), true, true);
        createProduct(vendor, "Item 2", BigDecimal.ONE, ProductStatus.ACTIVE, marketplace.getId(), true, true);
        createProduct(vendor, "Item 3", BigDecimal.ONE, ProductStatus.ACTIVE, marketplace.getId(), true, true);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/feed")
                        .header("Authorization", bearer(accessTokenFor(shopper)))
                        .param("size", "2")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }

    @Test
    void getFeed_sizeTooLarge_returns400() throws Exception {
        User shopper = createActiveUser("mkt-feed-shopper4@example.com", "Password1!");

        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/feed")
                        .header("Authorization", bearer(accessTokenFor(shopper)))
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeed_negativePage_returns400() throws Exception {
        User shopper = createActiveUser("mkt-feed-shopper5@example.com", "Password1!");

        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/feed")
                        .header("Authorization", bearer(accessTokenFor(shopper)))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /marketplaces/{marketplaceId}/catalog/products ─────────────────────

    @Test
    void searchCatalog_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchCatalog_existingMarketplace_returnsIndexedProductFromElasticsearch() throws Exception {
        User owner = createActiveUser("mkt-search-owner1@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Search Marketplace");
        createMarketplaceProfile(marketplace);
        Company vendor = createCompany(owner, "Search Vendor");
        addMember(owner, vendor, CompanyRole.OWNER);
        createVendor(marketplace, vendor, VendorTier.STANDARD, VendorStatus.APPROVED);
        // marketplaceId on the product is what the catalog ES query filters on.
        Product product = createProduct(vendor, "Ergonomic Laptop Stand", new BigDecimal("49.99"),
                ProductStatus.ACTIVE, marketplace.getId(), true, false);

        // Drive the real bulk indexing path into the live Elasticsearch cluster.
        mockMvc.perform(post("/companies/" + vendor.getId() + "/products/reindex")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isAccepted());

        // Wait for the async worker to write the document, then force a refresh so the search
        // query (as opposed to a real-time get-by-id) can see it and we don't cache an empty page.
        Optional<ProductDocument> indexed = await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()), Optional::isPresent);
        assertTrue(indexed.isPresent(), "Product should have been indexed into Elasticsearch");
        refreshSearchIndices();

        // The public catalog search must now return the indexed product — proving the real ES
        // query path (marketplace filter + product_search analyzer + fuzzy match) works, where the
        // mocked-ES suite could only ever assert a 503.
        MvcResult searchResult = mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/products")
                        .param("q", "laptop"))
                .andReturn();
        if (searchResult.getResponse().getStatus() != 200) {
            String mapping = esGet("/products/_mapping/field/price,category,brand,marketplaceId,status");
            String rawAgg = esPost("/products/_search",
                    "{\"size\":0,\"aggs\":{\"pr\":{\"range\":{\"field\":\"price\",\"ranges\":[{\"to\":25.0}]}}}}");
            org.junit.jupiter.api.Assertions.fail("CATALOG 503 DIAG >>> status="
                    + searchResult.getResponse().getStatus()
                    + " | MAPPING=" + mapping + " | RAWAGG=" + rawAgg);
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                searchResult.getResponse().getContentAsString().contains(product.getId().toString()),
                "Catalog search should return the indexed product");
    }

    @Test
    void searchCatalog_invalidSortField_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/products")
                        .param("sort", "created_at"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_invalidDirection_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/products")
                        .param("direction", "sideways"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/products")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/products")
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_queryTooLong_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/products")
                        .param("q", "a".repeat(201)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_pageTooLarge_returns400() throws Exception {
        User owner = createActiveUser("mkt-search-owner2@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Search Marketplace 2");
        createMarketplaceProfile(marketplace);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/products")
                        .param("page", "10001"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /marketplaces/{marketplaceId}/catalog/products/{productId} ─────────

    @Test
    void getProduct_notFoundInMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID()
                        + "/catalog/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProduct_found_returnsProductDetails() throws Exception {
        User owner = createActiveUser("mkt-product-owner1@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Product Marketplace");
        Company vendor = createCompany(owner, "Product Vendor");
        Product product = createProduct(vendor, "Marketplace Widget", new BigDecimal("29.99"),
                ProductStatus.ACTIVE, marketplace.getId(), true, false);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.companyId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.marketplaceId").value(marketplace.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Marketplace Widget"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.vendorId").value(nullValue()));
    }

    @Test
    void getProduct_wrongMarketplace_returns404() throws Exception {
        User owner = createActiveUser("mkt-product-owner2@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Product Marketplace 2");
        Company otherMarketplace = createCompany(owner, "Other Marketplace");
        Company vendor = createCompany(owner, "Product Vendor 2");
        Product product = createProduct(vendor, "Widget", BigDecimal.TEN,
                ProductStatus.ACTIVE, marketplace.getId(), true, false);

        mockMvc.perform(get("/marketplaces/" + otherMarketplace.getId() + "/catalog/products/" + product.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProduct_withVendor_includesVendorInfo() throws Exception {
        User owner = createActiveUser("mkt-product-owner3@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Product Marketplace 3");
        Company vendorCompany = createCompany(owner, "Vendor Company 3");
        MarketplaceVendor vendor = createVendor(marketplace, vendorCompany, VendorTier.PREMIUM, VendorStatus.APPROVED);
        Product product = createProduct(vendorCompany, "Vendored Widget", new BigDecimal("15.00"),
                ProductStatus.ACTIVE, marketplace.getId(), true, false);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.vendorName").value(vendorCompany.getName()))
                .andExpect(jsonPath("$.data.vendorTier").value("PREMIUM"));
    }

    // ── GET /marketplaces/{marketplaceId}/catalog/vendors/{vendorId}/storefront ─

    @Test
    void getVendorStorefront_notFound_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID()
                        + "/catalog/vendors/" + UUID.randomUUID() + "/storefront"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVendorStorefront_found_returnsStorefrontWithFeaturedProducts() throws Exception {
        User owner = createActiveUser("mkt-storefront-owner1@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Storefront Marketplace");
        Company vendorCompany = createCompany(owner, "Storefront Vendor");
        MarketplaceVendor vendor = createVendor(marketplace, vendorCompany, VendorTier.STANDARD, VendorStatus.APPROVED);
        Product featured = createProduct(vendorCompany, "Featured Item", new BigDecimal("12.50"),
                ProductStatus.ACTIVE, marketplace.getId(), true, true);
        createProduct(vendorCompany, "Regular Item", new BigDecimal("8.00"),
                ProductStatus.ACTIVE, marketplace.getId(), true, false);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/vendors/" + vendor.getId() + "/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.marketplaceId").value(marketplace.getId().toString()))
                .andExpect(jsonPath("$.data.vendorCompanyName").value(vendorCompany.getName()))
                .andExpect(jsonPath("$.data.vendorTier").value("STANDARD"))
                .andExpect(jsonPath("$.data.vendorStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.featuredProducts.length()").value(1))
                .andExpect(jsonPath("$.data.featuredProducts[0].id").value(featured.getId().toString()))
                .andExpect(jsonPath("$.data.totalListedProducts").value(2));
    }

    @Test
    void getVendorStorefront_wrongMarketplace_returns404() throws Exception {
        User owner = createActiveUser("mkt-storefront-owner2@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Storefront Marketplace 2");
        Company otherMarketplace = createCompany(owner, "Other Storefront Marketplace");
        Company vendorCompany = createCompany(owner, "Storefront Vendor 2");
        MarketplaceVendor vendor = createVendor(marketplace, vendorCompany, VendorTier.STANDARD, VendorStatus.APPROVED);

        mockMvc.perform(get("/marketplaces/" + otherMarketplace.getId() + "/catalog/vendors/" + vendor.getId() + "/storefront"))
                .andExpect(status().isNotFound());
    }
}
