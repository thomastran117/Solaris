package backend.integration.search;

import backend.documents.ProductDocument;
import backend.integration.fullinfra.AbstractSearchKafkaIT;
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
import backend.repositories.search.ProductSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers SearchSuggestionController (/marketplaces/{id}/catalog/search/suggestions)
 * and CompanySearchSuggestionController (/companies/{id}/catalog/search/suggestions).
 *
 * Runs against live Elasticsearch (via {@link AbstractSearchKafkaIT}): the validation and
 * empty-result cases still hold (a random marketplace has no matching products), and one test
 * indexes a real product and asserts the autocomplete analyzers surface it as a suggestion.
 */
class SearchSuggestionIT extends AbstractSearchKafkaIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSearchRepository productSearchRepository;

    @AfterEach
    void cleanSuggestions() {
        try { productSearchRepository.deleteAll(); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Suggest Co " + UUID.randomUUID());
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addMember(User user, Company company, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Product createMarketplaceProduct(Company company, String name, UUID marketplaceId) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(new BigDecimal("19.99"));
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        p.setMarketplaceId(marketplaceId);
        p.setMarketplaceListed(true);
        return productRepository.save(p);
    }

    /** A plain (non-marketplace) product — company suggestions filter on companyId, not marketplace. */
    private Product createProduct(Company company, String name) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(new BigDecimal("19.99"));
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        return productRepository.save(p);
    }

    // ── /marketplaces/{id}/catalog/search/suggestions ─────────────────────────

    @Test
    void marketplace_validQuery_returns200WithEmptyLists() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.brands").isArray());
    }

    @Test
    void marketplace_validQueryWithLimit_returns200() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "running shoes")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isArray());
    }

    @Test
    void marketplace_singleCharQuery_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marketplace_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marketplace_luceneSpecialCharsStripped_returns200() throws Exception {
        // Query with Lucene operators — service strips them and still returns 200
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "shoe^boost"))
                .andExpect(status().isOk());
    }

    // ── /companies/{id}/catalog/search/suggestions ────────────────────────────

    @Test
    void company_validQuery_returns200WithEmptyLists() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "jacket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.brands").isArray());
    }

    @Test
    void company_validQueryWithLimit_returns200() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "winter coat")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isArray());
    }

    @Test
    void company_singleCharQuery_returns400() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void company_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void company_luceneSpecialCharsStripped_returns200() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "(boots)"))
                .andExpect(status().isOk());
    }

    // ── Live autocomplete (real Elasticsearch) ────────────────────────────────

    @Test
    void marketplace_prefixQuery_returnsIndexedProductViaAutocompleteAnalyzer() throws Exception {
        User owner = createActiveUser("suggest-owner@example.com", "Password1!");
        Company vendor = createCompany(owner);
        addMember(owner, vendor, CompanyRole.OWNER);
        UUID marketplaceId = UUID.randomUUID();
        Product product = createMarketplaceProduct(vendor, "Ergonomic Laptop Stand", marketplaceId);

        // Drive the real bulk indexing path so nameCompletion is indexed with the edge_ngram
        // autocomplete_index analyzer.
        mockMvc.perform(post("/companies/" + vendor.getId() + "/products/reindex")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isAccepted());

        await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()), Optional::isPresent);
        refreshSearchIndices();

        // A prefix of the product name must surface it — proving the autocomplete_index /
        // autocomplete_search analyzer pair works against the live cluster (the mocked-ES suite
        // could only ever return empty suggestions).
        mockMvc.perform(get("/marketplaces/" + marketplaceId + "/catalog/search/suggestions")
                        .param("q", "Ergon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[*].id", hasItem(product.getId().toString())));
    }

    @Test
    void company_prefixQuery_returnsIndexedProductViaAutocompleteAnalyzer() throws Exception {
        User owner = createActiveUser("suggest-company-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        // Company suggestions filter on companyId + status only, so a plain (non-marketplace)
        // product is a valid match once the reindex writes it to the live index.
        Product product = createProduct(company, "Wireless Keyboard");

        mockMvc.perform(post("/companies/" + company.getId() + "/products/reindex")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isAccepted());

        await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()), Optional::isPresent);
        refreshSearchIndices();

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search/suggestions")
                        .param("q", "Wirel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[*].id", hasItem(product.getId().toString())));
    }
}
