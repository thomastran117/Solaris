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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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

        // The public catalog search must now return the indexed product under the endpoint's
        // *default* sort (relevance) — proving the real ES query path (marketplace filter +
        // product_search analyzer + fuzzy match + default ordering) works end to end. Before #61 the
        // default was a createdAt field sort that ProductDocument didn't index, which failed with
        // "No mapping found for [createdAt] in order to sort on".
        // CatalogSearchResponse extends PagedResponse but adds facets, so ApiResponseAdvice (which
        // unwraps only the exact PagedResponse class) leaves it serialized as-is: the page content
        // is under $.data.items, not $.data.
        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/catalog/products")
                        .param("q", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(product.getId().toString())));
    }

    /** Seeds a vendor on a fresh marketplace; returns {marketplace, vendor}. */
    private Company[] seedMarketplaceWithVendor(String ownerEmail) {
        User owner = createActiveUser(ownerEmail, "Password1!");
        Company marketplace = createCompany(owner, "Sort Marketplace");
        createMarketplaceProfile(marketplace);
        Company vendor = createCompany(owner, "Sort Vendor");
        addMember(owner, vendor, CompanyRole.OWNER);
        createVendor(marketplace, vendor, VendorTier.STANDARD, VendorStatus.APPROVED);
        return new Company[] {marketplace, vendor};
    }

    /** Bulk-reindexes the vendor and waits until every given product is searchable. */
    private void reindexAndAwait(Company vendor, Product... products) throws Exception {
        mockMvc.perform(post("/companies/" + vendor.getId() + "/products/reindex")
                        .header("Authorization", bearer(accessTokenFor(vendor.getOwner())))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isAccepted());
        for (Product p : products) {
            Optional<ProductDocument> indexed = await(Duration.ofSeconds(30),
                    () -> productSearchRepository.findById(p.getId()), Optional::isPresent);
            assertTrue(indexed.isPresent(), "Product " + p.getName() + " should have been indexed");
        }
        refreshSearchIndices();
    }

    private void setCreatedAt(Product product, Instant createdAt) {
        jdbcTemplate.update("UPDATE products SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(createdAt), product.getId());
    }

    @Test
    void searchCatalog_noQueryDefaultSort_returnsIndexedProducts() throws Exception {
        Company[] seeded = seedMarketplaceWithVendor("mkt-sort-default@example.com");
        Product product = createProduct(seeded[1], "Default Sort Lamp", new BigDecimal("19.99"),
                ProductStatus.ACTIVE, seeded[0].getId(), true, false);
        reindexAndAwait(seeded[1], product);

        // The storefront's landing request: no q, no sort — the exact request that used to 503.
        mockMvc.perform(get("/marketplaces/" + seeded[0].getId() + "/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(product.getId().toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @ParameterizedTest
    @CsvSource({
            "relevance, desc", "createdAt, asc", "createdAt, desc", "price, asc", "price, desc",
            "stock, asc", "stock, desc", "name, asc", "name, desc", "unknownField, desc"
    })
    void searchCatalog_everySortOption_returns200FromElasticsearch(String sort, String direction) throws Exception {
        Company[] seeded = seedMarketplaceWithVendor(
                "mkt-sort-" + sort.toLowerCase() + "-" + direction + "@example.com");
        Product product = createProduct(seeded[1], "Sortable Widget", new BigDecimal("9.99"),
                ProductStatus.ACTIVE, seeded[0].getId(), true, false);
        reindexAndAwait(seeded[1], product);

        mockMvc.perform(get("/marketplaces/" + seeded[0].getId() + "/catalog/products")
                        .param("sort", sort)
                        .param("direction", direction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(product.getId().toString())));
    }

    @Test
    void searchCatalog_sortCreatedAt_ordersByIndexedCreationTime() throws Exception {
        Company[] seeded = seedMarketplaceWithVendor("mkt-sort-newest@example.com");
        Product older = createProduct(seeded[1], "Older Kettle", new BigDecimal("30.00"),
                ProductStatus.ACTIVE, seeded[0].getId(), true, false);
        Product newer = createProduct(seeded[1], "Newer Kettle", new BigDecimal("30.00"),
                ProductStatus.ACTIVE, seeded[0].getId(), true, false);
        setCreatedAt(older, Instant.now().minus(Duration.ofDays(10)));
        setCreatedAt(newer, Instant.now().minus(Duration.ofDays(1)));
        reindexAndAwait(seeded[1], older, newer);

        mockMvc.perform(get("/marketplaces/" + seeded[0].getId() + "/catalog/products")
                        .param("sort", "createdAt").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$.data.items[1].id").value(older.getId().toString()));

        mockMvc.perform(get("/marketplaces/" + seeded[0].getId() + "/catalog/products")
                        .param("sort", "createdAt").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(older.getId().toString()));
    }

    @Test
    void searchCatalog_sortStockAsc_ordersByIndexedStock() throws Exception {
        Company[] seeded = seedMarketplaceWithVendor("mkt-sort-stock@example.com");
        Product plenty = createProduct(seeded[1], "Plenty Mug", new BigDecimal("8.00"),
                ProductStatus.ACTIVE, seeded[0].getId(), true, false);
        Product few = createProduct(seeded[1], "Few Mug", new BigDecimal("8.00"),
                ProductStatus.ACTIVE, seeded[0].getId(), true, false);
        plenty.setStock(50);
        few.setStock(2);
        productRepository.save(plenty);
        productRepository.save(few);
        reindexAndAwait(seeded[1], plenty, few);

        mockMvc.perform(get("/marketplaces/" + seeded[0].getId() + "/catalog/products")
                        .param("sort", "stock").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(few.getId().toString()))
                .andExpect(jsonPath("$.data.items[1].id").value(plenty.getId().toString()));
    }

    @Test
    void searchCatalog_defaultSort_pinnedProductOutranksNewerUnpinnedProduct() throws Exception {
        Company[] seeded = seedMarketplaceWithVendor("mkt-sort-pinned@example.com");
        Product pinned = createProduct(seeded[1], "Pinned Chair", new BigDecimal("120.00"),
                ProductStatus.ACTIVE, seeded[0].getId(), true, false);
        Product newer = createProduct(seeded[1], "Newer Chair", new BigDecimal("120.00"),
                ProductStatus.ACTIVE, seeded[0].getId(), true, false);
        pinned.setPinnedUntil(Instant.now().plus(Duration.ofDays(7)));
        pinned.setPinnedRank(1);
        productRepository.save(pinned);
        setCreatedAt(pinned, Instant.now().minus(Duration.ofDays(30)));
        setCreatedAt(newer, Instant.now().minus(Duration.ofDays(1)));
        reindexAndAwait(seeded[1], pinned, newer);

        // Relevance (the default) is _score-first, so the function_score pin beats the newer
        // product that the createdAt tie-break alone would put first. No q — the storefront
        // landing page — is the case that must still honour merchandising.
        mockMvc.perform(get("/marketplaces/" + seeded[0].getId() + "/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(pinned.getId().toString()))
                .andExpect(jsonPath("$.data.items[1].id").value(newer.getId().toString()));

        // An explicit field sort overrides merchandising.
        mockMvc.perform(get("/marketplaces/" + seeded[0].getId() + "/catalog/products")
                        .param("sort", "createdAt").param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(newer.getId().toString()));
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
