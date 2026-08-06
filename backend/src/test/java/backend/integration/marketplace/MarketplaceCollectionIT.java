package backend.integration.marketplace;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Collection;
import backend.models.core.CollectionProduct;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.CollectionProductRepository;
import backend.repositories.CollectionRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers MarketplaceCollectionController:
 *  - GET /marketplaces/{marketplaceId}/collections/featured
 *  - GET /marketplaces/{marketplaceId}/collections/featured/vendor/{vendorId}
 *  - GET /marketplaces/{marketplaceId}/collections/{slug}
 *  - GET /marketplaces/{marketplaceId}/collections/{slug}/products
 *
 * All four endpoints 404 with "Marketplace not found" unless a MarketplaceProfile exists for
 * {marketplaceId}. {@code listFeaturedForVendor}'s {vendorId} maps directly onto
 * Collection.company.id (CollectionRepository#findFeaturedForVendor) — i.e. the vendor's
 * Company id, not MarketplaceVendor.id.
 */
class MarketplaceCollectionIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private MarketplaceProfileRepository marketplaceProfileRepository;
    @Autowired private CollectionRepository collectionRepository;
    @Autowired private CollectionProductRepository collectionProductRepository;


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

    private Collection createCollection(Company company, String name, String slug, CollectionType type,
                                          CollectionStatus status, boolean featured, Integer featuredRank) {
        Collection c = new Collection();
        c.setCompany(company);
        c.setName(name);
        c.setSlug(slug);
        c.setType(type);
        c.setStatus(status);
        c.setFeatured(featured);
        c.setFeaturedRank(featuredRank);
        return collectionRepository.save(c);
    }

    private CollectionProduct addCollectionProduct(Collection collection, Product product, Integer pinnedRank) {
        CollectionProduct cp = new CollectionProduct();
        cp.setCollection(collection);
        cp.setProduct(product);
        cp.setPinnedRank(pinnedRank);
        return collectionProductRepository.save(cp);
    }

    // ── GET /marketplaces/{marketplaceId}/collections/featured ─────────────────

    @Test
    void listFeatured_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/featured"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFeatured_noFeaturedCollections_returnsEmptyList() throws Exception {
        User owner = createActiveUser("mc-featured-owner1@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Featured Marketplace");
        createMarketplaceProfile(marketplace);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listFeatured_returnsActiveFeaturedCollectionsOrderedByRank() throws Exception {
        User owner = createActiveUser("mc-featured-owner2@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Featured Marketplace 2");
        createMarketplaceProfile(marketplace);
        Company vendor1 = createCompany(owner, "Featured Vendor 1");
        Company vendor2 = createCompany(owner, "Featured Vendor 2");
        createProduct(vendor1, "Vendor1 Item", BigDecimal.TEN, ProductStatus.ACTIVE, marketplace.getId(), true, false);
        createProduct(vendor2, "Vendor2 Item", BigDecimal.TEN, ProductStatus.ACTIVE, marketplace.getId(), true, false);

        Collection rankTwo = createCollection(vendor1, "Vendor1 Picks", "vendor1-picks",
                CollectionType.STATIC, CollectionStatus.ACTIVE, true, 2);
        Collection rankOne = createCollection(vendor2, "Vendor2 Picks", "vendor2-picks",
                CollectionType.STATIC, CollectionStatus.ACTIVE, true, 1);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(rankOne.getId().toString()))
                .andExpect(jsonPath("$.data[1].id").value(rankTwo.getId().toString()));
    }

    @Test
    void listFeatured_excludesNonFeaturedAndDraftCollections() throws Exception {
        User owner = createActiveUser("mc-featured-owner3@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Featured Marketplace 3");
        createMarketplaceProfile(marketplace);
        Company vendor = createCompany(owner, "Featured Vendor 3");
        createProduct(vendor, "Vendor Item", BigDecimal.TEN, ProductStatus.ACTIVE, marketplace.getId(), true, false);

        createCollection(vendor, "Not Featured", "not-featured", CollectionType.STATIC, CollectionStatus.ACTIVE, false, null);
        createCollection(vendor, "Draft Featured", "draft-featured", CollectionType.STATIC, CollectionStatus.DRAFT, true, 1);
        Collection active = createCollection(vendor, "Active Featured", "active-featured", CollectionType.STATIC, CollectionStatus.ACTIVE, true, null);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(active.getId().toString()));
    }

    @Test
    void listFeatured_excludesCollectionsFromUnlistedVendors() throws Exception {
        User owner = createActiveUser("mc-featured-owner4@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Featured Marketplace 4");
        createMarketplaceProfile(marketplace);
        Company vendor = createCompany(owner, "Featured Vendor 4");
        // Vendor has no marketplace-listed products, so its collections aren't eligible.
        createProduct(vendor, "Unlisted Item", BigDecimal.TEN, ProductStatus.ACTIVE, marketplace.getId(), false, false);
        createCollection(vendor, "Featured but Unlisted Vendor", "unlisted-vendor-featured",
                CollectionType.STATIC, CollectionStatus.ACTIVE, true, null);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ── GET /marketplaces/{marketplaceId}/collections/featured/vendor/{vendorId} ─

    @Test
    void listFeaturedForVendor_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID()
                        + "/collections/featured/vendor/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFeaturedForVendor_returnsVendorsFeaturedActiveCollections() throws Exception {
        User owner = createActiveUser("mc-vendor-featured-owner1@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Vendor Featured Marketplace");
        createMarketplaceProfile(marketplace);
        Company vendorCompany = createCompany(owner, "Vendor Featured Co");

        Collection featured = createCollection(vendorCompany, "Vendor Picks", "vendor-picks",
                CollectionType.STATIC, CollectionStatus.ACTIVE, true, null);
        createCollection(vendorCompany, "Vendor Drafts", "vendor-drafts",
                CollectionType.STATIC, CollectionStatus.DRAFT, true, null);
        createCollection(vendorCompany, "Vendor Non-featured", "vendor-non-featured",
                CollectionType.STATIC, CollectionStatus.ACTIVE, false, null);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId()
                        + "/collections/featured/vendor/" + vendorCompany.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(featured.getId().toString()))
                .andExpect(jsonPath("$.data[0].companyId").value(vendorCompany.getId().toString()));
    }

    @Test
    void listFeaturedForVendor_excludesOtherVendorsCollections() throws Exception {
        User owner = createActiveUser("mc-vendor-featured-owner2@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Vendor Featured Marketplace 2");
        createMarketplaceProfile(marketplace);
        Company vendor1 = createCompany(owner, "Vendor Featured Co 1");
        Company vendor2 = createCompany(owner, "Vendor Featured Co 2");

        createCollection(vendor1, "Vendor1 Featured", "vendor1-featured",
                CollectionType.STATIC, CollectionStatus.ACTIVE, true, null);
        Collection vendor2Featured = createCollection(vendor2, "Vendor2 Featured", "vendor2-featured",
                CollectionType.STATIC, CollectionStatus.ACTIVE, true, null);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId()
                        + "/collections/featured/vendor/" + vendor2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(vendor2Featured.getId().toString()));
    }

    @Test
    void listFeaturedForVendor_noMatchingCollections_returnsEmptyList() throws Exception {
        User owner = createActiveUser("mc-vendor-featured-owner3@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Vendor Featured Marketplace 3");
        createMarketplaceProfile(marketplace);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId()
                        + "/collections/featured/vendor/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ── GET /marketplaces/{marketplaceId}/collections/{slug} ────────────────────

    @Test
    void getBySlug_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/some-slug"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBySlug_noMarketplaceListedVendors_returns404() throws Exception {
        User owner = createActiveUser("mc-slug-owner1@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Slug Marketplace 1");
        createMarketplaceProfile(marketplace);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/some-slug"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBySlug_draftCollection_returns404() throws Exception {
        User owner = createActiveUser("mc-slug-owner2@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Slug Marketplace 2");
        createMarketplaceProfile(marketplace);
        Company vendor = createCompany(owner, "Slug Vendor 2");
        createProduct(vendor, "Slug Item", BigDecimal.TEN, ProductStatus.ACTIVE, marketplace.getId(), true, false);
        createCollection(vendor, "Draft Collection", "draft-collection", CollectionType.STATIC, CollectionStatus.DRAFT, false, null);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/draft-collection"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBySlug_found_returnsCollectionDetails() throws Exception {
        User owner = createActiveUser("mc-slug-owner3@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Slug Marketplace 3");
        createMarketplaceProfile(marketplace);
        Company vendor = createCompany(owner, "Slug Vendor 3");
        createProduct(vendor, "Slug Item 3", BigDecimal.TEN, ProductStatus.ACTIVE, marketplace.getId(), true, false);
        Collection collection = createCollection(vendor, "Summer Sale", "summer-sale",
                CollectionType.STATIC, CollectionStatus.ACTIVE, true, 3);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/summer-sale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(collection.getId().toString()))
                .andExpect(jsonPath("$.data.companyId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Summer Sale"))
                .andExpect(jsonPath("$.data.slug").value("summer-sale"))
                .andExpect(jsonPath("$.data.type").value("STATIC"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.featured").value(true))
                .andExpect(jsonPath("$.data.featuredRank").value(3))
                .andExpect(jsonPath("$.data.productCount").value(0))
                .andExpect(jsonPath("$.data.lastMaterialisedAt").value(nullValue()));
    }

    // ── GET /marketplaces/{marketplaceId}/collections/{slug}/products ───────────

    @Test
    void listProductsBySlug_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/some-slug/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProductsBySlug_collectionNotFound_returns404() throws Exception {
        User owner = createActiveUser("mc-products-owner1@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Products Marketplace 1");
        createMarketplaceProfile(marketplace);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/missing/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProductsBySlug_returnsOnlyActiveMarketplaceListedProducts() throws Exception {
        User owner = createActiveUser("mc-products-owner2@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Products Marketplace 2");
        Company otherMarketplace = createCompany(owner, "Other Marketplace 2");
        createMarketplaceProfile(marketplace);
        Company vendor = createCompany(owner, "Products Vendor 2");

        Product visible = createProduct(vendor, "Visible Item", new BigDecimal("9.99"),
                ProductStatus.ACTIVE, marketplace.getId(), true, false);
        Product draft = createProduct(vendor, "Draft Item", new BigDecimal("9.99"),
                ProductStatus.DRAFT, marketplace.getId(), true, false);
        Product unlisted = createProduct(vendor, "Unlisted Item", new BigDecimal("9.99"),
                ProductStatus.ACTIVE, marketplace.getId(), false, false);
        Product wrongMarketplace = createProduct(vendor, "Wrong Marketplace Item", new BigDecimal("9.99"),
                ProductStatus.ACTIVE, otherMarketplace.getId(), true, false);

        Collection collection = createCollection(vendor, "Curated", "curated",
                CollectionType.STATIC, CollectionStatus.ACTIVE, false, null);
        addCollectionProduct(collection, visible, null);
        addCollectionProduct(collection, draft, null);
        addCollectionProduct(collection, unlisted, null);
        addCollectionProduct(collection, wrongMarketplace, null);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/curated/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(visible.getId().toString()))
                // Draft, unlisted and wrong-marketplace items are filtered out at the query
                // level, so totalElements reflects only the visible product.
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void listProductsBySlug_pagination_respectsSizeParam() throws Exception {
        User owner = createActiveUser("mc-products-owner3@example.com", "Password1!");
        Company marketplace = createCompany(owner, "Products Marketplace 3");
        createMarketplaceProfile(marketplace);
        Company vendor = createCompany(owner, "Products Vendor 3");

        Product p1 = createProduct(vendor, "Item 1", BigDecimal.ONE, ProductStatus.ACTIVE, marketplace.getId(), true, false);
        Product p2 = createProduct(vendor, "Item 2", BigDecimal.ONE, ProductStatus.ACTIVE, marketplace.getId(), true, false);
        Product p3 = createProduct(vendor, "Item 3", BigDecimal.ONE, ProductStatus.ACTIVE, marketplace.getId(), true, false);

        Collection collection = createCollection(vendor, "Paged", "paged",
                CollectionType.STATIC, CollectionStatus.ACTIVE, false, null);
        addCollectionProduct(collection, p1, null);
        addCollectionProduct(collection, p2, null);
        addCollectionProduct(collection, p3, null);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/paged/products")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(3));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/collections/paged/products")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }

    @Test
    void listProductsBySlug_negativePage_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/some-slug/products")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listProductsBySlug_sizeTooSmall_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/some-slug/products")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listProductsBySlug_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/some-slug/products")
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }
}
