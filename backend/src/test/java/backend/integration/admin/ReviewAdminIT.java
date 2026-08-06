package backend.integration.admin;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ReviewStatus;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers ReviewAdminController (/admin/reviews) — ADMIN-only.
 *
 * moderate: real DB with ProductReview + Product + Company. Post-commit
 * indexing is async (queued in ReviewIndexingService's worker pool) and does
 * not block the response.
 *
 * reindex: IndexVersionManager.rolloverIndex is a no-op when
 * app.elasticsearch.versioning.enabled=false (set in test properties).
 * reviewIndexingService.reindexAll() returns immediately — no reviews in DB.
 */
class ReviewAdminIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductReviewRepository reviewRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createAdmin(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Password1!"));
        u.setRole(UserRole.ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Review Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private Product createProduct(Company company) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Test Product");
        p.setPrice(new BigDecimal("19.99"));
        return productRepository.save(p);
    }

    private ProductReview createReview(User reviewer, Product product) {
        ProductReview r = new ProductReview();
        r.setProduct(product);
        r.setReviewer(reviewer);
        r.setRating(4);
        r.setTitle("Good product");
        r.setBody("Really happy with this purchase.");
        r.setStatus(ReviewStatus.PUBLISHED);
        return reviewRepository.save(r);
    }

    private String actionBody(String action) throws Exception {
        return objectMapper.writeValueAsString(Map.of("action", action));
    }

    // ── POST /admin/reviews/{reviewId}/moderate ────────────────────────────────

    @Test
    void moderate_publish_returns204() throws Exception {
        User admin = createAdmin("ra-admin-pub@example.com");
        User reviewer = createActiveUser("ra-reviewer-pub@example.com", "Password1!");
        Company company = createCompany(admin);
        Product product = createProduct(company);
        ProductReview review = createReview(reviewer, product);

        mockMvc.perform(post("/admin/reviews/" + review.getId() + "/moderate")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("PUBLISH")))
                .andExpect(status().isNoContent());
    }

    @Test
    void moderate_hide_returns204() throws Exception {
        User admin = createAdmin("ra-admin-hide@example.com");
        User reviewer = createActiveUser("ra-reviewer-hide@example.com", "Password1!");
        Company company = createCompany(admin);
        Product product = createProduct(company);
        ProductReview review = createReview(reviewer, product);

        mockMvc.perform(post("/admin/reviews/" + review.getId() + "/moderate")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("HIDE")))
                .andExpect(status().isNoContent());

        assertEquals(ReviewStatus.HIDDEN,
                reviewRepository.findById(review.getId()).orElseThrow().getStatus());
    }

    @Test
    void moderate_remove_returns204AndPersistsStatus() throws Exception {
        User admin = createAdmin("ra-admin-rem@example.com");
        User reviewer = createActiveUser("ra-reviewer-rem@example.com", "Password1!");
        Company company = createCompany(admin);
        Product product = createProduct(company);
        ProductReview review = createReview(reviewer, product);

        mockMvc.perform(post("/admin/reviews/" + review.getId() + "/moderate")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("REMOVE")))
                .andExpect(status().isNoContent());

        ProductReview updated = reviewRepository.findById(review.getId()).orElseThrow();
        assertEquals(ReviewStatus.REMOVED, updated.getStatus());
    }

    @Test
    void moderate_unknownReview_returns404() throws Exception {
        User admin = createAdmin("ra-admin-404@example.com");

        mockMvc.perform(post("/admin/reviews/" + UUID.randomUUID() + "/moderate")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("PUBLISH")))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderate_missingAction_returns400() throws Exception {
        User admin = createAdmin("ra-admin-400@example.com");

        mockMvc.perform(post("/admin/reviews/" + UUID.randomUUID() + "/moderate")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderate_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("ra-user-403@example.com", "Password1!");

        mockMvc.perform(post("/admin/reviews/" + UUID.randomUUID() + "/moderate")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("HIDE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderate_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/reviews/" + UUID.randomUUID() + "/moderate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("PUBLISH")))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /admin/reviews/reindex ────────────────────────────────────────────

    @Test
    void reindex_admin_returns200() throws Exception {
        User admin = createAdmin("ra-admin-reindex@example.com");

        mockMvc.perform(post("/admin/reviews/reindex")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Review index rollover and full reindex triggered"));
    }

    @Test
    void reindex_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("ra-user-reindex@example.com", "Password1!");

        mockMvc.perform(post("/admin/reviews/reindex")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reindex_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/reviews/reindex"))
                .andExpect(status().isUnauthorized());
    }
}
