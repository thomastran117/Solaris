package backend.controllers.impl.company;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.search.SearchSuggestionsResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.RateLimitService;
import backend.services.intf.search.SearchSuggestionService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanySearchSuggestionControllerTest {

    private SearchSuggestionService searchSuggestionService;
    private RateLimitService rateLimitService;
    private MockMvc mockMvc;

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    // MockMvc sets remoteAddr to "127.0.0.1" by default
    private static final String EXPECTED_RATE_KEY = "127.0.0.1:" + COMPANY_ID;

    @BeforeEach
    void setUp() {
        searchSuggestionService = mock(SearchSuggestionService.class);
        rateLimitService        = mock(RateLimitService.class);

        CompanySearchSuggestionController controller =
                new CompanySearchSuggestionController(searchSuggestionService, rateLimitService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── GET /companies/{companyId}/catalog/search/suggestions ──────────────

    @Test
    void getSuggestions_returns200WithSuggestions() throws Exception {
        SearchSuggestionsResponse resp = new SearchSuggestionsResponse(
                List.of("Widget Pro"), List.of("Electronics"), List.of("Acme"));
        when(searchSuggestionService.getCompanySuggestions(eq(COMPANY_ID), eq("wid"), eq(8)))
                .thenReturn(resp);

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                        .param("q", "wid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productNames[0]").value("Widget Pro"))
                .andExpect(jsonPath("$.categories[0]").value("Electronics"))
                .andExpect(jsonPath("$.brands[0]").value("Acme"));
    }

    @Test
    void getSuggestions_queryTooShort_returns400() throws Exception {
        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                        .param("q", "a"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchSuggestionService);
    }

    @Test
    void getSuggestions_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                        .param("q", "   "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchSuggestionService);
    }

    @Test
    void getSuggestions_enforces_rateLimitBeforeCallingService() throws Exception {
        when(searchSuggestionService.getCompanySuggestions(any(), any(), anyInt()))
                .thenReturn(new SearchSuggestionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                .param("q", "laptop"));

        verify(rateLimitService).enforce(
                eq("search:suggest:company"), eq(EXPECTED_RATE_KEY), eq(60), eq(60));
    }

    @Test
    void getSuggestions_rateLimitKeyedByIpAndCompanyId() throws Exception {
        when(searchSuggestionService.getCompanySuggestions(any(), any(), anyInt()))
                .thenReturn(new SearchSuggestionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                .param("q", "laptop"));

        // Key must embed companyId so two stores don't share one rate budget per IP
        verify(rateLimitService).enforce(anyString(), contains(COMPANY_ID.toString()), anyInt(), anyInt());
    }

    @Test
    void getSuggestions_customLimit_passedToService() throws Exception {
        when(searchSuggestionService.getCompanySuggestions(eq(COMPANY_ID), eq("cam"), eq(5)))
                .thenReturn(new SearchSuggestionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                        .param("q", "cam")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(searchSuggestionService).getCompanySuggestions(COMPANY_ID, "cam", 5);
    }

    @Test
    void getSuggestions_defaultLimit_is8() throws Exception {
        when(searchSuggestionService.getCompanySuggestions(any(), any(), anyInt()))
                .thenReturn(new SearchSuggestionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                .param("q", "cam"));

        verify(searchSuggestionService).getCompanySuggestions(eq(COMPANY_ID), eq("cam"), eq(8));
    }

    @Test
    void getSuggestions_serviceThrowsAppHttpException_propagates404() throws Exception {
        when(searchSuggestionService.getCompanySuggestions(any(), any(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Company not found"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                        .param("q", "laptop"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSuggestions_unexpectedServiceException_returns500() throws Exception {
        when(searchSuggestionService.getCompanySuggestions(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("ES cluster down"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search/suggestions")
                        .param("q", "laptop"))
                .andExpect(status().isInternalServerError());
    }
}
