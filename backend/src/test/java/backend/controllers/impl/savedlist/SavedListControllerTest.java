package backend.controllers.impl.savedlist;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.savedlist.AddSavedListItemRequest;
import backend.dtos.requests.savedlist.CreateSavedListRequest;
import backend.dtos.requests.savedlist.UpdateSavedListItemRequest;
import backend.dtos.responses.savedlist.PublicSavedListResponse;
import backend.dtos.responses.savedlist.SavedListItemResponse;
import backend.dtos.responses.savedlist.SavedListResponse;
import backend.dtos.responses.savedlist.SavedListSummaryResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.SavedListType;
import backend.services.intf.savedlist.SavedListService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SavedListControllerTest {

    private static final UUID USER_ID   = TestIds.uuid(1);
    private static final UUID LIST_ID   = TestIds.uuid(2);
    private static final UUID ITEM_ID   = TestIds.uuid(3);
    private static final UUID PRODUCT_ID = TestIds.uuid(4);

    private SavedListService savedListService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        savedListService = mock(SavedListService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new SavedListController(savedListService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /saved-lists ─────────────────────────────────────────────────────

    @Test
    void listSavedLists_returns200() throws Exception {
        when(savedListService.listSavedLists(eq(USER_ID), isNull()))
                .thenReturn(List.of(makeSummaryResponse()));

        mockMvc.perform(get("/saved-lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My List"));
    }

    @Test
    void listSavedLists_withTypeFilter_passesTypeToService() throws Exception {
        when(savedListService.listSavedLists(eq(USER_ID), eq(SavedListType.WISHLIST)))
                .thenReturn(List.of());

        mockMvc.perform(get("/saved-lists").param("type", "WISHLIST"))
                .andExpect(status().isOk());

        verify(savedListService).listSavedLists(eq(USER_ID), eq(SavedListType.WISHLIST));
    }

    // ─── GET /saved-lists/{id} ────────────────────────────────────────────────

    @Test
    void getSavedList_returns200() throws Exception {
        when(savedListService.getSavedList(USER_ID, LIST_ID)).thenReturn(makeListResponse());

        mockMvc.perform(get("/saved-lists/{id}", LIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LIST_ID.toString()));
    }

    @Test
    void getSavedList_notFound_returns404() throws Exception {
        when(savedListService.getSavedList(any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/saved-lists/{id}", LIST_ID))
                .andExpect(status().isNotFound());
    }

    // ─── GET /saved-lists/public/{slug} ───────────────────────────────────────

    @Test
    void getPublicSavedList_noAuthRequired_returns200() throws Exception {
        SecurityContextHolder.clearContext(); // ensure no auth
        when(savedListService.getPublicSavedList("abc123")).thenReturn(makePublicResponse());

        mockMvc.perform(get("/saved-lists/public/{slug}", "abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerDisplayName").value("John D."));
    }

    @Test
    void getPublicSavedList_notFound_returns404() throws Exception {
        SecurityContextHolder.clearContext();
        when(savedListService.getPublicSavedList(anyString()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/saved-lists/public/{slug}", "unknown"))
                .andExpect(status().isNotFound());
    }

    // ─── POST /saved-lists ────────────────────────────────────────────────────

    @Test
    void createSavedList_returns201() throws Exception {
        when(savedListService.createSavedList(eq(USER_ID), any()))
                .thenReturn(makeListResponse());

        mockMvc.perform(post("/saved-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My List"));
    }

    @Test
    void createSavedList_duplicateName_returns409() throws Exception {
        when(savedListService.createSavedList(any(), any()))
                .thenThrow(new ConflictException("already exists"));

        mockMvc.perform(post("/saved-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isConflict());
    }

    // ─── PUT /saved-lists/{id} ────────────────────────────────────────────────

    @Test
    void updateSavedList_returns200() throws Exception {
        when(savedListService.updateSavedList(eq(USER_ID), eq(LIST_ID), any()))
                .thenReturn(makeListResponse());

        mockMvc.perform(put("/saved-lists/{id}", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateSavedList_notFound_returns404() throws Exception {
        when(savedListService.updateSavedList(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(put("/saved-lists/{id}", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSavedList_conflict_returns409() throws Exception {
        when(savedListService.updateSavedList(any(), any(), any()))
                .thenThrow(new ConflictException("name taken"));

        mockMvc.perform(put("/saved-lists/{id}", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Taken Name\"}"))
                .andExpect(status().isConflict());
    }

    // ─── DELETE /saved-lists/{id} ─────────────────────────────────────────────

    @Test
    void deleteSavedList_returns204() throws Exception {
        mockMvc.perform(delete("/saved-lists/{id}", LIST_ID))
                .andExpect(status().isNoContent());

        verify(savedListService).deleteSavedList(USER_ID, LIST_ID);
    }

    @Test
    void deleteSavedList_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("not found"))
                .when(savedListService).deleteSavedList(any(), any());

        mockMvc.perform(delete("/saved-lists/{id}", LIST_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /saved-lists/{id}/items ─────────────────────────────────────────

    @Test
    void addItem_returns201() throws Exception {
        when(savedListService.addItem(eq(USER_ID), eq(LIST_ID), any()))
                .thenReturn(makeItemResponse());

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);

        mockMvc.perform(post("/saved-lists/{id}/items", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void addItem_duplicate_returns409() throws Exception {
        when(savedListService.addItem(any(), any(), any()))
                .thenThrow(new ConflictException("already in list"));

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);

        mockMvc.perform(post("/saved-lists/{id}/items", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void addItem_productNotFound_returns404() throws Exception {
        when(savedListService.addItem(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("product not found"));

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);

        mockMvc.perform(post("/saved-lists/{id}/items", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_invalidVariant_returns400() throws Exception {
        when(savedListService.addItem(any(), any(), any()))
                .thenThrow(new BadRequestException("variant not in product"));

        AddSavedListItemRequest req = new AddSavedListItemRequest();
        req.setProductId(PRODUCT_ID);
        req.setVariantId(TestIds.uuid(99));

        mockMvc.perform(post("/saved-lists/{id}/items", LIST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /saved-lists/{id}/items/{itemId} ───────────────────────────────

    @Test
    void updateItem_returns200() throws Exception {
        when(savedListService.updateItem(eq(USER_ID), eq(LIST_ID), eq(ITEM_ID), any()))
                .thenReturn(makeItemResponse());

        UpdateSavedListItemRequest req = new UpdateSavedListItemRequest();
        req.setQuantity(3);

        mockMvc.perform(patch("/saved-lists/{id}/items/{itemId}", LIST_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateItem_itemNotFound_returns404() throws Exception {
        when(savedListService.updateItem(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("item not found"));

        mockMvc.perform(patch("/saved-lists/{id}/items/{itemId}", LIST_ID, ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /saved-lists/{id}/items/{itemId} ──────────────────────────────

    @Test
    void removeItem_returns204() throws Exception {
        mockMvc.perform(delete("/saved-lists/{id}/items/{itemId}", LIST_ID, ITEM_ID))
                .andExpect(status().isNoContent());

        verify(savedListService).removeItem(USER_ID, LIST_ID, ITEM_ID);
    }

    @Test
    void removeItem_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("item not found"))
                .when(savedListService).removeItem(any(), any(), any());

        mockMvc.perform(delete("/saved-lists/{id}/items/{itemId}", LIST_ID, ITEM_ID))
                .andExpect(status().isNotFound());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private SavedListSummaryResponse makeSummaryResponse() {
        return new SavedListSummaryResponse(
                LIST_ID, USER_ID, "My List", SavedListType.WISHLIST, null,
                false, null, 0, 0, Instant.now(), Instant.now());
    }

    private SavedListResponse makeListResponse() {
        return new SavedListResponse(
                LIST_ID, USER_ID, "My List", SavedListType.WISHLIST, null,
                false, null, List.of(), Instant.now(), Instant.now());
    }

    private PublicSavedListResponse makePublicResponse() {
        return new PublicSavedListResponse(
                LIST_ID, "John D.", "My List", SavedListType.WISHLIST, null,
                "abc123", List.of(), Instant.now(), Instant.now());
    }

    private SavedListItemResponse makeItemResponse() {
        return new SavedListItemResponse(
                ITEM_ID, PRODUCT_ID, "Test Product", null,
                null, null, 1, null, false, null, Instant.now());
    }

    private CreateSavedListRequest makeCreateRequest() {
        CreateSavedListRequest req = new CreateSavedListRequest();
        req.setName("My List");
        req.setType(SavedListType.WISHLIST);
        return req;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
