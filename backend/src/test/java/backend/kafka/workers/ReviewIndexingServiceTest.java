package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.repositories.search.ReviewSearchRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewIndexingServiceTest {

    private static final UUID REVIEW_ID  = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);

    private ReviewSearchRepository reviewSearchRepository;
    private ProductReviewRepository reviewRepository;
    private ReviewMediaRepository   mediaRepository;
    private IndexVersionManager     indexVersionManager;
    private IndexingFailureRepository failureRepository;

    private ReviewIndexingService service;

    @BeforeEach
    void setUp() {
        reviewSearchRepository = mock(ReviewSearchRepository.class);
        reviewRepository       = mock(ProductReviewRepository.class);
        mediaRepository        = mock(ReviewMediaRepository.class);
        indexVersionManager    = mock(IndexVersionManager.class);
        failureRepository      = mock(IndexingFailureRepository.class);

        service = new ReviewIndexingService(
                reviewSearchRepository,
                reviewRepository,
                mediaRepository,
                failureRepository,
                indexVersionManager,
                new EnvironmentSetting());

        ReflectionTestUtils.setField(service, "taskQueue", new LinkedBlockingQueue<>(1000));
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    @Test
    void indexReview_doesNotThrow() {
        ProductReview review = review();
        assertDoesNotThrow(() -> service.indexReview(review, false));
    }

    @Test
    void removeReview_doesNotThrow() {
        assertDoesNotThrow(() -> service.removeReview(REVIEW_ID));
    }

    @Test
    void reindexAll_noReviews_doesNotQueryMedia() {
        when(reviewRepository.findAll()).thenReturn(List.of());

        service.reindexAll();

        verify(mediaRepository, never()).findByReviewIdInOrderByReviewIdAscPositionAsc(any());
    }

    @Test
    void reindexAll_withReviews_queuesAll() {
        when(reviewRepository.findAll()).thenReturn(List.of(review()));
        when(mediaRepository.findByReviewIdInOrderByReviewIdAscPositionAsc(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.reindexAll());
    }

    // ─── run (ApplicationRunner) ──────────────────────────────────────────────

    @Test
    void run_ensuresIndexExists() throws Exception {
        when(reviewSearchRepository.count()).thenReturn(1L);

        service.run(null);

        verify(indexVersionManager).ensureIndexExists("reviews");
    }

    @Test
    void run_emptyIndex_queuesReviews() throws Exception {
        when(reviewSearchRepository.count()).thenReturn(0L);
        when(reviewRepository.findAll()).thenReturn(List.of());

        service.run(null);

        verify(reviewRepository).findAll();
    }

    @Test
    void run_countThrows_doesNotPropagate() throws Exception {
        when(reviewSearchRepository.count()).thenThrow(new RuntimeException("ES down"));

        assertDoesNotThrow(() -> service.run(null));
    }

    // ─── processBatch — success paths ────────────────────────────────────────

    @Test
    void processBatch_indexReview_callsSaveAll() {
        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Index(review(), false));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(reviewSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_removeReview_callsDeleteAll() {
        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Remove(REVIEW_ID));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(reviewSearchRepository).deleteAllById(anyList());
    }

    @Test
    void processBatch_emptyBatch_callsNothing() {
        ReflectionTestUtils.invokeMethod(service, "processBatch", List.of());

        verify(reviewSearchRepository, never()).saveAll(any());
        verify(reviewSearchRepository, never()).deleteAllById(any());
    }

    @Test
    void processBatch_reviewWithProductAndReviewer_buildsDocument() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company);

        User reviewer = new User();
        reviewer.setId(TestIds.uuid(10));
        reviewer.setFirstName("Jane");
        reviewer.setLastName("Doe");

        ProductReview r = review();
        r.setProduct(product);
        r.setReviewer(reviewer);

        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Index(r, true));

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(reviewSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_reviewWithNullProductAndNoReviewer_gracefullyHandled() {
        ProductReview r = review();
        r.setProduct(null);

        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Index(r, false));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "processBatch", batch));
        verify(reviewSearchRepository).saveAll(anyList());
    }

    @Test
    void processBatch_mixed_indexAndRemove() {
        List<ReviewIndexingService.Task> batch = List.of(
                new ReviewIndexingService.Task.Index(review(), false),
                new ReviewIndexingService.Task.Remove(TestIds.uuid(50))
        );

        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(reviewSearchRepository).saveAll(anyList());
        verify(reviewSearchRepository).deleteAllById(anyList());
    }

    // ─── processBatch — failure / DLQ paths ──────────────────────────────────

    @Test
    void processBatch_saveAllThrows_persistsFailure() {
        doThrow(new RuntimeException("ES down")).when(reviewSearchRepository).saveAll(any());

        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Index(review(), false));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(failureRepository).save(any());
    }

    @Test
    void processBatch_deleteAllThrows_persistsRemoveFailure() {
        doThrow(new RuntimeException("ES down")).when(reviewSearchRepository).deleteAllById(any());

        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Remove(REVIEW_ID));
        ReflectionTestUtils.invokeMethod(service, "processBatch", batch);

        verify(failureRepository).save(any());
    }

    @Test
    void processBatch_failureRepositoryThrows_doesNotPropagate() {
        doThrow(new RuntimeException("ES down")).when(reviewSearchRepository).saveAll(any());
        doThrow(new RuntimeException("DB down")).when(failureRepository).save(any());

        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Index(review(), false));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "processBatch", batch));
    }

    // ─── toDocument — null company on product ─────────────────────────────────

    @Test
    void processBatch_reviewWithProductButNullCompany_buildsDocumentWithNullCompanyId() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(null); // company is null

        ProductReview r = review();
        r.setProduct(product);

        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Index(r, false));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "processBatch", batch));
        verify(reviewSearchRepository).saveAll(anyList());
    }

    // ─── submit — queue full ───────────────────────────────────────────────────

    @Test
    void indexReview_queueFull_doesNotThrow() {
        LinkedBlockingQueue<ReviewIndexingService.Task> fullQueue = new LinkedBlockingQueue<>(1);
        fullQueue.offer(new ReviewIndexingService.Task.Index(review(), false));
        ReflectionTestUtils.setField(service, "taskQueue", fullQueue);

        assertDoesNotThrow(() -> service.indexReview(review(), false));
    }

    // ─── failure truncation ────────────────────────────────────────────────────

    @Test
    void processBatch_saveAllThrowsLongMessage_truncatesFailureMessage() {
        String longMsg = "E".repeat(600);
        doThrow(new RuntimeException(longMsg)).when(reviewSearchRepository).saveAll(any());

        List<ReviewIndexingService.Task> batch =
                List.of(new ReviewIndexingService.Task.Index(review(), false));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "processBatch", batch));

        verify(failureRepository).save(argThat(f ->
                f instanceof backend.models.core.IndexingFailure failure
                        && failure.getErrorMessage() != null
                        && failure.getErrorMessage().length() <= 500));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ProductReview review() {
        ProductReview r = new ProductReview();
        r.setId(REVIEW_ID);
        r.setRating(4);
        return r;
    }
}
