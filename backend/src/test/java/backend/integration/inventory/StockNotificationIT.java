package backend.integration.inventory;

import backend.integration.AbstractIntegrationIT;
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
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StockNotificationIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;

    private static final String BASE = "/stock-notifications";

    @AfterEach
    void cleanNotifications() {
        try { jdbcTemplate.execute("DELETE FROM stock_notifications"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Product createProduct() {
        User owner = createActiveUser("prod-owner-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", "Password1!");
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Notif Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        Company company = companyRepository.save(c);

        Product p = new Product();
        p.setCompany(company);
        p.setName("Notif Product " + UUID.randomUUID().toString().substring(0, 8));
        p.setSku("NOTIF-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("29.99"));
        p.setCurrency("USD");
        p.setStock(0);
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        return productRepository.save(p);
    }

    private UUID subscribeViaApi(User user, UUID productId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("productId", productId.toString()));
        String response = mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    // ── POST /stock-notifications ─────────────────────────────────────────────

    @Test
    void subscribe_returns200WithFields() throws Exception {
        User user = createActiveUser("sub-ok@example.com", "Password1!");
        Product product = createProduct();

        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.productName").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.variantId").doesNotExist());
    }

    @Test
    void subscribe_idempotent_reactivatesExisting() throws Exception {
        User user = createActiveUser("sub-idem@example.com", "Password1!");
        Product product = createProduct();

        // First subscribe
        UUID firstId = subscribeViaApi(user, product.getId());

        // Subscribe again to the same product — should return the same record, re-activated
        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        String response = mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        UUID secondId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
        // Same DB record re-used (upsert, not duplicate)
        assert firstId.equals(secondId) : "Expected same notification ID on re-subscribe";
    }

    @Test
    void subscribe_missingProductId_returns400() throws Exception {
        User user = createActiveUser("sub-noprod@example.com", "Password1!");

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subscribe_unknownProduct_returns404() throws Exception {
        User user = createActiveUser("sub-404prod@example.com", "Password1!");

        String body = objectMapper.writeValueAsString(Map.of("productId", UUID.randomUUID().toString()));
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void subscribe_unauthenticated_returns401() throws Exception {
        Product product = createProduct();

        String body = objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()));
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /stock-notifications/{notificationId} ──────────────────────────

    @Test
    void cancel_returns204() throws Exception {
        User user = createActiveUser("cancel-ok@example.com", "Password1!");
        Product product = createProduct();
        UUID notifId = subscribeViaApi(user, product.getId());

        mockMvc.perform(delete(BASE + "/" + notifId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancel_unknownNotification_returns404() throws Exception {
        User user = createActiveUser("cancel-404@example.com", "Password1!");

        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_differentUsersNotification_returns403() throws Exception {
        User owner = createActiveUser("cancel-owner@example.com", "Password1!");
        User other = createActiveUser("cancel-other@example.com", "Password1!");
        Product product = createProduct();
        UUID notifId = subscribeViaApi(owner, product.getId());

        mockMvc.perform(delete(BASE + "/" + notifId)
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /stock-notifications ──────────────────────────────────────────────

    @Test
    void list_emptyForNewUser_returns200() throws Exception {
        User user = createActiveUser("list-empty@example.com", "Password1!");

        mockMvc.perform(get(BASE)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void list_afterSubscribe_returnsNotification() throws Exception {
        User user = createActiveUser("list-after@example.com", "Password1!");
        Product product = createProduct();
        subscribeViaApi(user, product.getId());

        mockMvc.perform(get(BASE)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void list_cancelledNotificationsExcluded() throws Exception {
        User user = createActiveUser("list-cancelled@example.com", "Password1!");
        Product product = createProduct();
        UUID notifId = subscribeViaApi(user, product.getId());

        mockMvc.perform(delete(BASE + "/" + notifId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());

        // Cancelled notifications are excluded from list
        mockMvc.perform(get(BASE)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }
}
