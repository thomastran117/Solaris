package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.models.core.ProductReview;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewIndexingServiceTest {

    private static final UUID REVIEW_ID = TestIds.uuid(1);

    private ReviewSearchRepository reviewSearchRepository;
    private ProductReviewRepository reviewRepository;
    private ReviewMediaRepository   mediaRepository;
    private IndexVersionManager     indexVersionManager;

    private ReviewIndexingService service;

    @BeforeEach
    void setUp() {
        reviewSearchRepository = mock(ReviewSearchRepository.class);
        reviewRepository       = mock(ProductReviewRepository.class);
        mediaRepository        = mock(ReviewMediaRepository.class);
        indexVersionManager    = mock(IndexVersionManager.class);

        service = new ReviewIndexingService(
                reviewSearchRepository,
                reviewRepository,
                mediaRepository,
                mock(IndexingFailureRepository.class),
                indexVersionManager,
                new EnvironmentSetting());

        ReflectionTestUtils.setField(service, "taskQueue", new LinkedBlockingQueue<>(1000));
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    @Test
    void indexReview_doesNotThrow() {
        ProductReview review = new ProductReview();
        review.setId(REVIEW_ID);

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
        ProductReview review = new ProductReview();
        review.setId(REVIEW_ID);
        when(reviewRepository.findAll()).thenReturn(List.of(review));
        when(mediaRepository.findByReviewIdInOrderByReviewIdAscPositionAsc(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.reindexAll());
    }

    // ─── run (ApplicationRunner) ──────────────────────────────────────────────

    @Test
    void run_ensuresIndexExists() throws Exception {
        when(reviewSearchRepository.count()).thenReturn(1L); // non-empty, skip reindex

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
}
