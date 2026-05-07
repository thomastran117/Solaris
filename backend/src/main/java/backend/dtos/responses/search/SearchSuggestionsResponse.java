package backend.dtos.responses.search;

import java.util.List;

public record SearchSuggestionsResponse(
        List<String> productNames,
        List<String> categories,
        List<String> brands
) {}
