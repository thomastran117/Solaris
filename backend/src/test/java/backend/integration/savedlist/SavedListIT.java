package backend.integration.savedlist;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SavedListIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;

    private static final String BASE = "/saved-lists";

    @AfterEach
    void cleanSavedLists() {
        try { jdbcTemplate.execute("DELETE FROM saved_list_items"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM saved_lists"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM product_change_log"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM products"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Product createProduct(User owner) {
        Company company = new Company();
        company.setOwner(owner);
        company.setName("Prod Co " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        Product p = new Product();
        p.setCompany(company);
        p.setName("Test Product " + UUID.randomUUID().toString().substring(0, 8));
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(new BigDecimal("19.99"));
        p.setCurrency("USD");
        p.setStock(10);
        p.setStatus(ProductStatus.ACTIVE);
        p.setListed(true);
        p.setPurchasable(true);
        return productRepository.save(p);
    }

    private String createBody(String name, String type) throws Exception {
        return objectMapper.writeValueAsString(Map.of("name", name, "type", type));
    }

    private String createPublicBody(String name, String type) throws Exception {
        return objectMapper.writeValueAsString(Map.of("name", name, "type", type, "public", true));
    }

    private UUID createListViaApi(User user, String name, String type) throws Exception {
        String response = mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    private UUID addItemViaApi(User user, UUID listId, UUID productId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("productId", productId.toString()));
        String response = mockMvc.perform(post(BASE + "/" + listId + "/items")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    // ── GET /saved-lists ──────────────────────────────────────────────────────

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
    void list_withTypeFilter_returnsMatchingOnly() throws Exception {
        User user = createActiveUser("list-filter@example.com", "Password1!");
        createListViaApi(user, "My Wishlist", "WISHLIST");
        createListViaApi(user, "My Shopping", "SHOPPING");

        mockMvc.perform(get(BASE)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get(BASE).param("type", "WISHLIST")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("My Wishlist"));

        mockMvc.perform(get(BASE).param("type", "SHOPPING")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /saved-lists/{id} ─────────────────────────────────────────────────

    @Test
    void getList_returns200WithItems() throws Exception {
        User user = createActiveUser("getlist@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");

        mockMvc.perform(get(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(listId.toString()))
                .andExpect(jsonPath("$.data.name").value("My List"))
                .andExpect(jsonPath("$.data.type").value("WISHLIST"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void getList_differentUsersListReturns404() throws Exception {
        User owner = createActiveUser("getlist-owner@example.com", "Password1!");
        User other = createActiveUser("getlist-other@example.com", "Password1!");
        UUID listId = createListViaApi(owner, "Owners List", "WISHLIST");

        mockMvc.perform(get(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getList_unknownList_returns404() throws Exception {
        User user = createActiveUser("getlist-404@example.com", "Password1!");

        mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getList_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /saved-lists/public/{slug} ────────────────────────────────────────

    @Test
    void getPublicList_returns200() throws Exception {
        User user = createActiveUser("public-list@example.com", "Password1!");
        String body = createPublicBody("My Public List", "WISHLIST");
        String response = mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String shareSlug = objectMapper.readTree(response).path("data").path("shareSlug").asText();

        mockMvc.perform(get(BASE + "/public/" + shareSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("My Public List"))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void getPublicList_unknownSlug_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/public/unknown-slug-xyz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicList_privateList_returns404() throws Exception {
        User user = createActiveUser("private-list@example.com", "Password1!");
        // Create public, then make private — slug should no longer be accessible
        String pubBody = createPublicBody("Then Private", "WISHLIST");
        String createResp = mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(createResp).path("data");
        UUID listId = UUID.fromString(node.path("id").asText());
        String shareSlug = node.path("shareSlug").asText();

        // Make it private
        String updateBody = objectMapper.writeValueAsString(Map.of("isPublic", false));
        mockMvc.perform(put(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        // Slug should no longer work
        mockMvc.perform(get(BASE + "/public/" + shareSlug))
                .andExpect(status().isNotFound());
    }

    // ── POST /saved-lists ─────────────────────────────────────────────────────

    @Test
    void create_returns201WithFields() throws Exception {
        User user = createActiveUser("create-ok@example.com", "Password1!");

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Holiday List", "GIFT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("Holiday List"))
                .andExpect(jsonPath("$.data.type").value("GIFT"))
                .andExpect(jsonPath("$.data.public").value(false))
                .andExpect(jsonPath("$.data.shareSlug").doesNotExist())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saved_lists WHERE name = 'Holiday List'", Integer.class);
        assertEquals(1, rows, "Saved list should be persisted");
    }

    @Test
    void create_publicList_hasShareSlug() throws Exception {
        User user = createActiveUser("create-public@example.com", "Password1!");

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPublicBody("Shared List", "WISHLIST")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.public").value(true))
                .andExpect(jsonPath("$.data.shareSlug").isNotEmpty());
    }

    @Test
    void create_missingName_returns400() throws Exception {
        User user = createActiveUser("create-noname@example.com", "Password1!");

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "WISHLIST"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingType_returns400() throws Exception {
        User user = createActiveUser("create-notype@example.com", "Password1!");

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "No Type List"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateNameAndType_returns409() throws Exception {
        User user = createActiveUser("create-dup@example.com", "Password1!");
        createListViaApi(user, "My Wishlist", "WISHLIST");

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("My Wishlist", "WISHLIST")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_freeTierLimit_returns402() throws Exception {
        User user = createActiveUser("create-limit@example.com", "Password1!");
        for (int i = 0; i < 5; i++) {
            createListViaApi(user, "List " + i, "WISHLIST");
        }

        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("List 6", "SHOPPING")))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Unauth List", "WISHLIST")))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /saved-lists/{id} ─────────────────────────────────────────────────

    @Test
    void update_renameList_returns200() throws Exception {
        User user = createActiveUser("update-rename@example.com", "Password1!");
        UUID listId = createListViaApi(user, "Old Name", "WISHLIST");

        String body = objectMapper.writeValueAsString(Map.of("name", "New Name"));
        mockMvc.perform(put(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"));

        assertEquals("New Name",
                jdbcTemplate.queryForObject("SELECT name FROM saved_lists", String.class));
    }

    @Test
    void update_makePublic_addsShareSlug() throws Exception {
        User user = createActiveUser("update-public@example.com", "Password1!");
        UUID listId = createListViaApi(user, "Soon Public", "WISHLIST");

        String body = objectMapper.writeValueAsString(Map.of("isPublic", true));
        mockMvc.perform(put(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.public").value(true))
                .andExpect(jsonPath("$.data.shareSlug").isNotEmpty());
    }

    @Test
    void update_makePrivate_removesShareSlug() throws Exception {
        User user = createActiveUser("update-private@example.com", "Password1!");
        String response = mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPublicBody("Was Public", "WISHLIST")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID listId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());

        String body = objectMapper.writeValueAsString(Map.of("isPublic", false));
        mockMvc.perform(put(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.public").value(false))
                .andExpect(jsonPath("$.data.shareSlug").value(nullValue()));
    }

    @Test
    void update_duplicateName_returns409() throws Exception {
        User user = createActiveUser("update-dup@example.com", "Password1!");
        createListViaApi(user, "Existing", "WISHLIST");
        UUID listId = createListViaApi(user, "Other", "WISHLIST");

        String body = objectMapper.writeValueAsString(Map.of("name", "Existing"));
        mockMvc.perform(put(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void update_differentUsersListReturns404() throws Exception {
        User owner = createActiveUser("update-owner@example.com", "Password1!");
        User other = createActiveUser("update-other@example.com", "Password1!");
        UUID listId = createListViaApi(owner, "Owners List", "WISHLIST");

        mockMvc.perform(put(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hijacked"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put(BASE + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /saved-lists/{id} ──────────────────────────────────────────────

    @Test
    void delete_returns204() throws Exception {
        User user = createActiveUser("delete-ok@example.com", "Password1!");
        UUID listId = createListViaApi(user, "To Delete", "WISHLIST");

        mockMvc.perform(delete(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());

        // Gone after delete
        mockMvc.perform(get(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_differentUsersListReturns404() throws Exception {
        User owner = createActiveUser("delete-owner@example.com", "Password1!");
        User other = createActiveUser("delete-other@example.com", "Password1!");
        UUID listId = createListViaApi(owner, "Owners List", "WISHLIST");

        mockMvc.perform(delete(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /saved-lists/{id}/items ──────────────────────────────────────────

    @Test
    void addItem_returns201WithFields() throws Exception {
        User user = createActiveUser("additem-ok@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");
        Product product = createProduct(user);

        mockMvc.perform(post(BASE + "/" + listId + "/items")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.data.productName").value(product.getName()))
                .andExpect(jsonPath("$.data.quantity").value(1))
                .andExpect(jsonPath("$.data.purchased").value(false));

        Integer itemRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saved_list_items", Integer.class);
        assertEquals(1, itemRows, "Saved list item should be persisted");
    }

    @Test
    void addItem_itemAppearsInList() throws Exception {
        User user = createActiveUser("additem-appears@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");
        Product product = createProduct(user);
        addItemViaApi(user, listId, product.getId());

        mockMvc.perform(get(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productId").value(product.getId().toString()));
    }

    @Test
    void addItem_duplicateProduct_returns409() throws Exception {
        User user = createActiveUser("additem-dup@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");
        Product product = createProduct(user);
        addItemViaApi(user, listId, product.getId());

        mockMvc.perform(post(BASE + "/" + listId + "/items")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()))))
                .andExpect(status().isConflict());
    }

    @Test
    void addItem_unknownProduct_returns404() throws Exception {
        User user = createActiveUser("additem-404prod@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");

        mockMvc.perform(post(BASE + "/" + listId + "/items")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_missingProductId_returns400() throws Exception {
        User user = createActiveUser("additem-400@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");

        mockMvc.perform(post(BASE + "/" + listId + "/items")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_differentUsersListReturns404() throws Exception {
        User owner = createActiveUser("additem-owner@example.com", "Password1!");
        User other = createActiveUser("additem-other@example.com", "Password1!");
        UUID listId = createListViaApi(owner, "Owners List", "WISHLIST");
        Product product = createProduct(owner);

        mockMvc.perform(post(BASE + "/" + listId + "/items")
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", product.getId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /saved-lists/{id}/items/{itemId} ────────────────────────────────

    @Test
    void updateItem_setsQuantityAndNote_returns200() throws Exception {
        User user = createActiveUser("upditem-ok@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");
        Product product = createProduct(user);
        UUID itemId = addItemViaApi(user, listId, product.getId());

        String body = objectMapper.writeValueAsString(Map.of("quantity", 3, "note", "for the party"));
        mockMvc.perform(patch(BASE + "/" + listId + "/items/" + itemId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.note").value("for the party"));
    }

    @Test
    void updateItem_marksPurchased_returns200() throws Exception {
        User user = createActiveUser("upditem-purchased@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "SHOPPING");
        Product product = createProduct(user);
        UUID itemId = addItemViaApi(user, listId, product.getId());

        String body = objectMapper.writeValueAsString(Map.of("purchased", true));
        mockMvc.perform(patch(BASE + "/" + listId + "/items/" + itemId)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchased").value(true))
                .andExpect(jsonPath("$.data.purchasedAt").isNotEmpty());
    }

    @Test
    void updateItem_unknownItem_returns404() throws Exception {
        User user = createActiveUser("upditem-404@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");

        mockMvc.perform(patch(BASE + "/" + listId + "/items/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_differentUsersListReturns404() throws Exception {
        User owner = createActiveUser("upditem-owner@example.com", "Password1!");
        User other = createActiveUser("upditem-other@example.com", "Password1!");
        UUID listId = createListViaApi(owner, "Owners List", "WISHLIST");
        Product product = createProduct(owner);
        UUID itemId = addItemViaApi(owner, listId, product.getId());

        mockMvc.perform(patch(BASE + "/" + listId + "/items/" + itemId)
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 5))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch(BASE + "/" + UUID.randomUUID() + "/items/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /saved-lists/{id}/items/{itemId} ───────────────────────────────

    @Test
    void removeItem_returns204_andItemGone() throws Exception {
        User user = createActiveUser("rmitem-ok@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");
        Product product = createProduct(user);
        UUID itemId = addItemViaApi(user, listId, product.getId());

        mockMvc.perform(delete(BASE + "/" + listId + "/items/" + itemId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNoContent());

        // Item should be gone from the list
        mockMvc.perform(get(BASE + "/" + listId)
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void removeItem_unknownItem_returns404() throws Exception {
        User user = createActiveUser("rmitem-404@example.com", "Password1!");
        UUID listId = createListViaApi(user, "My List", "WISHLIST");

        mockMvc.perform(delete(BASE + "/" + listId + "/items/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItem_differentUsersListReturns404() throws Exception {
        User owner = createActiveUser("rmitem-owner@example.com", "Password1!");
        User other = createActiveUser("rmitem-other@example.com", "Password1!");
        UUID listId = createListViaApi(owner, "Owners List", "WISHLIST");
        Product product = createProduct(owner);
        UUID itemId = addItemViaApi(owner, listId, product.getId());

        mockMvc.perform(delete(BASE + "/" + listId + "/items/" + itemId)
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItem_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID() + "/items/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
