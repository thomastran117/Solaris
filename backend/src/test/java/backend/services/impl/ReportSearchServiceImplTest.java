package backend.services.impl;

import backend.documents.ReportDocument;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.report.ReportResponse;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportSearchServiceImplTest {

    private static final UUID REPORT_ID   = TestIds.uuid(1);
    private static final UUID TARGET_ID   = TestIds.uuid(2);
    private static final UUID REPORTER_ID = TestIds.uuid(3);

    private ElasticsearchOperations elasticsearchOperations;
    private ProductRepository productRepository;
    private CompanyRepository companyRepository;
    private ProductReviewRepository reviewRepository;
    private UserRepository userRepository;

    private ReportSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        productRepository       = mock(ProductRepository.class);
        companyRepository       = mock(CompanyRepository.class);
        reviewRepository        = mock(ProductReviewRepository.class);
        userRepository          = mock(UserRepository.class);

        service = new ReportSearchServiceImpl(
                elasticsearchOperations, productRepository,
                companyRepository, reviewRepository, userRepository);
    }

    // ─── input clamping ───────────────────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_sizeOver50_clampedTo50() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search(null, null, null, null, null, null, "newest", 0, 200);

        assertEquals(50, result.getSize());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_sizeZero_clampedToOne() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search(null, null, null, null, null, null, "newest", 0, 0);

        assertEquals(1, result.getSize());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_negativePageNumber_clampedToZero() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search(null, null, null, null, null, null, "newest", -5, 20);

        assertEquals(0, result.getPage());
    }

    // ─── elasticsearch failure path ───────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_elasticsearchFails_returnsEmptyPage() {
        doThrow(new RuntimeException("ES cluster down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search("spam", null, null, null, null, null, "newest", 0, 20);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotalElements());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_elasticsearchFails_withFilters_returnsEmptyPage() {
        doThrow(new RuntimeException("ES cluster down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result = service.search(
                "counterfeit", ReportTargetType.PRODUCT, ReportStatus.OPEN,
                ReportReason.COUNTERFEIT, Instant.now().minusSeconds(86400), null, "newest", 0, 10);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
    }

    // ─── sort fallback ────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_unknownSort_doesNotThrow() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search(null, null, null, null, null, null, "unknown_sort", 0, 20);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_nullSort_defaultsToNewest() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search(null, null, null, null, null, null, null, 0, 20);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
    }

    // ─── happy path with mocked SearchHits ───────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_happyPath_mapsDocumentToResponse() {
        // resolveReporterName always queries the DB — stub it so we get the expected name
        backend.models.core.User reporter = mock(backend.models.core.User.class);
        when(reporter.getFirstName()).thenReturn("Alice");
        when(reporter.getLastName()).thenReturn("Smith");
        when(userRepository.findById(REPORTER_ID)).thenReturn(java.util.Optional.of(reporter));

        ReportDocument doc = new ReportDocument();
        doc.setId(REPORT_ID);
        doc.setTargetType("PRODUCT");
        doc.setTargetId(TARGET_ID.toString());
        doc.setReporterId(REPORTER_ID.toString());
        doc.setReason("SPAM");
        doc.setTitle("Spam listing");
        doc.setDescription("This listing is spam.");
        doc.setStatus("OPEN");
        doc.setTargetName("Blue Widget");  // non-null → used directly, skips repo
        doc.setCreatedAt(Instant.now());

        SearchHit<ReportDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        SearchHits<ReportDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);

        doReturn(hits).when(elasticsearchOperations)
                .search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search("spam", null, null, null, null, null, "newest", 0, 20);

        assertEquals(1, result.getItems().size());
        ReportResponse response = result.getItems().get(0);
        assertEquals(REPORT_ID.toString(), response.getId());
        assertEquals(ReportTargetType.PRODUCT, response.getTargetType());
        assertEquals("Spam listing", response.getTitle());
        assertEquals(ReportStatus.OPEN, response.getStatus());
        assertEquals("Blue Widget", response.getTargetName());
        assertEquals("Alice Smith", response.getReporterName());
        assertEquals(1L, result.getTotalElements());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_happyPath_emptyHits_returnsEmptyPage() {
        SearchHits<ReportDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(hits.getTotalHits()).thenReturn(0L);

        doReturn(hits).when(elasticsearchOperations)
                .search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search("nothing", null, null, null, null, null, "newest", 0, 20);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0L, result.getTotalElements());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_happyPath_nullTargetName_fallsBackToRepository() {
        // targetName is null in the document — service should fall back to resolving from repo
        ReportDocument doc = new ReportDocument();
        doc.setId(REPORT_ID);
        doc.setTargetType("PRODUCT");
        doc.setTargetId(TARGET_ID.toString());
        doc.setReporterId(REPORTER_ID.toString());
        doc.setReason("SPAM");
        doc.setTitle("Spam");
        doc.setDescription("Spam listing.");
        doc.setStatus("OPEN");
        doc.setTargetName(null);  // force repository lookup
        doc.setCreatedAt(Instant.now());

        backend.models.core.Product product = mock(backend.models.core.Product.class);
        when(product.getName()).thenReturn("Red Widget");
        when(productRepository.findById(TARGET_ID)).thenReturn(java.util.Optional.of(product));

        SearchHit<ReportDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        SearchHits<ReportDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);

        doReturn(hits).when(elasticsearchOperations)
                .search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search(null, null, null, null, null, null, "newest", 0, 20);

        assertEquals("Red Widget", result.getItems().get(0).getTargetName());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_oldestSort_doesNotThrow() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search(null, null, null, null, null, null, "oldest", 0, 20);

        assertNotNull(result);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_statusSort_doesNotThrow() {
        doThrow(new RuntimeException("ES down"))
                .when(elasticsearchOperations).search(any(Query.class), (Class) any());

        PagedResponse<ReportResponse> result =
                service.search(null, null, null, null, null, null, "status", 0, 20);

        assertNotNull(result);
    }
}
