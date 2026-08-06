package backend.integration.marketplace;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Collection;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.CollectionRepository;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

class MarketplaceIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CollectionRepository collectionRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompanyWithOwner(User user) {
        Company company = new Company();
        company.setOwner(user);
        company.setName("Co " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(CompanyRole.OWNER);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);

        return company;
    }

    private String createBody(String slug) throws Exception {
        return objectMapper.writeValueAsString(Map.of("slug", slug));
    }

    /** Creates a marketplace via API and returns the MarketplaceProfile UUID ($.data.id). */
    private UUID createMarketplaceViaApi(User user, UUID companyId, String slug) throws Exception {
        String response = mockMvc.perform(post("/marketplaces/companies/" + companyId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    /** Creates a product with marketplace listing turned on. */
    private Product createMarketplaceListedProduct(Company vendorCompany, UUID marketplaceCompanyId) {
        Product p = new Product();
        p.setCompany(vendorCompany);
        p.setName("MP Product " + UUID.randomUUID().toString().substring(0, 8));
        p.setSku("MP-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("19.99"));
        p.setCurrency("USD");
        p.setStock(10);
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        p.setMarketplaceId(marketplaceCompanyId);
        p.setMarketplaceListed(true);
        return productRepository.save(p);
    }

    private Collection createActiveCollection(Company company, String slug) {
        Collection c = new Collection();
        c.setCompany(company);
        c.setName("Collection " + slug);
        c.setSlug(slug);
        c.setType(CollectionType.STATIC);
        c.setStatus(CollectionStatus.ACTIVE);
        c.setFeatured(false);
        return collectionRepository.save(c);
    }

    // ── POST /marketplaces/companies/{companyId} ──────────────────────────────

    @Test
    void create_returns201WithFields() throws Exception {
        User owner = createActiveUser("op-create@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);

        mockMvc.perform(post("/marketplaces/companies/" + company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("my-market")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.slug").value("my-market"))
                .andExpect(jsonPath("$.data.payoutSchedule").value("WEEKLY"))
                .andExpect(jsonPath("$.data.holdPeriodDays").value(7))
                .andExpect(jsonPath("$.data.defaultCurrency").value("USD"))
                .andExpect(jsonPath("$.data.acceptingApplications").value(true));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM marketplace_profiles WHERE slug = 'my-market'", Integer.class);
        assertEquals(1, rows, "Marketplace profile should be persisted");
    }

    @Test
    void create_missingSlug_returns400() throws Exception {
        User owner = createActiveUser("op-create-400@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);

        mockMvc.perform(post("/marketplaces/companies/" + company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidSlugPattern_returns400() throws Exception {
        User owner = createActiveUser("op-create-slug@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);

        // Uppercase letters are not allowed per the pattern ^[a-z0-9-]+$
        mockMvc.perform(post("/marketplaces/companies/" + company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("My-Market")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateSlug_returns409() throws Exception {
        User owner = createActiveUser("op-create-dupslug@example.com", "Password1!");
        Company company1 = createCompanyWithOwner(owner);
        User owner2 = createActiveUser("op-create-dupslug2@example.com", "Password1!");
        Company company2 = createCompanyWithOwner(owner2);

        createMarketplaceViaApi(owner, company1.getId(), "dup-slug");

        mockMvc.perform(post("/marketplaces/companies/" + company2.getId())
                        .header("Authorization", bearer(accessTokenFor(owner2)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("dup-slug")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_companyAlreadyHasMarketplace_returns409() throws Exception {
        User owner = createActiveUser("op-create-dup@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        createMarketplaceViaApi(owner, company.getId(), "first-market");

        mockMvc.perform(post("/marketplaces/companies/" + company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("second-market")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_nonOwnerOfCompany_returns403() throws Exception {
        User owner = createActiveUser("op-create-403owner@example.com", "Password1!");
        User stranger = createActiveUser("op-create-403stranger@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);

        mockMvc.perform(post("/marketplaces/companies/" + company.getId())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("stranger-market")))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("op-create-401@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);

        mockMvc.perform(post("/marketplaces/companies/" + company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("unauth-market")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId} ─────────────────────────────────────

    @Test
    void get_returns200WithFields() throws Exception {
        User owner = createActiveUser("op-get@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        UUID profileId = createMarketplaceViaApi(owner, company.getId(), "get-market");

        mockMvc.perform(get("/marketplaces/" + profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(profileId.toString()))
                .andExpect(jsonPath("$.data.slug").value("get-market"))
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()));
    }

    @Test
    void get_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /marketplaces/{marketplaceId} ───────────────────────────────────

    @Test
    void update_holdPeriodDays_returns200() throws Exception {
        User owner = createActiveUser("op-update@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        UUID profileId = createMarketplaceViaApi(owner, company.getId(), "update-market");

        String body = objectMapper.writeValueAsString(Map.of("holdPeriodDays", 14));
        mockMvc.perform(patch("/marketplaces/" + profileId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdPeriodDays").value(14));

        Integer holdPeriod = jdbcTemplate.queryForObject(
                "SELECT hold_period_days FROM marketplace_profiles", Integer.class);
        assertEquals(14, holdPeriod, "Hold period change should be persisted");
    }

    @Test
    void update_closesApplications_returns200() throws Exception {
        User owner = createActiveUser("op-update-close@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        UUID profileId = createMarketplaceViaApi(owner, company.getId(), "close-market");

        String body = objectMapper.writeValueAsString(Map.of("acceptingApplications", false));
        mockMvc.perform(patch("/marketplaces/" + profileId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptingApplications").value(false));

        Boolean accepting = jdbcTemplate.queryForObject(
                "SELECT accepting_applications FROM marketplace_profiles", Boolean.class);
        assertEquals(Boolean.FALSE, accepting, "Closing applications should be persisted");
    }

    @Test
    void update_unknownMarketplace_returns404() throws Exception {
        User owner = createActiveUser("op-update-404@example.com", "Password1!");

        mockMvc.perform(patch("/marketplaces/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_nonOwner_returns403() throws Exception {
        User owner = createActiveUser("op-update-403owner@example.com", "Password1!");
        User stranger = createActiveUser("op-update-403stranger@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        UUID profileId = createMarketplaceViaApi(owner, company.getId(), "403-market");

        mockMvc.perform(patch("/marketplaces/" + profileId)
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("holdPeriodDays", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        User owner = createActiveUser("op-update-401@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        UUID profileId = createMarketplaceViaApi(owner, company.getId(), "401-market");

        mockMvc.perform(patch("/marketplaces/" + profileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/collections/featured ────────────────

    @Test
    void listFeatured_emptyForNewMarketplace_returns200() throws Exception {
        User owner = createActiveUser("op-feat@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        createMarketplaceViaApi(owner, company.getId(), "feat-market");

        // marketplaceId in this URL is the company ID
        mockMvc.perform(get("/marketplaces/" + company.getId() + "/collections/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listFeatured_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/featured"))
                .andExpect(status().isNotFound());
    }

    // ── GET /marketplaces/{marketplaceId}/collections/featured/vendor/{vendorId}

    @Test
    void listFeaturedForVendor_emptyReturns200() throws Exception {
        User owner = createActiveUser("op-vfeat@example.com", "Password1!");
        Company marketplaceCo = createCompanyWithOwner(owner);
        createMarketplaceViaApi(owner, marketplaceCo.getId(), "vfeat-market");

        User vendor = createActiveUser("vendor-vfeat@example.com", "Password1!");
        Company vendorCo = createCompanyWithOwner(vendor);

        mockMvc.perform(get("/marketplaces/" + marketplaceCo.getId() + "/collections/featured/vendor/" + vendorCo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listFeaturedForVendor_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/featured/vendor/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── GET /marketplaces/{marketplaceId}/collections/{slug} ──────────────────

    @Test
    void getCollectionBySlug_returnsCollection() throws Exception {
        User owner = createActiveUser("op-slug@example.com", "Password1!");
        Company marketplaceCo = createCompanyWithOwner(owner);
        createMarketplaceViaApi(owner, marketplaceCo.getId(), "slug-market");

        // Vendor company with a marketplace-listed product
        User vendor = createActiveUser("vendor-slug@example.com", "Password1!");
        Company vendorCo = createCompanyWithOwner(vendor);
        createMarketplaceListedProduct(vendorCo, marketplaceCo.getId());

        // Active collection owned by the vendor company
        createActiveCollection(vendorCo, "vendor-picks");

        mockMvc.perform(get("/marketplaces/" + marketplaceCo.getId() + "/collections/vendor-picks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("vendor-picks"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getCollectionBySlug_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/any-slug"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCollectionBySlug_unknownSlug_returns404() throws Exception {
        User owner = createActiveUser("op-slug-404@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        createMarketplaceViaApi(owner, company.getId(), "slug-404-market");

        mockMvc.perform(get("/marketplaces/" + company.getId() + "/collections/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ── GET /marketplaces/{marketplaceId}/collections/{slug}/products ─────────

    @Test
    void listProductsBySlug_returnsProducts() throws Exception {
        User owner = createActiveUser("op-slprod@example.com", "Password1!");
        Company marketplaceCo = createCompanyWithOwner(owner);
        createMarketplaceViaApi(owner, marketplaceCo.getId(), "slprod-market");

        User vendor = createActiveUser("vendor-slprod@example.com", "Password1!");
        Company vendorCo = createCompanyWithOwner(vendor);
        createMarketplaceListedProduct(vendorCo, marketplaceCo.getId());
        createActiveCollection(vendorCo, "slprod-picks");

        mockMvc.perform(get("/marketplaces/" + marketplaceCo.getId() + "/collections/slprod-picks/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listProductsBySlug_unknownMarketplace_returns404() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/collections/any-slug/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProductsBySlug_unknownSlug_returns404() throws Exception {
        User owner = createActiveUser("op-slprod-404@example.com", "Password1!");
        Company company = createCompanyWithOwner(owner);
        createMarketplaceViaApi(owner, company.getId(), "slprod-404-market");

        mockMvc.perform(get("/marketplaces/" + company.getId() + "/collections/nonexistent/products"))
                .andExpect(status().isNotFound());
    }
}
