package backend.services.impl.products;

import backend.documents.ReviewDocument;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewSearchHit;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewSearchServiceImplTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID PRODUCT_ID  = TestIds.uuid(2);
    private static final UUID USER_ID     = TestIds.uuid(3);

    private ElasticsearchOperations elasticsearchOperations;
    private ReviewSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        service = new ReviewSearchServiceImpl(elasticsearchOperations);
    }

    // ─── input clamping (verified via empty-fallback page metadata) ───────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_sizeOver50_clampedTo50() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, null, null, null, "relevance", 0, 200);

        assertEquals(50, result.getSize());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_sizeZero_clampedToOne() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, null, null, null, "relevance", 0, 0);

        assertEquals(1, result.getSize());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_negativePageNumber_clampedToZero() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, null, null, null, "relevance", -5, 20);

        assertEquals(0, result.getPage());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_sizeClamped_exactlyAtBoundary_50_succeeds() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, null, null, null, "relevance", 0, 50);

        assertEquals(50, result.getSize()); // boundary: 50 is valid (max)
    }

    // ─── sort resolution ──────────────────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_invalidSort_stillReturnsResult() {
        // Invalid sort → falls back to "relevance"; no exception thrown
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, "great", null, null, "unknownSort", 0, 20);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_nullSort_treatedAsRelevance() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, "great", null, null, null, 0, 20);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
    }

    // ─── elasticsearch failure path ───────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_elasticsearchFails_returnsEmptyPage() {
        doThrow(new RuntimeException("ES cluster down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, "great product", null, null, "relevance", 0, 20);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotalElements());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_elasticsearchFails_withFilters_returnsEmptyPage() {
        doThrow(new RuntimeException("ES cluster down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, "great", List.of(4, 5), true, "createdAt", 1, 10);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
    }

    // ─── happy path with mocked SearchHits ───────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_happyPath_mapsReviewDocumentToHit() {
        UUID reviewId = TestIds.uuid(10);
        ReviewDocument doc = new ReviewDocument();
        doc.setId(reviewId);
        doc.setProductId(PRODUCT_ID);
        doc.setReviewerId(USER_ID);
        doc.setReviewerName("John D.");
        doc.setRating(5);
        doc.setTitle("Great product");
        doc.setBody("Loved it");
        doc.setStatus("PUBLISHED");
        doc.setVerifiedPurchase(true);
        doc.setHasMedia(false);
        doc.setHelpfulCount(3);

        SearchHit<ReviewDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightFields()).thenReturn(Map.of(
                "title", List.of("<mark>Great</mark> product"),
                "body", List.of("Loved <mark>it</mark>")));
        when(hit.getScore()).thenReturn(1.5f);

        SearchHits<ReviewDocument> hits = mock(SearchHits.class);
        when(hits.iterator()).thenReturn(List.of(hit).iterator());
        when(hits.getTotalHits()).thenReturn(1L);

        doReturn(hits).when(elasticsearchOperations)
                .search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, "great", null, null, "relevance", 0, 20);

        assertEquals(1, result.getItems().size());
        ReviewSearchHit rsh = result.getItems().get(0);
        assertEquals(reviewId, rsh.getId());
        assertEquals(5, rsh.getRating());
        assertEquals("Great product", rsh.getTitle());
        assertTrue(rsh.isVerifiedPurchase());
        assertEquals(3, rsh.getHelpfulCount());
        assertFalse(rsh.getTitleHighlights().isEmpty());
        assertFalse(rsh.getBodyHighlights().isEmpty());
        assertEquals("<mark>Great</mark> product", rsh.getTitleHighlights().get(0));
        assertEquals(1L, result.getTotalElements());
        assertTrue(rsh.getRelevanceScore() > 0);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_happyPath_nanScore_convertedToZero() {
        ReviewDocument doc = new ReviewDocument();
        doc.setId(TestIds.uuid(10));
        doc.setProductId(PRODUCT_ID);
        doc.setStatus("PUBLISHED");
        doc.setRating(3);
        doc.setTitle("OK");

        SearchHit<ReviewDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightFields()).thenReturn(Map.of());
        when(hit.getScore()).thenReturn(Float.NaN); // NaN score

        SearchHits<ReviewDocument> hits = mock(SearchHits.class);
        when(hits.iterator()).thenReturn(List.of(hit).iterator());
        when(hits.getTotalHits()).thenReturn(1L);

        doReturn(hits).when(elasticsearchOperations)
                .search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, null, null, null, "relevance", 0, 20);

        assertEquals(0.0, result.getItems().get(0).getRelevanceScore(), 0.001);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_happyPath_missingHighlights_returnsEmptyLists() {
        ReviewDocument doc = new ReviewDocument();
        doc.setId(TestIds.uuid(10));
        doc.setProductId(PRODUCT_ID);
        doc.setStatus("PUBLISHED");
        doc.setRating(4);
        doc.setTitle("Good product");

        SearchHit<ReviewDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightFields()).thenReturn(Map.of()); // no highlights
        when(hit.getScore()).thenReturn(0.8f);

        SearchHits<ReviewDocument> hits = mock(SearchHits.class);
        when(hits.iterator()).thenReturn(List.of(hit).iterator());
        when(hits.getTotalHits()).thenReturn(1L);

        doReturn(hits).when(elasticsearchOperations)
                .search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, null, null, null, "relevance", 0, 20);

        ReviewSearchHit rsh = result.getItems().get(0);
        assertTrue(rsh.getTitleHighlights().isEmpty());
        assertTrue(rsh.getBodyHighlights().isEmpty());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_happyPath_emptyHits_returnsEmptyPage() {
        SearchHits<ReviewDocument> hits = mock(SearchHits.class);
        when(hits.iterator()).thenReturn(List.<SearchHit<ReviewDocument>>of().iterator());
        when(hits.getTotalHits()).thenReturn(0L);

        doReturn(hits).when(elasticsearchOperations)
                .search(any(Query.class), (Class) any());

        PagedResponse<ReviewSearchHit> result =
                service.search(COMPANY_ID, PRODUCT_ID, "nothing matches", null, null, "relevance", 0, 20);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotalElements());
    }
}
