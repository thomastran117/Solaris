package backend.services.impl.products;

import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.services.impl.SingleFlightCache;

import backend.dtos.requests.review.CreateReviewRequest;
import backend.dtos.requests.review.UpdateReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewResponse;
import backend.dtos.responses.review.ReviewSummaryResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.models.enums.ReviewStatus;
import backend.exceptions.http.BadRequestException;
import backend.dtos.responses.review.ReviewMediaResponse;
import backend.models.core.ReviewMedia;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewMediaRepository;
import backend.repositories.UserRepository;
import backend.repositories.specifications.ReviewSpecification;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.kafka.workers.ReviewIndexingService;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.products.ReviewService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "rating", "helpfulCount");

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ReviewMediaRepository mediaRepository;
    private final ActivityEventPublisher activityEventPublisher;
    private final ReviewIndexingService reviewIndexingService;
    private final SingleFlightCache singleFlightCache;
    private final long cacheTtl;
    private final long cacheTtlShort;

    public ReviewServiceImpl(
            ProductReviewRepository reviewRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            ReviewMediaRepository mediaRepository,
            ActivityEventPublisher activityEventPublisher,
            ReviewIndexingService reviewIndexingService,
            SingleFlightCache singleFlightCache,
            @Value("${app.product.cache-ttl-seconds:300}") long cacheTtl,
            @Value("${app.product.cache-ttl-short-seconds:60}") long cacheTtlShort) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.mediaRepository = mediaRepository;
        this.activityEventPublisher = activityEventPublisher;
        this.reviewIndexingService = reviewIndexingService;
        this.singleFlightCache = singleFlightCache;
        this.cacheTtl = cacheTtl;
        this.cacheTtlShort = cacheTtlShort;
    }

    @Override
    public PagedResponse<ReviewResponse> getReviews(UUID companyId, UUID productId, int page, int size, String sort, String direction,
                                                     List<Integer> ratings, Boolean verifiedOnly, Boolean hasMedia) {
        resolveProduct(companyId, productId);
        final int clampedSize = Math.min(size, 50);
        String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
        String sortDir = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";

        List<Integer> normalizedRatings = ratings == null
                ? List.of()
                : ratings.stream().filter(r -> r != null && r >= 1 && r <= 5).distinct().sorted().toList();
        boolean verified = Boolean.TRUE.equals(verifiedOnly);
        boolean media = Boolean.TRUE.equals(hasMedia);

        String ratingsKey = normalizedRatings.isEmpty() ? "-" :
                normalizedRatings.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String cacheKey = "reviews:" + companyId + ":" + productId + ":" + page + ":" + clampedSize
                + ":" + sortField + ":" + sortDir + ":r=" + ratingsKey + ":v=" + verified + ":m=" + media;

        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Pageable pageable = PageRequest.of(page, clampedSize,
                    Sort.by("asc".equals(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortField));
            var spec = ReviewSpecification.withFilters(productId, normalizedRatings, verified, media);
            var pageOfReviews = reviewRepository.findAll(spec, pageable);
            Map<UUID, List<ReviewMediaResponse>> mediaByReview = fetchMediaForReviewIds(
                    pageOfReviews.map(ProductReview::getId).getContent());
            return new PagedResponse<>(pageOfReviews.map(r -> toResponse(r, mediaByReview.getOrDefault(r.getId(), List.of()))));
        }, new TypeReference<PagedResponse<ReviewResponse>>() {});
    }

    @Override
    public ReviewSummaryResponse getReviewSummary(UUID companyId, UUID productId) {
        resolveProduct(companyId, productId);
        String cacheKey = "reviews:summary:" + companyId + ":" + productId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Object[] aggregates = reviewRepository.findSummaryAggregates(productId);
            double avg = 0d;
            long total = 0L;
            long verified = 0L;
            if (aggregates != null && aggregates.length >= 3) {
                Object rawAvg = aggregates[0];
                Object rawCount = aggregates[1];
                Object rawVerified = aggregates[2];
                if (rawAvg instanceof Number n) avg = n.doubleValue();
                if (rawCount instanceof Number n) total = n.longValue();
                if (rawVerified instanceof Number n) verified = n.longValue();
            }

            Map<Integer, Long> distribution = new LinkedHashMap<>();
            for (int star = 5; star >= 1; star--) distribution.put(star, 0L);
            for (Object[] row : reviewRepository.findRatingDistribution(productId)) {
                int rating = ((Number) row[0]).intValue();
                long count = ((Number) row[1]).longValue();
                distribution.put(rating, count);
            }

            return new ReviewSummaryResponse(productId, avg, total, verified, distribution);
        }, ReviewSummaryResponse.class);
    }

    @Override
    public ReviewResponse getMyReview(UUID companyId, UUID productId, UUID userId) {
        resolveProduct(companyId, productId);
        String cacheKey = "review:me:" + companyId + ":" + productId + ":" + userId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtlShort, () -> {
            ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));
            return toResponse(review);
        }, ReviewResponse.class);
    }

    @Override
    public ReviewResponse createReview(UUID companyId, UUID productId, UUID userId, CreateReviewRequest request) {
        Product product = resolveProduct(companyId, productId);
        User reviewer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!orderRepository.existsDeliveredOrShippedOrderForProduct(userId, productId)) {
            throw new BadRequestException("You must have a delivered or shipped order for this product to leave a review");
        }

        if (reviewRepository.existsByProductIdAndReviewerId(productId, userId)) {
            throw new ConflictException("You have already reviewed this product. Please update your existing review instead.");
        }

        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setReviewer(reviewer);
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setBody(request.getBody());
        review.setVerifiedPurchase(true);

        ProductReview saved = reviewRepository.save(review);
        ReviewResponse response = toResponse(saved);

        Long marketplaceId = product.getMarketplaceId();
        if (marketplaceId != null && request.getRating() != 3) {
            ActivityType type = request.getRating() >= 4 ? ActivityType.REVIEW_POSITIVE : ActivityType.REVIEW_NEGATIVE;
            activityEventPublisher.publish(new UserActivityEvent(
                    userId, null, productId, marketplaceId, type, Instant.now()));
        }

        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + companyId + ":" + productId + ":*");
            singleFlightCache.evict("review:me:" + companyId + ":" + productId + ":" + userId);
            singleFlightCache.evict("reviews:summary:" + companyId + ":" + productId);
            reviewIndexingService.indexReview(saved, false);
        });
        return response;
    }

    @Override
    public ReviewResponse updateReview(UUID companyId, UUID productId, UUID userId, UpdateReviewRequest request) {
        resolveProduct(companyId, productId);
        ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));

        if (request.getRating() != null) review.setRating(request.getRating());
        if (request.getTitle() != null) review.setTitle(request.getTitle());
        if (request.getBody() != null) review.setBody(request.getBody());

        ProductReview saved = reviewRepository.save(review);
        ReviewResponse result = toResponse(saved);
        boolean hasMedia = mediaRepository.countByReviewId(saved.getId()) > 0;
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + companyId + ":" + productId + ":*");
            singleFlightCache.evict("review:me:" + companyId + ":" + productId + ":" + userId);
            singleFlightCache.evict("reviews:summary:" + companyId + ":" + productId);
            reviewIndexingService.indexReview(saved, hasMedia);
        });
        return result;
    }

    @Override
    public void deleteReview(UUID companyId, UUID productId, UUID userId) {
        resolveProduct(companyId, productId);
        ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));
        UUID reviewId = review.getId();
        reviewRepository.delete(review);
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + companyId + ":" + productId + ":*");
            singleFlightCache.evict("review:me:" + companyId + ":" + productId + ":" + userId);
            singleFlightCache.evict("reviews:summary:" + companyId + ":" + productId);
            reviewIndexingService.removeReview(reviewId);
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

    private Product resolveProduct(UUID companyId, UUID productId) {
        return productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
    }

    private ReviewResponse toResponse(ProductReview review) {
        List<ReviewMediaResponse> media = mediaRepository.findByReviewIdOrderByPositionAsc(review.getId()).stream()
                .map(this::mediaToResponse)
                .toList();
        return toResponse(review, media);
    }

    private ReviewResponse toResponse(ProductReview review, List<ReviewMediaResponse> media) {
        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getReviewer().getId(),
                review.getReviewer().getFirstName(),
                review.getReviewer().getLastName(),
                review.getRating(),
                review.getTitle(),
                review.getBody(),
                review.getStatus().name(),
                review.isVerifiedPurchase(),
                review.getHelpfulCount(),
                media,
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private Map<UUID, List<ReviewMediaResponse>> fetchMediaForReviewIds(Collection<UUID> reviewIds) {
        if (reviewIds.isEmpty()) return Map.of();
        Map<UUID, List<ReviewMediaResponse>> grouped = new HashMap<>();
        for (ReviewMedia m : mediaRepository.findByReviewIdInOrderByReviewIdAscPositionAsc(reviewIds)) {
            grouped.computeIfAbsent(m.getReviewId(), k -> new ArrayList<>()).add(mediaToResponse(m));
        }
        return grouped;
    }

    private ReviewMediaResponse mediaToResponse(ReviewMedia m) {
        return new ReviewMediaResponse(m.getId(), m.getUrl(), m.getPosition());
    }
}
