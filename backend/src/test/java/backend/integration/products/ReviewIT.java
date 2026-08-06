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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ReviewController.
 *
 * NOTE: createReview requires existsDeliveredOrShippedOrderForProduct — tests that need to
 * exercise the POST endpoint fully would need a complete order + delivery setup. Auth guard
 * tests (401, validation) use the endpoint without that setup to verify the guard fires first.
 */
class ReviewIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Review Test Company " + UUID.randomUUID());
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

    private Product createProduct(Company company, String name) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(10));
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    // ── GET /companies/{companyId}/products/{productId}/reviews ───────────────

    @Test
    void getReviews_returnsEmptyPageWhenNoReviews() throws Exception {
        User owner = createActiveUser("review-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Reviewable Product");

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/reviews",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getReviews_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("review-list-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/reviews",
                        company.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReviews_returns400ForInvalidSortField() throws Exception {
        User owner = createActiveUser("review-list-sort@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Product");

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/reviews",
                        company.getId(), product.getId())
                        .param("sort", "DROP TABLE--")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReviews_returns400ForInvalidDirection() throws Exception {
        User owner = createActiveUser("review-list-dir@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Product Dir");

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/reviews",
                        company.getId(), product.getId())
                        .param("direction", "sideways")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── GET /companies/{companyId}/products/{productId}/reviews/summary ────────

    @Test
    void getReviewSummary_returns200WithZeroStats() throws Exception {
        User owner = createActiveUser("review-summary@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Summary Product");

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/reviews/summary",
                        company.getId(), product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void getReviewSummary_returns404ForUnknownProduct() throws Exception {
        User owner = createActiveUser("review-summary-404@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/reviews/summary",
                        company.getId(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── GET /companies/{companyId}/products/{productId}/reviews/me ────────────

    @Test
    void getMyReview_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("review-me-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Me Product");

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/reviews/me",
                company.getId(), product.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyReview_returns404WhenNoReview() throws Exception {
        User user = createActiveUser("review-me-none@example.com", "Password1!");
        User owner = createActiveUser("review-me-none-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Me Product None");

        mockMvc.perform(get("/companies/{companyId}/products/{productId}/reviews/me",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{companyId}/products/{productId}/reviews ──────────────

    @Test
    void createReview_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("review-create-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Product Unauth");

        Map<String, Object> body = Map.of("rating", 5);

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/reviews",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReview_returns400WhenRatingMissing() throws Exception {
        User user = createActiveUser("review-create-norating@example.com", "Password1!");
        User owner = createActiveUser("review-create-norating-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "No Rating Product");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/reviews",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReview_returns400WhenRatingOutOfRange() throws Exception {
        User user = createActiveUser("review-create-badrating@example.com", "Password1!");
        User owner = createActiveUser("review-create-badrating-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Bad Rating Product");

        Map<String, Object> body = Map.of("rating", 10);

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/reviews",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /companies/{companyId}/products/{productId}/reviews/me ──────────

    @Test
    void updateReview_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("review-upd-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Update Unauth");

        mockMvc.perform(patch("/companies/{companyId}/products/{productId}/reviews/me",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateReview_returns404WhenNoReviewExists() throws Exception {
        User user = createActiveUser("review-upd-none@example.com", "Password1!");
        User owner = createActiveUser("review-upd-none-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Update None");

        mockMvc.perform(patch("/companies/{companyId}/products/{productId}/reviews/me",
                        company.getId(), product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /companies/{companyId}/products/{productId}/reviews/me ─────────

    @Test
    void deleteReview_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("review-del-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Delete Unauth");

        mockMvc.perform(delete("/companies/{companyId}/products/{productId}/reviews/me",
                        company.getId(), product.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteReview_returns404WhenNoReviewExists() throws Exception {
        User user = createActiveUser("review-del-none@example.com", "Password1!");
        User owner = createActiveUser("review-del-none-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Delete None");

        mockMvc.perform(delete("/companies/{companyId}/products/{productId}/reviews/me",
                        company.getId(), product.getId())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /companies/{companyId}/products/{productId}/reviews/{reviewId}/helpful

    @Test
    void voteHelpful_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("review-vote-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Vote Unauth");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/reviews/{reviewId}/helpful",
                        company.getId(), product.getId(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void voteHelpful_returns404ForUnknownReview() throws Exception {
        User user = createActiveUser("review-vote-404@example.com", "Password1!");
        User owner = createActiveUser("review-vote-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Vote 404");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/reviews/{reviewId}/helpful",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /companies/{companyId}/products/{productId}/reviews/{reviewId}/helpful

    @Test
    void removeHelpful_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("review-unvote-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Unvote Unauth");

        mockMvc.perform(delete("/companies/{companyId}/products/{productId}/reviews/{reviewId}/helpful",
                        company.getId(), product.getId(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{reviewId}/media ────────────────────────────────────────────────

    @Test
    void attachMedia_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("review-media-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Media Unauth");

        Map<String, Object> body = Map.of("url", "https://cdn.example.com/photo.jpg");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/reviews/{reviewId}/media",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void attachMedia_returns404ForUnknownReview() throws Exception {
        User user = createActiveUser("review-media-404@example.com", "Password1!");
        User owner = createActiveUser("review-media-404-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Media 404");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", "https://cdn.example.com/photo.jpg");

        mockMvc.perform(post("/companies/{companyId}/products/{productId}/reviews/{reviewId}/media",
                        company.getId(), product.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }
}
