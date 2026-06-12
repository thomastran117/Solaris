package backend.controllers.impl.collections;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.collection.CollectionProductResponse;
import backend.dtos.responses.collection.CollectionResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.collections.CollectionService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollectionControllerTest {

    private CollectionService collectionService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID COLL_ID    = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID USER_ID    = TestIds.uuid(4);
    private static final UUID VENDOR_ID  = TestIds.uuid(5);

    @BeforeEach
    void setUp() {
        collectionService = mock(CollectionService.class);

        CollectionController controller = new CollectionController(collectionService);

        // Custom @SafeText / @SafeIdentifier validators use @Autowired SanitizationService,
        // which is null in standalone mode. Use a no-op validator so controller-layer routing
        // and exception-propagation tests are not blocked by DTO validation.
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /companies/{companyId}/collections ───────────────────────────────

    @Test
    void listCollections_returns200WithPagedResponse() throws Exception {
        CollectionResponse resp = makeCollectionResponse(COLL_ID, COMPANY_ID);
        when(collectionService.listCollections(eq(COMPANY_ID), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(makePagedResponse(List.of(resp)));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(COLL_ID.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Test Collection"));
    }

    @Test
    void listCollections_withTypeParam_passesTypeToService() throws Exception {
        when(collectionService.listCollections(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(makePagedResponse(List.of()));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/collections").param("type", "DYNAMIC"))
                .andExpect(status().isOk());

        verify(collectionService).listCollections(
                eq(COMPANY_ID),
                eq(backend.models.enums.CollectionType.DYNAMIC),
                isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void listCollections_withFeaturedParam_passesParamToService() throws Exception {
        when(collectionService.listCollections(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(makePagedResponse(List.of()));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/collections").param("featured", "true"))
                .andExpect(status().isOk());

        verify(collectionService).listCollections(
                eq(COMPANY_ID), isNull(), isNull(), eq(true), anyInt(), anyInt());
    }

    @Test
    void listCollections_serviceThrows404_returns404() throws Exception {
        when(collectionService.listCollections(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Company not found"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/collections"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /companies/{companyId}/collections/{collectionId} ────────────────

    @Test
    void getCollection_returns200() throws Exception {
        CollectionResponse resp = makeCollectionResponse(COLL_ID, COMPANY_ID);
        when(collectionService.getCollection(COMPANY_ID, COLL_ID)).thenReturn(resp);

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/collections/" + COLL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COLL_ID.toString()));
    }

    @Test
    void getCollection_notFound_returns404() throws Exception {
        when(collectionService.getCollection(any(), any()))
                .thenThrow(new ResourceNotFoundException("Collection not found"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/collections/" + COLL_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{companyId}/collections ──────────────────────────────

    @Test
    void createCollection_returns201() throws Exception {
        authenticateAs(USER_ID);
        CollectionResponse resp = makeCollectionResponse(COLL_ID, COMPANY_ID);
        when(collectionService.createCollection(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(resp);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Best Sellers", "best-sellers")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(COLL_ID.toString()));
    }

    @Test
    void createCollection_conflict_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(collectionService.createCollection(any(), any(), any()))
                .thenThrow(new ConflictException("A collection with this slug already exists"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Dup", "dup-slug")))
                .andExpect(status().isConflict());
    }

    @Test
    void createCollection_invalidRules_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(collectionService.createCollection(any(), any(), any()))
                .thenThrow(new BadRequestException("rulesJson is not valid JSON"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Dyn", "dyn-slug")))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /companies/{companyId}/collections/{collectionId} ─────────────

    @Test
    void updateCollection_returns200() throws Exception {
        authenticateAs(USER_ID);
        CollectionResponse resp = makeCollectionResponse(COLL_ID, COMPANY_ID);
        when(collectionService.updateCollection(eq(COMPANY_ID), eq(COLL_ID), eq(USER_ID), any()))
                .thenReturn(resp);

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/collections/" + COLL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COLL_ID.toString()));
    }

    @Test
    void updateCollection_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(collectionService.updateCollection(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Collection not found"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/collections/" + COLL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCollection_conflict_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(collectionService.updateCollection(any(), any(), any(), any()))
                .thenThrow(new ConflictException("Slug already exists"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/collections/" + COLL_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"taken-slug\"}"))
                .andExpect(status().isConflict());
    }

    // ─── DELETE /companies/{companyId}/collections/{collectionId} ────────────

    @Test
    void deleteCollection_returns204() throws Exception {
        authenticateAs(USER_ID);
        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/collections/" + COLL_ID))
                .andExpect(status().isNoContent());

        verify(collectionService).deleteCollection(COMPANY_ID, COLL_ID, USER_ID);
    }

    @Test
    void deleteCollection_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new ResourceNotFoundException("Collection not found"))
                .when(collectionService).deleteCollection(any(), any(), any());

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/collections/" + COLL_ID))
                .andExpect(status().isNotFound());
    }

    // ─── GET /companies/{companyId}/collections/{collectionId}/products ───────

    @Test
    void listCollectionProducts_returns200() throws Exception {
        CollectionProductResponse pr = makeProductResponse(TestIds.uuid(10), COLL_ID, PRODUCT_ID);
        when(collectionService.listCollectionProducts(eq(COMPANY_ID), eq(COLL_ID), anyInt(), anyInt()))
                .thenReturn(makeProductPagedResponse(List.of(pr)));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/collections/" + COLL_ID + "/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID.toString()));
    }

    @Test
    void listCollectionProducts_notFound_returns404() throws Exception {
        when(collectionService.listCollectionProducts(any(), any(), anyInt(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Collection not found"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/collections/" + COLL_ID + "/products"))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{companyId}/collections/{collectionId}/products ──────

    @Test
    void addProduct_returns201() throws Exception {
        authenticateAs(USER_ID);
        CollectionProductResponse pr = makeProductResponse(TestIds.uuid(10), COLL_ID, PRODUCT_ID);
        when(collectionService.addCollectionProduct(eq(COMPANY_ID), eq(COLL_ID), eq(USER_ID), any()))
                .thenReturn(pr);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/collections/" + COLL_ID + "/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", PRODUCT_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()));
    }

    @Test
    void addProduct_dynamicCollection_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(collectionService.addCollectionProduct(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("Dynamic collections derive their members from rules"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/collections/" + COLL_ID + "/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", PRODUCT_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addProduct_duplicate_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(collectionService.addCollectionProduct(any(), any(), any(), any()))
                .thenThrow(new ConflictException("Product is already in this collection"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/collections/" + COLL_ID + "/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", PRODUCT_ID))))
                .andExpect(status().isConflict());
    }

    // ─── PATCH /companies/{companyId}/collections/{collectionId}/products/{productId}

    @Test
    void updateProduct_returns200() throws Exception {
        authenticateAs(USER_ID);
        CollectionProductResponse pr = makeProductResponse(TestIds.uuid(10), COLL_ID, PRODUCT_ID);
        when(collectionService.updateCollectionProduct(
                eq(COMPANY_ID), eq(COLL_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(pr);

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/collections/" + COLL_ID
                        + "/products/" + PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boostWeight\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()));
    }

    @Test
    void updateProduct_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(collectionService.updateCollectionProduct(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Product is not in this collection"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/collections/" + COLL_ID
                        + "/products/" + PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /companies/{companyId}/collections/{collectionId}/products/{productId}

    @Test
    void removeProduct_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/collections/" + COLL_ID
                + "/products/" + PRODUCT_ID))
                .andExpect(status().isNoContent());

        verify(collectionService).removeCollectionProduct(COMPANY_ID, COLL_ID, PRODUCT_ID, USER_ID);
    }

    @Test
    void removeProduct_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new ResourceNotFoundException("Product is not in this collection"))
                .when(collectionService).removeCollectionProduct(any(), any(), any(), any());

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/collections/" + COLL_ID
                + "/products/" + PRODUCT_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{companyId}/collections/{collectionId}/refresh ───────

    @Test
    void refreshCollection_returns200() throws Exception {
        authenticateAs(USER_ID);
        CollectionResponse resp = makeCollectionResponse(COLL_ID, COMPANY_ID);
        when(collectionService.refreshCollection(eq(COMPANY_ID), eq(COLL_ID), eq(USER_ID)))
                .thenReturn(resp);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/collections/" + COLL_ID + "/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COLL_ID.toString()));
    }

    @Test
    void refreshCollection_staticCollection_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(collectionService.refreshCollection(any(), any(), any()))
                .thenThrow(new BadRequestException("Only dynamic collections can be refreshed"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/collections/" + COLL_ID + "/refresh"))
                .andExpect(status().isBadRequest());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private String createBody(String name, String slug) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("name", name, "slug", slug, "type", "STATIC"));
    }

    private CollectionResponse makeCollectionResponse(UUID id, UUID companyId) {
        return new CollectionResponse(
                id, companyId, "Test Collection", "test-slug",
                null, null, "STATIC", "ACTIVE",
                false, null, null, 0L, null, null, null);
    }

    private CollectionProductResponse makeProductResponse(UUID id, UUID collectionId, UUID productId) {
        return new CollectionProductResponse(
                id, collectionId, productId, "Product Name", "SKU-1",
                null, BigDecimal.TEN, "USD", "ACTIVE",
                null, null, "MANUAL", null, null);
    }

    private PagedResponse<CollectionResponse> makePagedResponse(List<CollectionResponse> items) {
        return new PagedResponse<>(new PageImpl<>(items, PageRequest.of(0, 20), items.size()));
    }

    private PagedResponse<CollectionProductResponse> makeProductPagedResponse(
            List<CollectionProductResponse> items) {
        return new PagedResponse<>(new PageImpl<>(items, PageRequest.of(0, 20), items.size()));
    }

    /** No-op validator: bypasses @SafeText / @SafeIdentifier which require a Spring bean. */
    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
