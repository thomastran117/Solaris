package backend.services.impl.search;

import backend.documents.ProductDocument;
import backend.dtos.responses.search.ProductSuggestion;
import backend.dtos.responses.search.SearchSuggestionsResponse;
import backend.services.impl.SingleFlightCache;
import backend.testutil.TestIds;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
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
    @SuppressWarnings("unchecked")
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        singleFlightCache       = mock(SingleFlightCache.class);

        service = new SearchSuggestionServiceImpl(elasticsearchOperations, singleFlightCache);

        // Bypass cache: execute supplier directly
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private SearchHits<ProductDocument> makeHits(ProductDocument... docs) {
        SearchHits hits = mock(SearchHits.class);
        List<SearchHit<ProductDocument>> hitList = Arrays.stream(docs)
                .map(d -> {
                    SearchHit<ProductDocument> hit = mock(SearchHit.class);
                    when(hit.getContent()).thenReturn(d);
                    return hit;
                })
                .toList();
        when(hits.stream()).thenReturn(hitList.stream());
        when(hits.getAggregations()).thenReturn(null);
        return hits;
    }

    private ProductDocument makeDoc(UUID id, String name, BigDecimal price,
                                    BigDecimal discountedPrice, String thumbnailUrl) {
        ProductDocument d = new ProductDocument();
        d.setId(id);
        d.setName(name);
        d.setPrice(price);
        d.setDiscountedPrice(discountedPrice);
        d.setThumbnailUrl(thumbnailUrl);
        return d;
    }

    // ─── getSuggestions ───────────────────────────────────────────────────────

    @Test
    void getSuggestions_nullQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, null, 8);

        assertTrue(result.products().isEmpty());
        assertTrue(result.categories().isEmpty());
        assertTrue(result.brands().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    void getSuggestions_blankQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "   ", 8);

        assertTrue(result.products().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    void getSuggestions_singleCharQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "a", 8);

        assertTrue(result.products().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSuggestions_elasticsearchFails_returnsEmptyGracefully() {
        doThrow(new RuntimeException("ES cluster down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "widget", 8);

        assertNotNull(result);
        assertTrue(result.products().isEmpty());
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSuggestions_validHits_returnsProductSuggestions() {
        UUID id1 = TestIds.uuid(10);
        UUID id2 = TestIds.uuid(11);
        ProductDocument doc1 = makeDoc(id1, "Widget Pro", new BigDecimal("29.99"), null, "https://cdn/w.jpg");
        ProductDocument doc2 = makeDoc(id2, "Widget Lite", new BigDecimal("9.99"), new BigDecimal("7.99"), null);

        // Build the mock before starting the outer when() to avoid nested-stubbing errors
        SearchHits<ProductDocument> hits = makeHits(doc1, doc2);
        when(elasticsearchOperations.search(any(Query.class), (Class) any()))
                .thenReturn(hits);

        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "widget", 8);

        assertEquals(2, result.products().size());

        ProductSuggestion s1 = result.products().get(0);
        assertEquals(id1, s1.id());
        assertEquals("Widget Pro", s1.name());
        assertEquals(new BigDecimal("29.99"), s1.price());
        assertNull(s1.discountedPrice());
        assertEquals("https://cdn/w.jpg", s1.thumbnailUrl());

        ProductSuggestion s2 = result.products().get(1);
        assertEquals(id2, s2.id());
        assertEquals(new BigDecimal("7.99"), s2.discountedPrice());
        assertNull(s2.thumbnailUrl());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getSuggestions_capsProductsAt3() {
        ProductDocument[] docs = new ProductDocument[5];
        for (int i = 0; i < 5; i++) {
            docs[i] = makeDoc(TestIds.uuid(20 + i), "Product " + i,
                    new BigDecimal("10.00"), null, null);
        }
        SearchHits<ProductDocument> hits = makeHits(docs);
        when(elasticsearchOperations.search(any(Query.class), (Class) any()))
                .thenReturn(hits);

        SearchSuggestionsResponse result = service.getSuggestions(MARKETPLACE_ID, "prod", 8);

        assertEquals(3, result.products().size());
    }

    // ─── getCompanySuggestions ────────────────────────────────────────────────

    @Test
    void getCompanySuggestions_nullQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getCompanySuggestions(COMPANY_ID, null, 8);

        assertTrue(result.products().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    void getCompanySuggestions_singleCharQuery_returnsEmptyImmediately() {
        SearchSuggestionsResponse result = service.getCompanySuggestions(COMPANY_ID, "x", 8);

        assertTrue(result.products().isEmpty());
        verifyNoInteractions(singleFlightCache, elasticsearchOperations);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getCompanySuggestions_elasticsearchFails_returnsEmptyGracefully() {
        doThrow(new RuntimeException("ES cluster down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        SearchSuggestionsResponse result = service.getCompanySuggestions(COMPANY_ID, "laptop", 8);

        assertNotNull(result);
        assertTrue(result.products().isEmpty());
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getCompanySuggestions_validHits_returnsProductSuggestions() {
        UUID id1 = TestIds.uuid(30);
        ProductDocument doc1 = makeDoc(id1, "Laptop X", new BigDecimal("999.00"),
                new BigDecimal("899.00"), "https://cdn/lx.jpg");

        SearchHits<ProductDocument> hits = makeHits(doc1);
        when(elasticsearchOperations.search(any(Query.class), (Class) any()))
                .thenReturn(hits);

        SearchSuggestionsResponse result = service.getCompanySuggestions(COMPANY_ID, "lap", 8);

        assertEquals(1, result.products().size());
        ProductSuggestion s = result.products().get(0);
        assertEquals(id1, s.id());
        assertEquals("Laptop X", s.name());
        assertEquals(new BigDecimal("999.00"), s.price());
        assertEquals(new BigDecimal("899.00"), s.discountedPrice());
        assertEquals("https://cdn/lx.jpg", s.thumbnailUrl());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getCompanySuggestions_capsProductsAt3() {
        ProductDocument[] docs = new ProductDocument[5];
        for (int i = 0; i < 5; i++) {
            docs[i] = makeDoc(TestIds.uuid(40 + i), "Item " + i,
                    new BigDecimal("5.00"), null, null);
        }
        SearchHits<ProductDocument> hits = makeHits(docs);
        when(elasticsearchOperations.search(any(Query.class), (Class) any()))
                .thenReturn(hits);

        SearchSuggestionsResponse result = service.getCompanySuggestions(COMPANY_ID, "item", 8);

        assertEquals(3, result.products().size());
    }
}
