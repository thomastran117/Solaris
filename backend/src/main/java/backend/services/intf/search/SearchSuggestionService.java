package backend.services.intf.search;

import backend.dtos.responses.search.SearchSuggestionsResponse;

public interface SearchSuggestionService {
    SearchSuggestionsResponse getSuggestions(long marketplaceId, String q, int limit);
}
