package backend.services.intf.search;

import backend.dtos.responses.search.SearchSuggestionsResponse;

public interface SearchSuggestionService {
    SearchSuggestionsResponse getSuggestions(long marketplaceId, String q, int limit);

    /** Same shape as {@link #getSuggestions} but scoped to a single company. */
    SearchSuggestionsResponse getCompanySuggestions(long companyId, String q, int limit);
}
