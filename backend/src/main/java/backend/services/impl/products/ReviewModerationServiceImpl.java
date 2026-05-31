package backend.services.impl.products;

import backend.dtos.requests.review.ModerateReviewRequest;
import backend.exceptions.http.ResourceNotFoundException;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.core.ProductReview;
import backend.models.enums.ModerationAction;
import backend.models.enums.ReviewStatus;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.products.ReviewModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
public class ReviewModerationServiceImpl implements ReviewModerationService {

    private final ProductReviewRepository reviewRepository;
    private final ReviewMediaRepository mediaRepository;
    private final ReviewIndexingService reviewIndexingService;
    private final SingleFlightCache singleFlightCache;

    public ReviewModerationServiceImpl(
            ProductReviewRepository reviewRepository,
            ReviewMediaRepository mediaRepository,
            ReviewIndexingService reviewIndexingService,
            SingleFlightCache singleFlightCache) {
        this.reviewRepository = reviewRepository;
        this.mediaRepository = mediaRepository;
        this.reviewIndexingService = reviewIndexingService;
        this.singleFlightCache = singleFlightCache;
    }

    @Override
    @Transactional
    public void moderate(UUID reviewId, UUID moderatorId, ModerateReviewRequest request) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        ModerationAction action = request.getAction();
        ReviewStatus targetStatus = switch (action) {
            case PUBLISH -> ReviewStatus.PUBLISHED;
            case HIDE -> ReviewStatus.HIDDEN;
            case REMOVE -> ReviewStatus.REMOVED;
        };
        reviewRepository.updateStatusIfDifferent(reviewId, targetStatus.name());
        review.setStatus(targetStatus);

        UUID cid = review.getProduct().getCompany().getId();
        UUID pid = review.getProduct().getId();
        boolean hasMedia = mediaRepository.countByReviewId(reviewId) > 0;
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + cid + ":" + pid + ":*");
            singleFlightCache.evict("reviews:summary:" + cid + ":" + pid);
            if (review.getReviewer() != null) {
                singleFlightCache.evict("review:me:" + cid + ":" + pid + ":" + review.getReviewer().getId());
            }
            if (action == ModerationAction.REMOVE) {
                reviewIndexingService.removeReview(reviewId);
            } else {
                reviewIndexingService.indexReview(review, hasMedia);
            }
        });
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
