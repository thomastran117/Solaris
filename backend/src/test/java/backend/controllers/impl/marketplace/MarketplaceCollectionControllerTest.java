package backend.controllers.impl.marketplace;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.collection.CollectionProductResponse;
import backend.dtos.responses.collection.CollectionResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.collections.CollectionService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketplaceCollectionControllerTest {

    private CollectionService collectionService;
    private MockMvc mockMvc;

    private static final UUID MARKETPLACE_ID = TestIds.uuid(1);
    private static final UUID VENDOR_ID      = TestIds.uuid(2);
    private static final UUID COLL_ID        = TestIds.uuid(3);
    private static final UUID PRODUCT_ID     = TestIds.uuid(4);

    @BeforeEach
    void setUp() {
        collectionService = mock(CollectionService.class);
        MarketplaceCollectionController controller =
                new MarketplaceCollectionController(collectionService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    // ─── GET /marketplaces/{marketplaceId}/collections/featured ──────────────

    @Test
    void listFeatured_returns200WithCollections() throws Exception {
        CollectionResponse resp = makeCollectionResponse(COLL_ID);
        when(collectionService.listFeaturedForMarketplace(MARKETPLACE_ID)).thenReturn(List.of(resp));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/collections/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(COLL_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Featured Collection"));
    }

    @Test
    void listFeatured_emptyList_returns200() throws Exception {
        when(collectionService.listFeaturedForMarketplace(MARKETPLACE_ID)).thenReturn(List.of());

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/collections/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listFeatured_marketplaceNotFound_returns404() throws Exception {
        when(collectionService.listFeaturedForMarketplace(MARKETPLACE_ID))
                .thenThrow(new ResourceNotFoundException("Marketplace not found"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/collections/featured"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /marketplaces/{marketplaceId}/collections/featured/vendor/{vendorId}

    @Test
    void listFeaturedForVendor_returns200() throws Exception {
        CollectionResponse resp = makeCollectionResponse(COLL_ID);
        when(collectionService.listFeaturedForVendor(MARKETPLACE_ID, VENDOR_ID))
                .thenReturn(List.of(resp));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID
                        + "/collections/featured/vendor/" + VENDOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(COLL_ID.toString()));
    }

    @Test
    void listFeaturedForVendor_marketplaceNotFound_returns404() throws Exception {
        when(collectionService.listFeaturedForVendor(any(), any()))
                .thenThrow(new ResourceNotFoundException("Marketplace not found"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID
                        + "/collections/featured/vendor/" + VENDOR_ID))
                .andExpect(status().isNotFound());
    }

    // ─── GET /marketplaces/{marketplaceId}/collections/{slug} ────────────────

    @Test
    void getBySlug_returns200() throws Exception {
        CollectionResponse resp = makeCollectionResponse(COLL_ID);
        when(collectionService.getCollectionBySlug(MARKETPLACE_ID, "summer-sale")).thenReturn(resp);

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/collections/summer-sale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COLL_ID.toString()));
    }

    @Test
    void getBySlug_notFound_returns404() throws Exception {
        when(collectionService.getCollectionBySlug(any(), any()))
                .thenThrow(new ResourceNotFoundException("Collection not found"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/collections/no-such-slug"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBySlug_marketplaceNotFound_returns404() throws Exception {
        when(collectionService.getCollectionBySlug(any(), any()))
                .thenThrow(new ResourceNotFoundException("Marketplace not found"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/collections/any-slug"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /marketplaces/{marketplaceId}/collections/{slug}/products ────────

    @Test
    void listProductsBySlug_returns200() throws Exception {
        CollectionProductResponse pr = makeProductResponse(TestIds.uuid(10), COLL_ID, PRODUCT_ID);
        when(collectionService.listMarketplaceCollectionProducts(
                eq(MARKETPLACE_ID), eq("summer-sale"), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(
                        new PageImpl<>(List.of(pr), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID
                        + "/collections/summer-sale/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID.toString()));
    }

    @Test
    void listProductsBySlug_notFound_returns404() throws Exception {
        when(collectionService.listMarketplaceCollectionProducts(any(), any(), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Collection not found"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID
                        + "/collections/no-such-slug/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProductsBySlug_defaultPageParams_passedToService() throws Exception {
        when(collectionService.listMarketplaceCollectionProducts(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of())));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/collections/slug/products"))
                .andExpect(status().isOk());

        verify(collectionService).listMarketplaceCollectionProducts(
                eq(MARKETPLACE_ID), eq("slug"), eq(0), eq(20));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CollectionResponse makeCollectionResponse(UUID id) {
        return new CollectionResponse(
                id, TestIds.uuid(99), "Featured Collection", "featured-slug",
                null, null, "STATIC", "ACTIVE",
                true, 1, null, 5L, null, null, null);
    }

    private CollectionProductResponse makeProductResponse(UUID id, UUID collectionId, UUID productId) {
        return new CollectionProductResponse(
                id, collectionId, productId, "Cool Product", "SKU-X",
                null, BigDecimal.valueOf(29.99), "USD", "ACTIVE",
                null, null, "AUTO", null, null);
    }
}
