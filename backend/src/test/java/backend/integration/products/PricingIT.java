package backend.integration.products;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.ProductStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PricingIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;


    // ── Setup helpers ─────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Pricing Company " + UUID.randomUUID());
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

    private Product createActiveListedProduct(Company company, BigDecimal price) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Pricing Product " + UUID.randomUUID());
        p.setPrice(price);
        p.setStatus(ProductStatus.ACTIVE);
        // listed defaults to true
        return productRepository.save(p);
    }

    private String quoteBody(UUID productId, int qty) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", productId.toString());
        item.put("quantity", qty);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        return objectMapper.writeValueAsString(body);
    }

    // ── POST /pricing/quote ───────────────────────────────────────────────────

    @Test
    void quote_returnsSubtotalForActiveProduct() throws Exception {
        User owner = createActiveUser("pricing-quote-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        Product product = createActiveListedProduct(company, BigDecimal.valueOf(15.00));

        mockMvc.perform(post("/pricing/quote")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(product.getId(), 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotal").value(30.00))
                .andExpect(jsonPath("$.data.finalTotal").value(30.00))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.lines", hasSize(1)));
    }

    @Test
    void quote_allowsAnonymousCallerWithoutAuth() throws Exception {
        User owner = createActiveUser("pricing-anon-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createActiveListedProduct(company, BigDecimal.valueOf(10.00));

        mockMvc.perform(post("/pricing/quote")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(product.getId(), 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotal").value(10.00))
                .andExpect(jsonPath("$.data.finalTotal").value(10.00));
    }

    @Test
    void quote_returns404WhenProductNotFound() throws Exception {
        mockMvc.perform(post("/pricing/quote")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(UUID.randomUUID(), 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void quote_returns400WhenProductIsNotActive() throws Exception {
        User owner = createActiveUser("pricing-draft-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product p = new Product();
        p.setCompany(company);
        p.setName("Draft Product");
        p.setPrice(BigDecimal.valueOf(10.00));
        p.setStatus(ProductStatus.DRAFT);
        productRepository.save(p);

        mockMvc.perform(post("/pricing/quote")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(p.getId(), 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quote_returns400WhenItemsEmpty() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of());

        mockMvc.perform(post("/pricing/quote")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quote_returns400WhenBothProductIdAndBundleIdSet() throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", UUID.randomUUID().toString());
        item.put("bundleId", UUID.randomUUID().toString());
        item.put("quantity", 1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));

        mockMvc.perform(post("/pricing/quote")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quote_returns400WhenNeitherProductIdNorBundleIdSet() throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("quantity", 1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));

        mockMvc.perform(post("/pricing/quote")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quote_multipleItemsComputeCorrectSubtotal() throws Exception {
        User owner = createActiveUser("pricing-multi-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product p1 = createActiveListedProduct(company, BigDecimal.valueOf(5.00));
        Product p2 = createActiveListedProduct(company, BigDecimal.valueOf(10.00));

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("productId", p1.getId().toString());
        item1.put("quantity", 2); // 10.00

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("productId", p2.getId().toString());
        item2.put("quantity", 3); // 30.00

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item1, item2));

        mockMvc.perform(post("/pricing/quote")
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotal").value(40.00))
                .andExpect(jsonPath("$.data.lines", hasSize(2)));
    }
}
