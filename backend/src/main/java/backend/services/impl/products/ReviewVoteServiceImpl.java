package backend.services.impl.products;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.dtos.responses.review.HelpfulVoteResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.core.ProductReview;
import backend.models.core.ReviewVote;
import backend.models.enums.ReviewStatus;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.repositories.ReviewVoteRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.products.ReviewVoteService;

@Service
public class ReviewVoteServiceImpl implements ReviewVoteService {

    private final ProductReviewRepository reviewRepository;
    private final ReviewVoteRepository voteRepository;
    private final ReviewMediaRepository mediaRepository;
    private final ReviewIndexingService reviewIndexingService;
    private final SingleFlightCache singleFlightCache;

    public ReviewVoteServiceImpl(
            ProductReviewRepository reviewRepository,
            ReviewVoteRepository voteRepository,
            ReviewMediaRepository mediaRepository,
            ReviewIndexingService reviewIndexingService,
            SingleFlightCache singleFlightCache) {
        this.reviewRepository = reviewRepository;
        this.voteRepository = voteRepository;
        this.mediaRepository = mediaRepository;
        this.reviewIndexingService = reviewIndexingService;
        this.singleFlightCache = singleFlightCache;
    }

    @Override
    @Transactional
    public HelpfulVoteResponse voteHelpful(UUID reviewId, UUID userId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (review.getStatus() != ReviewStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Review not found");
        }

        if (voteRepository.existsByReviewIdAndUserId(reviewId, userId)) {
            return new HelpfulVoteResponse(reviewId, review.getHelpfulCount(), true);
        }

        try {
            ReviewVote vote = new ReviewVote();
            vote.setReviewId(reviewId);
            vote.setUserId(userId);
            voteRepository.saveAndFlush(vote);
        } catch (DataIntegrityViolationException dup) {
            return new HelpfulVoteResponse(reviewId, review.getHelpfulCount(), true);
        }

        reviewRepository.incrementHelpfulCount(reviewId);

        UUID companyId = review.getProduct().getCompany().getId();
        UUID productId = review.getProduct().getId();
        review.setHelpfulCount(review.getHelpfulCount() + 1);
        boolean hasMedia = mediaRepository.countByReviewId(reviewId) > 0;
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + companyId + ":" + productId + ":*");
            reviewIndexingService.indexReview(review, hasMedia);
        });

        return new HelpfulVoteResponse(reviewId, review.getHelpfulCount(), true);
    }

    @Override
    @Transactional
    public HelpfulVoteResponse removeHelpful(UUID reviewId, UUID userId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        int deleted = voteRepository.deleteByReviewIdAndUserId(reviewId, userId);
        if (deleted == 0) {
            return new HelpfulVoteResponse(reviewId, review.getHelpfulCount(), false);
        }

        reviewRepository.decrementHelpfulCount(reviewId);

        UUID companyId = review.getProduct().getCompany().getId();
        UUID productId = review.getProduct().getId();
        int newCount = Math.max(0, review.getHelpfulCount() - 1);
        review.setHelpfulCount(newCount);
        boolean hasMedia = mediaRepository.countByReviewId(reviewId) > 0;
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + companyId + ":" + productId + ":*");
            reviewIndexingService.indexReview(review, hasMedia);
        });

        return new HelpfulVoteResponse(reviewId, newCount, false);
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
