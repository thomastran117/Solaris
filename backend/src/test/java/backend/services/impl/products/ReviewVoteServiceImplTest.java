package backend.services.impl.products;

import backend.dtos.responses.review.HelpfulVoteResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.enums.ReviewStatus;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.repositories.ReviewVoteRepository;
import backend.services.impl.SingleFlightCache;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewVoteServiceImplTest {

    private static final UUID REVIEW_ID  = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID COMPANY_ID = TestIds.uuid(4);

    private ProductReviewRepository reviewRepository;
    private ReviewVoteRepository    voteRepository;
    private ReviewMediaRepository   mediaRepository;
    private ReviewIndexingService   reviewIndexingService;
    private SingleFlightCache       singleFlightCache;

    private ReviewVoteServiceImpl service;

    @BeforeEach
    void setUp() {
        reviewRepository      = mock(ProductReviewRepository.class);
        voteRepository        = mock(ReviewVoteRepository.class);
        mediaRepository       = mock(ReviewMediaRepository.class);
        reviewIndexingService = mock(ReviewIndexingService.class);
        singleFlightCache     = mock(SingleFlightCache.class);

        service = new ReviewVoteServiceImpl(reviewRepository, voteRepository,
                mediaRepository, reviewIndexingService, singleFlightCache);
    }

    // ─── voteHelpful ──────────────────────────────────────────────────────────

    @Test
    void voteHelpful_reviewNotFound_throwsResourceNotFoundException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.voteHelpful(REVIEW_ID, USER_ID));
    }

    @Test
    void voteHelpful_reviewNotPublished_throwsResourceNotFoundException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review(ReviewStatus.PENDING_MODERATION)));

        assertThrows(ResourceNotFoundException.class, () -> service.voteHelpful(REVIEW_ID, USER_ID));
    }

    @Test
    void voteHelpful_voteAlreadyExists_returnsCurrentCountWithoutIncrement() {
        ProductReview rev = review(ReviewStatus.PUBLISHED);
        rev.setHelpfulCount(3);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(rev));
        when(voteRepository.existsByReviewIdAndUserId(REVIEW_ID, USER_ID)).thenReturn(true);

        HelpfulVoteResponse resp = service.voteHelpful(REVIEW_ID, USER_ID);

        assertEquals(3, resp.getHelpfulCount());
        verify(reviewRepository, never()).incrementHelpfulCount(any());
    }

    @Test
    void voteHelpful_duplicateKeyOnSave_returnsCurrentCountWithoutIncrement() {
        ProductReview rev = review(ReviewStatus.PUBLISHED);
        rev.setHelpfulCount(5);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(rev));
        when(voteRepository.existsByReviewIdAndUserId(REVIEW_ID, USER_ID)).thenReturn(false);
        when(voteRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        HelpfulVoteResponse resp = service.voteHelpful(REVIEW_ID, USER_ID);

        assertEquals(5, resp.getHelpfulCount());
        verify(reviewRepository, never()).incrementHelpfulCount(any());
    }

    @Test
    void voteHelpful_happyPath_incrementsAndEvicts() {
        ProductReview rev = review(ReviewStatus.PUBLISHED);
        rev.setHelpfulCount(2);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(rev));
        when(voteRepository.existsByReviewIdAndUserId(REVIEW_ID, USER_ID)).thenReturn(false);
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);

        HelpfulVoteResponse resp = service.voteHelpful(REVIEW_ID, USER_ID);

        assertEquals(3, resp.getHelpfulCount());
        verify(reviewRepository).incrementHelpfulCount(REVIEW_ID);
        // No transaction active in unit test → eviction runs synchronously
        verify(singleFlightCache).evictByPattern(anyString());
    }

    // ─── removeHelpful ────────────────────────────────────────────────────────

    @Test
    void removeHelpful_voteNotFound_returnsCurrentCountWithoutDecrement() {
        ProductReview rev = review(ReviewStatus.PUBLISHED);
        rev.setHelpfulCount(4);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(rev));
        when(voteRepository.deleteByReviewIdAndUserId(REVIEW_ID, USER_ID)).thenReturn(0);

        HelpfulVoteResponse resp = service.removeHelpful(REVIEW_ID, USER_ID);

        assertEquals(4, resp.getHelpfulCount());
        verify(reviewRepository, never()).decrementHelpfulCount(any());
    }

    @Test
    void removeHelpful_happyPath_decrementsAndEvicts() {
        ProductReview rev = review(ReviewStatus.PUBLISHED);
        rev.setHelpfulCount(5);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(rev));
        when(voteRepository.deleteByReviewIdAndUserId(REVIEW_ID, USER_ID)).thenReturn(1);
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(1L);

        HelpfulVoteResponse resp = service.removeHelpful(REVIEW_ID, USER_ID);

        assertEquals(4, resp.getHelpfulCount());
        verify(reviewRepository).decrementHelpfulCount(REVIEW_ID);
        verify(singleFlightCache).evictByPattern(anyString());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ProductReview review(ReviewStatus status) {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company);

        ProductReview r = new ProductReview();
        r.setId(REVIEW_ID);
        r.setStatus(status);
        r.setProduct(product);
        r.setHelpfulCount(0);
        return r;
    }
}
