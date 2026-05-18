package backend.services.impl.products;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.requests.review.AttachReviewMediaRequest;
import backend.dtos.responses.review.ReviewMediaResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.ProductReview;
import backend.models.core.ReviewMedia;
import backend.models.enums.UploadFolder;
import backend.kafka.workers.ReviewIndexingService;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.products.ReviewMediaService;

import java.util.List;

@Service
public class ReviewMediaServiceImpl implements ReviewMediaService {

    static final int MAX_MEDIA_PER_REVIEW = 5;

    private final ProductReviewRepository reviewRepository;
    private final ReviewMediaRepository mediaRepository;
    private final EnvironmentSetting environmentSetting;
    private final ReviewIndexingService reviewIndexingService;
    private final SingleFlightCache singleFlightCache;

    public ReviewMediaServiceImpl(
            ProductReviewRepository reviewRepository,
            ReviewMediaRepository mediaRepository,
            EnvironmentSetting environmentSetting,
            ReviewIndexingService reviewIndexingService,
            SingleFlightCache singleFlightCache) {
        this.reviewRepository = reviewRepository;
        this.mediaRepository = mediaRepository;
        this.environmentSetting = environmentSetting;
        this.reviewIndexingService = reviewIndexingService;
        this.singleFlightCache = singleFlightCache;
    }

    @Override
    @Transactional
    public ReviewMediaResponse attachMedia(long companyId, long productId, long reviewId, UUID userId, AttachReviewMediaRequest request) {
        ProductReview review = loadOwnedReview(companyId, productId, reviewId, userId);

        String url = request.getUrl();
        if (!isUrlInOurReviewMediaFolder(url, userId)) {
            throw new BadRequestException("Media URL must come from a presigned upload to the review-media folder");
        }

        long existing = mediaRepository.countByReviewId(reviewId);
        if (existing >= MAX_MEDIA_PER_REVIEW) {
            throw new BadRequestException("A review may include at most " + MAX_MEDIA_PER_REVIEW + " photos");
        }

        ReviewMedia media = new ReviewMedia();
        media.setReviewId(reviewId);
        media.setUrl(url);
        media.setPosition((int) existing);
        ReviewMedia saved = mediaRepository.save(media);

        long pid = review.getProduct().getId();
        long cid = review.getProduct().getCompany().getId();
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + cid + ":" + pid + ":*");
            singleFlightCache.evict("review:me:" + cid + ":" + pid + ":" + userId);
            reviewIndexingService.indexReview(review, true);
        });

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteMedia(long companyId, long productId, long reviewId, long mediaId, UUID userId) {
        ProductReview review = loadOwnedReview(companyId, productId, reviewId, userId);
        ReviewMedia media = mediaRepository.findByIdAndReviewId(mediaId, reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        long countBefore = mediaRepository.countByReviewId(reviewId);
        mediaRepository.delete(media);

        long pid = review.getProduct().getId();
        long cid = review.getProduct().getCompany().getId();
        boolean stillHasMedia = countBefore > 1;
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + cid + ":" + pid + ":*");
            singleFlightCache.evict("review:me:" + cid + ":" + pid + ":" + userId);
            reviewIndexingService.indexReview(review, stillHasMedia);
        });
    }

    @Override
    public List<ReviewMediaResponse> listMedia(long reviewId) {
        return mediaRepository.findByReviewIdOrderByPositionAsc(reviewId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ProductReview loadOwnedReview(long companyId, long productId, long reviewId, UUID userId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (review.getProduct() == null
                || review.getProduct().getId() != productId
                || review.getProduct().getCompany() == null
                || review.getProduct().getCompany().getId() != companyId) {
            throw new ResourceNotFoundException("Review not found");
        }
        if (review.getReviewer() == null || review.getReviewer().getId() != userId) {
            throw new ForbiddenException("You can only modify your own review");
        }
        return review;
    }

    private boolean isUrlInOurReviewMediaFolder(String url, UUID userId) {
        if (url == null || url.isBlank()) return false;
        EnvironmentSetting.S3 s3 = environmentSetting.getS3();
        String base = s3.getPublicUrlBase();
        String expectedPrefix;
        if (base != null && !base.isBlank()) {
            expectedPrefix = base.stripTrailing() + "/" + UploadFolder.REVIEW_MEDIA.getPath() + "/" + userId + "/";
        } else {
            expectedPrefix = "https://" + s3.getBucket() + ".s3." + s3.getRegion() + ".amazonaws.com/"
                    + UploadFolder.REVIEW_MEDIA.getPath() + "/" + userId + "/";
        }
        return url.startsWith(expectedPrefix);
    }

    private ReviewMediaResponse toResponse(ReviewMedia media) {
        return new ReviewMediaResponse(media.getId(), media.getUrl(), media.getPosition());
    }

    private void evictAfterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { eviction.run(); }
            });
        } else {
            eviction.run();
        }
    }
}
