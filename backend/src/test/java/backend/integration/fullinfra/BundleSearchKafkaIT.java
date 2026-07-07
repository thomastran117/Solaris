package backend.integration.fullinfra;

import backend.documents.BundleDocument;
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
import backend.repositories.search.BundleSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof that bundle changes flow through the real Kafka + Elasticsearch pipeline:
 * creating a bundle on the HTTP API publishes to the live {@code bundle-events} topic, the real
 * {@link backend.kafka.consumers.ProductIndexingKafkaConsumer#onBundleEvent} consumes it, and the
 * indexing worker writes the {@link BundleDocument} to a live Elasticsearch index — read back
 * through the real {@link BundleSearchRepository}. Unlike products, bundle events have no
 * marketplace guard, so any bundle mutation participates.
 */
class BundleSearchKafkaIT extends AbstractSearchKafkaIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BundleSearchRepository bundleSearchRepository;

    @AfterEach
    void cleanSearchAndBundles() {
        try { bundleSearchRepository.deleteAll(); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM bundle_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_bundles"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Bundle Search Co " + UUID.randomUUID());
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

    private Product createProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Bundle Item " + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("10.00"));
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    private UUID createBundleViaApi(Company company, User owner, String name, UUID productId) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", productId.toString());
        item.put("quantity", 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("items", List.of(item));

        String response = mockMvc.perform(post("/companies/{companyId}/bundles", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

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
    void creatingBundle_indexesItInElasticsearchViaKafka() throws Exception {
        User owner = createActiveUser("fi-bundle-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company);

        UUID bundleId = createBundleViaApi(company, owner, "Indexed Bundle", product.getId());

        Optional<BundleDocument> indexed = await(Duration.ofSeconds(30),
                () -> bundleSearchRepository.findById(bundleId), Optional::isPresent);

        assertTrue(indexed.isPresent(),
                "Bundle should have been indexed into Elasticsearch via the Kafka pipeline");
        assertEquals("Indexed Bundle", indexed.get().getName(),
                "Indexed bundle document should carry the bundle name");
    }

    @Test
    void deletingBundle_removesItFromElasticsearchViaKafka() throws Exception {
        User owner = createActiveUser("fi-bundle-del-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        Product product = createProduct(company);
        UUID bundleId = createBundleViaApi(company, owner, "Doomed Bundle", product.getId());

        await(Duration.ofSeconds(30), () -> bundleSearchRepository.findById(bundleId), Optional::isPresent);

        mockMvc.perform(delete("/companies/{companyId}/bundles/{bundleId}", company.getId(), bundleId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        Optional<BundleDocument> afterDelete = await(Duration.ofSeconds(30),
                () -> bundleSearchRepository.findById(bundleId), Optional::isEmpty);

        assertTrue(afterDelete.isEmpty(),
                "Bundle document should have been removed from Elasticsearch via the Kafka pipeline");
    }
}
