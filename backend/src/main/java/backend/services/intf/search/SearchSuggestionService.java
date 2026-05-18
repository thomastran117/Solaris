package backend.services.intf.search;

import backend.dtos.responses.search.SearchSuggestionsResponse;

import java.util.UUID;

public interface SearchSuggestionService {
    SearchSuggestionsResponse getSuggestions(UUID marketplaceId, String q, int limit);

    /** Same shape as {@link #getSuggestions} but scoped to a single company. */
    SearchSuggestionsResponse getCompanySuggestions(UUID companyId, String q, int limit);
}
