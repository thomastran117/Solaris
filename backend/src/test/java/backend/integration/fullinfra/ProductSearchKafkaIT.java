package backend.integration.fullinfra;

import backend.documents.ProductDocument;
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
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof that the search-indexing pipeline works against <b>real</b> infrastructure:
 * a product mutation on the HTTP API publishes a {@code product-events} record to a live Kafka
 * broker, the {@link backend.kafka.consumers.ProductIndexingKafkaConsumer} consumes it, and the
 * indexing worker writes the document to a live Elasticsearch index — which we then read back
 * through the real {@link ProductSearchRepository}.
 *
 * <p>Only <b>marketplace</b> products flow through Kafka ({@code ProductChangedPublisher} guards on
 * {@code marketplaceId != null}), so the fixtures seed a marketplace-listed product.
 */
class ProductSearchKafkaIT extends AbstractSearchKafkaIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSearchRepository productSearchRepository;

    @AfterEach
    void cleanSearchAndProducts() {
        try { productSearchRepository.deleteAll(); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Search Co " + UUID.randomUUID());
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addOwner(User user, Company company) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(CompanyRole.OWNER);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    /** Seeds a marketplace-listed product so its mutations flow through Kafka to the indexer. */
    private Product createMarketplaceProduct(Company company, String name) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setSku("SK-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("19.99"));
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        p.setMarketplaceId(UUID.randomUUID());
        p.setMarketplaceListed(true);
        return productRepository.save(p);
    }

    /** Polls up to {@code timeout} for the supplier to return a present/true result. */
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

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void updatingMarketplaceProduct_indexesItInElasticsearchViaKafka() throws Exception {
        User owner = createActiveUser("fi-index-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createMarketplaceProduct(company, "Original Name");

        // Mutate via the real API → publishes ProductIndexEvent (AFTER_COMMIT) → Kafka product-events
        // → ProductIndexingKafkaConsumer → indexing worker → Elasticsearch.
        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Indexed Widget")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());

        // The document must appear in the live ES index, carrying the updated name — proving the
        // whole real Kafka→ES path ran, not just the HTTP response.
        Optional<ProductDocument> indexed = await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()),
                Optional::isPresent);

        assertTrue(indexed.isPresent(),
                "Product should have been indexed into Elasticsearch via the Kafka pipeline");
        assertEquals("Indexed Widget", indexed.get().getName(),
                "Indexed document should reflect the updated product name");
    }

    @Test
    void deletingMarketplaceProduct_removesItFromElasticsearchViaKafka() throws Exception {
        User owner = createActiveUser("fi-remove-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createMarketplaceProduct(company, "To Be Removed");

        // First get it indexed.
        mockMvc.perform(patch("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Still Here")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk());
        await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()), Optional::isPresent);

        // Delete via the API → ProductRemoveEvent → Kafka DELETED → consumer removes from ES.
        mockMvc.perform(delete("/companies/{companyId}/products/{id}", company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        Optional<ProductDocument> afterDelete = await(Duration.ofSeconds(30),
                () -> productSearchRepository.findById(product.getId()),
                Optional::isEmpty);

        assertTrue(afterDelete.isEmpty(),
                "Product document should have been removed from Elasticsearch via the Kafka pipeline");
    }
}
