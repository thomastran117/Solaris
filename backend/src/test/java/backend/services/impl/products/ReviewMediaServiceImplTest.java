package backend.services.impl.products;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.requests.review.AttachReviewMediaRequest;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.ReviewMedia;
import backend.models.core.User;
import backend.models.enums.ReviewStatus;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.services.impl.SingleFlightCache;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewMediaServiceImplTest {

    private static final UUID REVIEW_ID  = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID COMPANY_ID = TestIds.uuid(4);
    private static final UUID MEDIA_ID   = TestIds.uuid(5);

    private static final String CDN_BASE = "https://cdn.example.com";
    private static final String VALID_URL =
            CDN_BASE + "/review-media/" + USER_ID + "/photo.jpg";

    private ProductReviewRepository reviewRepository;
    private ReviewMediaRepository   mediaRepository;
    private EnvironmentSetting      environmentSetting;
    private ReviewIndexingService   reviewIndexingService;
    private SingleFlightCache       singleFlightCache;

    private ReviewMediaServiceImpl service;

    @BeforeEach
    void setUp() {
        reviewRepository      = mock(ProductReviewRepository.class);
        mediaRepository       = mock(ReviewMediaRepository.class);
        reviewIndexingService = mock(ReviewIndexingService.class);
        singleFlightCache     = mock(SingleFlightCache.class);

        environmentSetting = new EnvironmentSetting();
        environmentSetting.getS3().setPublicUrlBase(CDN_BASE);

        service = new ReviewMediaServiceImpl(reviewRepository, mediaRepository,
                environmentSetting, reviewIndexingService, singleFlightCache);
    }

    // ─── attachMedia ──────────────────────────────────────────────────────────

    @Test
    void attachMedia_reviewNotFound_throwsResourceNotFoundException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.attachMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, USER_ID, request(VALID_URL)));
    }

    @Test
    void attachMedia_productMismatch_throwsResourceNotFoundException() {
        ProductReview review = review();
        review.getProduct().setId(TestIds.uuid(99)); // different product
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        assertThrows(ResourceNotFoundException.class, () ->
                service.attachMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, USER_ID, request(VALID_URL)));
    }

    @Test
    void attachMedia_reviewerNotCurrentUser_throwsForbiddenException() {
        ProductReview review = review();
        review.getReviewer().setId(TestIds.uuid(99)); // different user
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

        assertThrows(ForbiddenException.class, () ->
                service.attachMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, USER_ID, request(VALID_URL)));
    }

    @Test
    void attachMedia_urlNotInReviewMediaFolder_throwsBadRequestException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review()));

        assertThrows(BadRequestException.class, () ->
                service.attachMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, USER_ID,
                        request("https://malicious.com/photo.jpg")));
    }

    @Test
    void attachMedia_alreadyAtMaxMedia_throwsBadRequestException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(5L); // MAX = 5

        assertThrows(BadRequestException.class, () ->
                service.attachMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, USER_ID, request(VALID_URL)));
    }

    @Test
    void attachMedia_happyPath_savesAndEvictsCache() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(1L);

        ReviewMedia saved = new ReviewMedia();
        saved.setId(MEDIA_ID);
        saved.setUrl(VALID_URL);
        saved.setPosition(1);
        when(mediaRepository.save(any(ReviewMedia.class))).thenReturn(saved);

        service.attachMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, USER_ID, request(VALID_URL));

        verify(mediaRepository).save(any(ReviewMedia.class));
        // No active transaction in unit test → eviction runs synchronously
        verify(singleFlightCache).evictByPattern(any());
        verify(reviewIndexingService).indexReview(any(), any(Boolean.class));
    }

    // ─── deleteMedia ──────────────────────────────────────────────────────────

    @Test
    void deleteMedia_mediaNotFound_throwsResourceNotFoundException() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
        when(mediaRepository.findByIdAndReviewId(MEDIA_ID, REVIEW_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.deleteMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, MEDIA_ID, USER_ID));
    }

    @Test
    void deleteMedia_lastMedia_indexesWithStillHasMediaFalse() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
        ReviewMedia media = new ReviewMedia();
        media.setId(MEDIA_ID);
        when(mediaRepository.findByIdAndReviewId(MEDIA_ID, REVIEW_ID)).thenReturn(Optional.of(media));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(1L); // only 1 → last one

        service.deleteMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, MEDIA_ID, USER_ID);

        verify(reviewIndexingService).indexReview(any(), any(Boolean.class));
    }

    @Test
    void deleteMedia_multipleMediaRemain_indexesWithStillHasMediaTrue() {
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review()));
        ReviewMedia media = new ReviewMedia();
        media.setId(MEDIA_ID);
        when(mediaRepository.findByIdAndReviewId(MEDIA_ID, REVIEW_ID)).thenReturn(Optional.of(media));
        when(mediaRepository.countByReviewId(REVIEW_ID)).thenReturn(3L); // 3 remaining → still has media

        service.deleteMedia(COMPANY_ID, PRODUCT_ID, REVIEW_ID, MEDIA_ID, USER_ID);

        verify(reviewIndexingService).indexReview(any(), any(Boolean.class));
    }

    // ─── listMedia ────────────────────────────────────────────────────────────

    @Test
    void listMedia_returnsResponsesFromRepository() {
        ReviewMedia m = new ReviewMedia();
        m.setId(MEDIA_ID);
        m.setUrl(VALID_URL);
        m.setPosition(0);
        when(mediaRepository.findByReviewIdOrderByPositionAsc(REVIEW_ID)).thenReturn(List.of(m));

        var result = service.listMedia(REVIEW_ID);

        assert result.size() == 1;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ProductReview review() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCompany(company);

        User reviewer = new User();
        reviewer.setId(USER_ID);

        ProductReview r = new ProductReview();
        r.setId(REVIEW_ID);
        r.setStatus(ReviewStatus.PUBLISHED);
        r.setProduct(product);
        r.setReviewer(reviewer);
        return r;
    }

    private AttachReviewMediaRequest request(String url) {
        AttachReviewMediaRequest req = new AttachReviewMediaRequest();
        req.setUrl(url);
        return req;
    }
}
