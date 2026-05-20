package backend.services.impl.search;

import backend.dtos.responses.search.SearchSuggestionsResponse;
import backend.services.impl.SingleFlightCache;
import backend.testutil.TestIds;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchSuggestionServiceImplTest {

    private static final UUID MARKETPLACE_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID     = TestIds.uuid(2);

    private ElasticsearchOperations elasticsearchOperations;
    private SingleFlightCache singleFlightCache;

    private SearchSuggestionServiceImpl service;

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        singleFlightCache       = mock(SingleFlightCache.class);

        service = new SearchSuggestionServiceImpl(elasticsearchOperations, singleFlightCache);

        // Bypass cache: execute supplier directly
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── getSuggestions ───────────────────────────────────────────────────────

    @Test
    void getSuggestions_nullQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, null, 8);

        assertTrue(result.productNames().isEmpty());
        assertTrue(result.categories().isEmpty());
        assertTrue(result.brands().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    void getSuggestions_blankQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "   ", 8);

        assertTrue(result.productNames().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    void getSuggestions_singleCharQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "a", 8);

        assertTrue(result.productNames().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSuggestions_elasticsearchFails_returnsEmptyGracefully() {
        doThrow(new RuntimeException("ES cluster down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "widget", 8);

        assertNotNull(result);
        assertTrue(result.productNames().isEmpty());
        assertTrue(result.categories().isEmpty());
        assertTrue(result.brands().isEmpty());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSuggestions_limitClampedTo10_reflectedInCacheKey() {
        // ES throws → lambda returns empty; we verify the clamped limit in the cache key
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        service.getSuggestions(MARKETPLACE_ID, "widget", 100); // request 100 → clamped to 10

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(singleFlightCache).getOrLoad(keyCaptor.capture(), anyLong(), any(), any(TypeReference.class));
        assertTrue(keyCaptor.getValue().endsWith(":10"),
                "Cache key should end with clamped limit :10, got: " + keyCaptor.getValue());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSuggestions_limitUnder10_notClamped() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        service.getSuggestions(MARKETPLACE_ID, "widget", 5); // 5 ≤ 10, no clamping

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(singleFlightCache).getOrLoad(keyCaptor.capture(), anyLong(), any(), any(TypeReference.class));
        assertTrue(keyCaptor.getValue().endsWith(":5"),
                "Cache key should preserve limit :5, got: " + keyCaptor.getValue());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSuggestions_validQuery_cacheKeyIncludesLowercaseQuery() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        service.getSuggestions(MARKETPLACE_ID, "WIDGET", 8);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(singleFlightCache).getOrLoad(keyCaptor.capture(), anyLong(), any(), any(TypeReference.class));
        assertTrue(keyCaptor.getValue().contains("widget"),
                "Cache key should lowercase query; got: " + keyCaptor.getValue());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSuggestions_twoCharQuery_isAccepted() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        // Length == 2 is the minimum; should proceed to cache/ES
        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "ab", 8);

        assertNotNull(result);
        verify(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── getCompanySuggestions ────────────────────────────────────────────────

    @Test
    void getCompanySuggestions_nullQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getCompanySuggestions(COMPANY_ID, null, 8);

        assertTrue(result.productNames().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    void getCompanySuggestions_singleCharQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getCompanySuggestions(COMPANY_ID, "x", 8);

        assertTrue(result.productNames().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getCompanySuggestions_elasticsearchFails_returnsEmptyGracefully() {
        doThrow(new RuntimeException("ES cluster down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        SearchSuggestionsResponse result = service.getCompanySuggestions(COMPANY_ID, "laptop", 8);

        assertNotNull(result);
        assertTrue(result.productNames().isEmpty());
        assertTrue(result.categories().isEmpty());
        assertTrue(result.brands().isEmpty());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getCompanySuggestions_limitClampedTo10() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        service.getCompanySuggestions(COMPANY_ID, "lap", 50); // 50 → clamped to 10

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(singleFlightCache).getOrLoad(keyCaptor.capture(), anyLong(), any(), any(TypeReference.class));
        assertTrue(keyCaptor.getValue().endsWith(":10"),
                "Cache key should end with :10, got: " + keyCaptor.getValue());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getCompanySuggestions_cacheKeyDifferentFromMarketplaceKey() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        service.getSuggestions(MARKETPLACE_ID, "lap", 8);
        service.getCompanySuggestions(COMPANY_ID, "lap", 8);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(singleFlightCache, times(2)).getOrLoad(keyCaptor.capture(), anyLong(), any(), any(TypeReference.class));

        String marketplaceKey = keyCaptor.getAllValues().get(0);
        String companyKey     = keyCaptor.getAllValues().get(1);
        assertTrue(companyKey.contains("company"),
                "Company cache key should contain 'company' prefix: " + companyKey);
        assertNotEquals(marketplaceKey, companyKey);
    }
}
