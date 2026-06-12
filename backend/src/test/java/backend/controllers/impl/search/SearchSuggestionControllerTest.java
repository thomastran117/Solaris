package backend.controllers.impl.search;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.search.ProductSuggestion;
import backend.dtos.responses.search.SearchSuggestionsResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.RateLimitService;
import backend.services.intf.search.SearchSuggestionService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchSuggestionControllerTest {

    private SearchSuggestionService searchSuggestionService;
    private RateLimitService rateLimitService;
    private MockMvc mockMvc;

    private static final UUID MARKETPLACE_ID = TestIds.uuid(1);
    // MockMvc sets remoteAddr to "127.0.0.1" by default
    private static final String CLIENT_IP = "127.0.0.1";

    @BeforeEach
    void setUp() {
        searchSuggestionService = mock(SearchSuggestionService.class);
        rateLimitService        = mock(RateLimitService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SearchSuggestionController(searchSuggestionService, rateLimitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    // ─── GET /marketplaces/{marketplaceId}/catalog/search/suggestions ─────────

    @Test
    void getSuggestions_returns200WithSuggestions() throws Exception {
        ProductSuggestion product = new ProductSuggestion(
                UUID.randomUUID(), "Widget Pro", new BigDecimal("29.99"), null, null);
        SearchSuggestionsResponse resp = new SearchSuggestionsResponse(
                List.of(product), List.of("Electronics"), List.of("Acme"));
        when(searchSuggestionService.getSuggestions(eq(MARKETPLACE_ID), eq("wid"), eq(8)))
                .thenReturn(resp);

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                        .param("q", "wid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].name").value("Widget Pro"))
                .andExpect(jsonPath("$.products[0].price").value(29.99))
                .andExpect(jsonPath("$.categories[0]").value("Electronics"))
                .andExpect(jsonPath("$.brands[0]").value("Acme"));
    }

    @Test
    void getSuggestions_queryTooShort_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                        .param("q", "a"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchSuggestionService);
    }

    @Test
    void getSuggestions_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                        .param("q", "   "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchSuggestionService);
    }

    @Test
    void getSuggestions_enforces_rateLimitBeforeCallingService() throws Exception {
        when(searchSuggestionService.getSuggestions(any(), any(), anyInt()))
                .thenReturn(new SearchSuggestionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                .param("q", "laptop"));

        verify(rateLimitService).enforce(
                eq("search:suggest"), eq(CLIENT_IP), eq(60), eq(60));
    }

    @Test
    void getSuggestions_rateLimitKeyedByIpOnly_notByMarketplace() throws Exception {
        when(searchSuggestionService.getSuggestions(any(), any(), anyInt()))
                .thenReturn(new SearchSuggestionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                .param("q", "laptop"));

        // Rate key is plain IP — shared across all marketplaces per client
        verify(rateLimitService).enforce(anyString(), eq(CLIENT_IP), anyInt(), anyInt());
    }

    @Test
    void getSuggestions_customLimit_passedToService() throws Exception {
        when(searchSuggestionService.getSuggestions(eq(MARKETPLACE_ID), eq("cam"), eq(5)))
                .thenReturn(new SearchSuggestionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                        .param("q", "cam")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(searchSuggestionService).getSuggestions(MARKETPLACE_ID, "cam", 5);
    }

    @Test
    void getSuggestions_defaultLimit_is8() throws Exception {
        when(searchSuggestionService.getSuggestions(any(), any(), anyInt()))
                .thenReturn(new SearchSuggestionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                .param("q", "cam"));

        verify(searchSuggestionService).getSuggestions(eq(MARKETPLACE_ID), eq("cam"), eq(8));
    }

    @Test
    void getSuggestions_serviceThrowsAppHttpException_propagates404() throws Exception {
        when(searchSuggestionService.getSuggestions(any(), any(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Marketplace not found"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                        .param("q", "laptop"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSuggestions_unexpectedServiceException_returns500() throws Exception {
        when(searchSuggestionService.getSuggestions(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("ES cluster down"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/catalog/search/suggestions")
                        .param("q", "laptop"))
                .andExpect(status().isInternalServerError());
    }
}
