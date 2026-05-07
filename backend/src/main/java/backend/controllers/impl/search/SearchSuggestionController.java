package backend.controllers.impl.search;

import backend.dtos.responses.search.SearchSuggestionsResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.search.SearchSuggestionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/marketplaces/{marketplaceId}/catalog/search")
public class SearchSuggestionController {

    private final SearchSuggestionService searchSuggestionService;

    public SearchSuggestionController(SearchSuggestionService searchSuggestionService) {
        this.searchSuggestionService = searchSuggestionService;
    }

    @GetMapping("/suggestions")
    public ResponseEntity<SearchSuggestionsResponse> getSuggestions(
            @PathVariable long marketplaceId,
            @RequestParam String q,
            @RequestParam(defaultValue = "8") @Min(1) @Max(10) int limit) {
        if (q == null || q.isBlank() || q.length() < 2) {
            throw new BadRequestException("Query must be at least 2 characters");
        }
        try {
            return ResponseEntity.ok(searchSuggestionService.getSuggestions(marketplaceId, q, limit));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
