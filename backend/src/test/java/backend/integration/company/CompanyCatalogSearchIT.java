package backend.integration.company;

import backend.documents.ProductDocument;
import backend.integration.fullinfra.AbstractSearchKafkaIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Product;
import backend.models.core.PromotionRule;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.PromotionRuleType;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.search.ProductSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Live Elasticsearch + Kafka coverage (#61) for company-scoped catalog reads of <em>plain</em>
 * products, i.e. products with no {@code marketplaceId}:
 * <ul>
 *   <li>a product created through the API reaches the search index incrementally via the
 *       product-events topic (previously only marketplace products were published), and</li>
 *   <li>the public storefront search {@code GET /companies/{id}/catalog/search} returns it from
 *       Elasticsearch under the default (relevance) sort, which used to fail on the unmapped
 *       {@code createdAt} sort and 503 whenever a filter was present;</li>
 *   <li>the unqueried {@code GET /companies/{id}/products} listing reads PostgreSQL, so a product
 *       is visible immediately after creation, before the asynchronous index catches up — and that
 *       database path applies every listing filter the ES path does, including
 *       {@code hasDiscount=false}.</li>
 * </ul>
 */
class CompanyCatalogSearchIT extends AbstractSearchKafkaIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductSearchRepository productSearchRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PromotionRuleRepository promotionRuleRepository;

    @AfterEach
    void clean() {
        try { productSearchRepository.deleteAll(); } catch (Exception ignored) {}
    }

    private Company createCompanyOwnedBy(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Catalog Co " + UUID.randomUUID().toString().substring(0, 8));
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

    private UUID createActiveProductViaApi(Company company, User owner, String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("price", 24.50);
        body.put("stock", 7);
        body.put("status", "ACTIVE");
        body.put("listed", true);
        MvcResult result = mockMvc.perform(post("/companies/" + company.getId() + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    @Test
    void plainProductCreatedViaApi_isIndexedIncrementallyAndFoundByStorefrontSearch() throws Exception {
        User owner = createActiveUser("company-catalog-search@example.com", "Password1!");
        Company company = createCompanyOwnedBy(owner);
        UUID productId = createActiveProductViaApi(company, owner, "Walnut Desk Organizer");

        // No reindex call: the create must flow ProductIndexEvent -> product-events -> indexer.
        Optional<ProductDocument> indexed = await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(productId), Optional::isPresent);
        assertTrue(indexed.isPresent(), "Plain product should be indexed through the Kafka path");
        assertNull(indexed.get().getMarketplaceId());
        assertTrue(indexed.get().getCreatedAt() != null, "createdAt must be indexed for sorting");
        refreshSearchIndices();

        // Default sort (relevance) with a text filter — previously a 503.
        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("q", "walnut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(productId.toString())));

        // Explicit "Newest" sort on the indexed createdAt field.
        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("q", "organizer")
                        .param("sort", "createdAt")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(productId.toString()));
    }

    @Test
    void unqueriedProductListing_showsNewProductImmediately() throws Exception {
        User owner = createActiveUser("company-listing-fresh@example.com", "Password1!");
        Company company = createCompanyOwnedBy(owner);
        UUID productId = createActiveProductViaApi(company, owner, "Fresh Listing Lamp");

        // Straight after the write, with no wait for indexing: the listing reads PostgreSQL.
        // PagedResponse is unwrapped by ApiResponseAdvice, so items are directly under $.data.
        mockMvc.perform(get("/companies/" + company.getId() + "/products")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(productId.toString())));
    }

    @Test
    void unqueriedProductListing_hasDiscountFilter_appliesBothPolarities() throws Exception {
        User owner = createActiveUser("company-listing-discount@example.com", "Password1!");
        Company company = createCompanyOwnedBy(owner);
        Product discounted = saveActiveProduct(company, "Discounted Vase");
        Product fullPrice = saveActiveProduct(company, "Full Price Vase");

        PromotionRule rule = new PromotionRule();
        rule.setCompany(company);
        rule.setName("Spring sale");
        rule.setRuleType(PromotionRuleType.PERCENTAGE_OFF);
        rule.setConfigJson("{\"percent\":10}");
        rule.getTargetProducts().add(discounted);
        promotionRuleRepository.save(rule);

        // No q, so both requests take the PostgreSQL listing path.
        mockMvc.perform(get("/companies/" + company.getId() + "/products")
                        .param("hasDiscount", "false")
                        .param("sort", "price")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(fullPrice.getId().toString())))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(discounted.getId().toString()))));

        mockMvc.perform(get("/companies/" + company.getId() + "/products")
                        .param("hasDiscount", "true")
                        .param("sort", "price")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(discounted.getId().toString())))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(fullPrice.getId().toString()))));
    }

    private Product saveActiveProduct(Company company, String name) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(new BigDecimal("40.00"));
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        return productRepository.save(p);
    }
}
