package backend.integration.search;

import backend.integration.AbstractIntegrationIT;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers SearchSuggestionController (/marketplaces/{id}/catalog/search/suggestions)
 * and CompanySearchSuggestionController (/companies/{id}/catalog/search/suggestions).
 *
 * ElasticsearchOperations is mocked; the service catches the resulting NPE and returns
 * empty suggestions, so valid queries always yield a 200 with empty lists.
 */
class SearchSuggestionIT extends AbstractIntegrationIT {

    // No DB rows needed — service falls back to empty response on ES failure

    // ── /marketplaces/{id}/catalog/search/suggestions ─────────────────────────

    @Test
    void marketplace_validQuery_returns200WithEmptyLists() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.brands").isArray());
    }

    @Test
    void marketplace_validQueryWithLimit_returns200() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "running shoes")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isArray());
    }

    @Test
    void marketplace_singleCharQuery_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marketplace_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marketplace_luceneSpecialCharsStripped_returns200() throws Exception {
        // Query with Lucene operators — service strips them and still returns 200
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "shoe^boost"))
                .andExpect(status().isOk());
    }

    // ── /companies/{id}/catalog/search/suggestions ────────────────────────────

    @Test
    void company_validQuery_returns200WithEmptyLists() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "jacket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.brands").isArray());
    }

    @Test
    void company_validQueryWithLimit_returns200() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "winter coat")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isArray());
    }

    @Test
    void company_singleCharQuery_returns400() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void company_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void company_luceneSpecialCharsStripped_returns200() throws Exception {
        mockMvc.perform(get("/companies/" + UUID.randomUUID() + "/catalog/search/suggestions")
                        .param("q", "(boots)"))
                .andExpect(status().isOk());
    }
}
