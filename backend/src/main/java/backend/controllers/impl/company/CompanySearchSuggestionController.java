package backend.controllers.impl.company;

import backend.dtos.responses.search.SearchSuggestionsResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.RateLimitService;
import backend.services.intf.search.SearchSuggestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous autocomplete suggestions scoped to a single company storefront.
 * Mirrors {@code SearchSuggestionController} but filtered by {@code companyId}.
 */
@RestController
@RequestMapping("/companies/{companyId}/catalog/search")
public class CompanySearchSuggestionController {

    private static final int SUGGEST_LIMIT = 60;
    private static final int SUGGEST_WINDOW_SECONDS = 60;

    private final SearchSuggestionService searchSuggestionService;
    private final RateLimitService rateLimitService;

    public CompanySearchSuggestionController(SearchSuggestionService searchSuggestionService,
                                             RateLimitService rateLimitService) {
        this.searchSuggestionService = searchSuggestionService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/suggestions")
    public ResponseEntity<SearchSuggestionsResponse> getSuggestions(
            @PathVariable long companyId,
            @RequestParam String q,
            @RequestParam(defaultValue = "8") @Min(1) @Max(10) int limit,
            HttpServletRequest httpRequest) {
        if (q == null || q.isBlank() || q.length() < 2) {
            throw new BadRequestException("Query must be at least 2 characters");
        }
        // Key by IP + companyId so one IP browsing two stores doesn't share a single budget.
        String rateKey = httpRequest.getRemoteAddr() + ":" + companyId;
        rateLimitService.enforce("search:suggest:company", rateKey, SUGGEST_LIMIT, SUGGEST_WINDOW_SECONDS);
        try {
            return ResponseEntity.ok(searchSuggestionService.getCompanySuggestions(companyId, q, limit));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
