package backend.dtos.responses.search;

import java.util.List;

public record SearchSuggestionsResponse(
        List<ProductSuggestion> products,
        List<String> categories,
        List<String> brands
) {
    public SearchSuggestionsResponse {
        products   = products   != null ? products   : List.of();
        categories = categories != null ? categories : List.of();
        brands     = brands     != null ? brands     : List.of();
    }
}
