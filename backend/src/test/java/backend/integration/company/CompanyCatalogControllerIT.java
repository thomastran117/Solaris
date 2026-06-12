package backend.integration.company;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Product;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers CompanyCatalogController (GET /companies/{companyId}/catalog/search) —
 * public, anonymous-safe storefront search scoped to a single company.
 *
 * elasticsearchOperations is @MockitoBean'd and returns null, so
 * elasticsearchOperations.search(...) throws NPE in ProductServiceImpl,
 * which is caught and handled as "ES unavailable":
 *  - if any search filter (q/category/brand/minPrice/maxPrice) is present,
 *    a 503 ServiceUnavaliableException is thrown;
 *  - otherwise it falls back to a DB page of the company's ACTIVE products
 *    only — both `items` and `totalElements` exclude non-ACTIVE products.
 */
class CompanyCatalogControllerIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Catalog Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private Product createProduct(Company company, String name, BigDecimal price, ProductStatus status) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(price);
        p.setStatus(status);
        return productRepository.save(p);
    }

    // ── GET /companies/{companyId}/catalog/search ───────────────────────────────

    @Test
    void searchCatalog_unknownCompany_returns404() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchCatalog_noFilters_returnsActiveProductFromDb() throws Exception {
        User owner = createActiveUser("ccc-owner-1@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Widget A", new BigDecimal("9.99"), ProductStatus.ACTIVE);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].name").value("Widget A"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));
    }

    @Test
    void searchCatalog_excludesNonActiveFromItemsAndTotal() throws Exception {
        User owner = createActiveUser("ccc-owner-2@example.com", "Password1!");
        Company company = createCompany(owner);
        createProduct(company, "Active Widget", new BigDecimal("9.99"), ProductStatus.ACTIVE);
        createProduct(company, "Draft Widget", new BigDecimal("9.99"), ProductStatus.DRAFT);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void searchCatalog_pagination_respectsSizeParam() throws Exception {
        User owner = createActiveUser("ccc-owner-3@example.com", "Password1!");
        Company company = createCompany(owner);
        createProduct(company, "Widget 1", BigDecimal.ONE, ProductStatus.ACTIVE);
        createProduct(company, "Widget 2", BigDecimal.ONE, ProductStatus.ACTIVE);
        createProduct(company, "Widget 3", BigDecimal.ONE, ProductStatus.ACTIVE);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("size", "2")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("size", "2")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void searchCatalog_withQueryFilter_returns503WhenEsUnavailable() throws Exception {
        User owner = createActiveUser("ccc-owner-4@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("q", "widget"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void searchCatalog_withMinPriceFilter_returns503WhenEsUnavailable() throws Exception {
        User owner = createActiveUser("ccc-owner-5@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("minPrice", "10.00"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void searchCatalog_invalidSortField_returns400() throws Exception {
        User owner = createActiveUser("ccc-owner-6@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("sort", "created_at"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_invalidDirection_returns400() throws Exception {
        User owner = createActiveUser("ccc-owner-7@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("direction", "sideways"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_negativePage_returns400() throws Exception {
        User owner = createActiveUser("ccc-owner-8@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_sizeTooLarge_returns400() throws Exception {
        User owner = createActiveUser("ccc-owner-9@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCatalog_queryTooLong_returns400() throws Exception {
        User owner = createActiveUser("ccc-owner-10@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/" + company.getId() + "/catalog/search")
                        .param("q", "a".repeat(201)))
                .andExpect(status().isBadRequest());
    }
}
