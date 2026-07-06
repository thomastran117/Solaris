package backend.integration.products;

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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PriceWatchIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void cleanPriceWatch() {
        try { jdbcTemplate.execute("DELETE FROM price_watchers"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Product createProduct(User owner, BigDecimal price) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("PW Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        c = companyRepository.save(c);

        Product p = new Product();
        p.setCompany(c);
        p.setName("Watch Me " + UUID.randomUUID().toString().substring(0, 8));
        p.setSku("PW-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(price);
        p.setCurrency("USD");
        p.setStock(5);
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        return productRepository.save(p);
    }

    // ── POST /products/{productId}/price-watch ────────────────────────────────

    @Test
    void watch_returns200_createsWatcher() throws Exception {
        User user = createActiveUser("pw-watch-ok@example.com", "Password1!");
        Product product = createProduct(user, new BigDecimal("49.99"));

        mockMvc.perform(post("/products/" + product.getId() + "/price-watch")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.productName").value(product.getName()))
                .andExpect(jsonPath("$.data.watchPriceCents").value(4999));

        Long watchPrice = jdbcTemplate.queryForObject(
                "SELECT watch_price_cents FROM price_watchers", Long.class);
        assertEquals(4999L, watchPrice, "Watcher should be persisted at the product's price");
    }

    @Test
    void watch_secondCallUpdatesWatchPrice() throws Exception {
        User user = createActiveUser("pw-watch-update@example.com", "Password1!");
        Product product = createProduct(user, new BigDecimal("49.99"));
        String token = bearer(accessTokenFor(user));
        String path = "/products/" + product.getId() + "/price-watch";

        // First watch
        mockMvc.perform(post(path).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watchPriceCents").value(4999));

        // Update product price
        product.setPrice(new BigDecimal("39.99"));
        productRepository.save(product);

        // Second watch — should update watchPriceCents to new price
        mockMvc.perform(post(path).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watchPriceCents").value(3999));

        // The re-watch must update in place (one row) with the new price committed.
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM price_watchers", Integer.class);
        assertEquals(1, rows, "Re-watching should update the existing row, not create a second");
        Long watchPrice = jdbcTemplate.queryForObject(
                "SELECT watch_price_cents FROM price_watchers", Long.class);
        assertEquals(3999L, watchPrice);
    }

    @Test
    void watch_unknownProduct_returns404() throws Exception {
        User user = createActiveUser("pw-watch-404@example.com", "Password1!");

        mockMvc.perform(post("/products/" + UUID.randomUUID() + "/price-watch")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void watch_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/products/" + UUID.randomUUID() + "/price-watch"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /products/{productId}/price-watch ──────────────────────────────

    @Test
    void unwatch_existingWatch_returns204() throws Exception {
        User user = createActiveUser("pw-unwatch-ok@example.com", "Password1!");
        Product product = createProduct(user, new BigDecimal("29.99"));
        String token = bearer(accessTokenFor(user));

        // Create watch first
        mockMvc.perform(post("/products/" + product.getId() + "/price-watch")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        // Unwatch
        mockMvc.perform(delete("/products/" + product.getId() + "/price-watch")
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM price_watchers", Integer.class);
        assertEquals(0, remaining, "Unwatched row should be removed from the database");

        // Should no longer appear in list
        mockMvc.perform(get("/price-watches").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void unwatch_nonWatchedProduct_returns204Idempotent() throws Exception {
        User user = createActiveUser("pw-unwatch-idem@example.com", "Password1!");
        Product product = createProduct(user, new BigDecimal("19.99"));

        // Never watched — should still return 204 cleanly
        mockMvc.perform(delete("/products/" + product.getId() + "/price-watch")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());
    }

    @Test
    void unwatch_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/products/" + UUID.randomUUID() + "/price-watch"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /price-watches ────────────────────────────────────────────────────

    @Test
    void list_emptyForNewUser_returns200() throws Exception {
        User user = createActiveUser("pw-list-empty@example.com", "Password1!");

        mockMvc.perform(get("/price-watches")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void list_withWatches_returnsPage() throws Exception {
        User user = createActiveUser("pw-list-ok@example.com", "Password1!");
        Product p1 = createProduct(user, new BigDecimal("10.00"));
        Product p2 = createProduct(user, new BigDecimal("20.00"));
        String token = bearer(accessTokenFor(user));

        mockMvc.perform(post("/products/" + p1.getId() + "/price-watch").header("Authorization", token));
        mockMvc.perform(post("/products/" + p2.getId() + "/price-watch").header("Authorization", token));

        mockMvc.perform(get("/price-watches").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    void list_onlyReturnsOwnWatches() throws Exception {
        User userA = createActiveUser("pw-list-a@example.com", "Password1!");
        User userB = createActiveUser("pw-list-b@example.com", "Password1!");
        Product product = createProduct(userA, new BigDecimal("15.00"));

        // Both users watch the same product
        mockMvc.perform(post("/products/" + product.getId() + "/price-watch")
                .header("Authorization", bearer(accessTokenFor(userA))));
        mockMvc.perform(post("/products/" + product.getId() + "/price-watch")
                .header("Authorization", bearer(accessTokenFor(userB))));

        // Each user only sees their own entry
        mockMvc.perform(get("/price-watches")
                        .header("Authorization", bearer(accessTokenFor(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        mockMvc.perform(get("/price-watches")
                        .header("Authorization", bearer(accessTokenFor(userB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/price-watches"))
                .andExpect(status().isUnauthorized());
    }
}
