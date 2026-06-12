package backend.services.impl.products;

import backend.dtos.requests.review.ModerateReviewRequest;
import backend.exceptions.http.ResourceNotFoundException;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.models.enums.ModerationAction;
import backend.models.enums.ReviewStatus;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.services.impl.SingleFlightCache;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewModerationServiceImplTest {

    private static final UUID REVIEW_ID    = TestIds.uuid(1);
    private static final UUID MODERATOR_ID = TestIds.uuid(2);
    private static final UUID PRODUCT_ID   = TestIds.uuid(3);
    private static final UUID COMPANY_ID   = TestIds.uuid(4);
    private static final UUID REVIEWER_ID  = TestIds.uuid(5);

    private ProductReviewRepository reviewRepository;
    private ReviewMediaRepository   mediaRepository;
    private ReviewIndexingService   reviewIndexingService;
    private SingleFlightCache       singleFlightCache;

    private ReviewModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        reviewRepository      = mock(ProductReviewRepository.class);
        mediaRepository       = mock(ReviewMediaRepository.class);
        reviewIndexingService = mock(ReviewIndexingService.class);
        singleFlightCache     = mock(SingleFlightCache.class);

        service = new ReviewModerationServiceImpl(reviewRepository, mediaRepository,
                reviewIndexingService, singleFlightCache);
    }

    @Test
    void moderate_reviewNotFound_throwsResourceNotFoundException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.moderate(REVIEW_ID, MODERATOR_ID, request(ModerationAction.PUBLISH)));
    }

    @Test
    void moderate_publishAction_updatesStatusAndIndexes() {
        ProductReview review = review(true);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);

        service.moderate(REVIEW_ID, MODERATOR_ID, request(ModerationAction.PUBLISH));

        verify(reviewRepository).updateStatusIfDifferent(REVIEW_ID, ReviewStatus.PUBLISHED.name());
        verify(reviewIndexingService).indexReview(eq(review), eq(false));
        verify(reviewIndexingService, never()).removeReview(any());
    }

    @Test
    void moderate_hideAction_updatesStatusAndIndexes() {
        ProductReview review = review(true);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);

        service.moderate(REVIEW_ID, MODERATOR_ID, request(ModerationAction.HIDE));

        verify(reviewRepository).updateStatusIfDifferent(REVIEW_ID, ReviewStatus.HIDDEN.name());
        verify(reviewIndexingService).indexReview(eq(review), eq(false));
        verify(reviewIndexingService, never()).removeReview(any());
    }

    @Test
    void moderate_removeAction_removesFromIndexNotReindex() {
        ProductReview review = review(true);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);

        service.moderate(REVIEW_ID, MODERATOR_ID, request(ModerationAction.REMOVE));

        verify(reviewRepository).updateStatusIfDifferent(REVIEW_ID, ReviewStatus.REMOVED.name());
        verify(reviewIndexingService).removeReview(REVIEW_ID);
        verify(reviewIndexingService, never()).indexReview(any(), any(Boolean.class));
    }

    @Test
    void moderate_reviewHasNoReviewer_doesNotEvictReviewerKey() {
        ProductReview review = review(false); // no reviewer
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);

        service.moderate(REVIEW_ID, MODERATOR_ID, request(ModerationAction.PUBLISH));

        // Verify pattern evict was called for the product cache but not a reviewer-specific key
        verify(singleFlightCache).evictByPattern(anyString());
        verify(singleFlightCache).evict("reviews:summary:" + COMPANY_ID + ":" + PRODUCT_ID);
        // No reviewer-specific evict since reviewer is null
        verify(singleFlightCache, never()).evict("review:me:" + COMPANY_ID + ":" + PRODUCT_ID + ":" + REVIEWER_ID);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ProductReview review(boolean withReviewer) {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company);

        ProductReview r = new ProductReview();
        r.setId(REVIEW_ID);
        r.setStatus(ReviewStatus.PENDING_MODERATION);
        r.setProduct(product);
        if (withReviewer) {
            User reviewer = new User();
            reviewer.setId(REVIEWER_ID);
            r.setReviewer(reviewer);
        }
        return r;
    }

    private ModerateReviewRequest request(ModerationAction action) {
        ModerateReviewRequest req = new ModerateReviewRequest();
        req.setAction(action);
        return req;
    }
}
