package backend.services.impl.products;

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
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.models.enums.ReviewStatus;
import backend.exceptions.http.BadRequestException;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.UserRepository;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.products.ReviewService;

import java.time.Instant;
import java.util.Set;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "rating");

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ActivityEventPublisher activityEventPublisher;
    private final SingleFlightCache singleFlightCache;
    private final long cacheTtl;
    private final long cacheTtlShort;

    public ReviewServiceImpl(
            ProductReviewRepository reviewRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            ActivityEventPublisher activityEventPublisher,
            SingleFlightCache singleFlightCache,
            @Value("${app.product.cache-ttl-seconds:300}") long cacheTtl,
            @Value("${app.product.cache-ttl-short-seconds:60}") long cacheTtlShort) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.activityEventPublisher = activityEventPublisher;
        this.singleFlightCache = singleFlightCache;
        this.cacheTtl = cacheTtl;
        this.cacheTtlShort = cacheTtlShort;
    }

    @Override
    public PagedResponse<ReviewResponse> getReviews(long companyId, long productId, int page, int size, String sort, String direction) {
        resolveProduct(companyId, productId);
        final int clampedSize = Math.min(size, 50);
        String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
        String sortDir = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";
        String cacheKey = "reviews:" + companyId + ":" + productId + ":" + page + ":" + clampedSize + ":" + sortField + ":" + sortDir;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Pageable pageable = PageRequest.of(page, clampedSize,
                    Sort.by("asc".equals(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortField));
            return new PagedResponse<>(
                    reviewRepository.findAllByProductIdAndStatus(productId, ReviewStatus.PUBLISHED, pageable)
                            .map(this::toResponse));
        }, new TypeReference<PagedResponse<ReviewResponse>>() {});
    }

    @Override
    public ReviewResponse getMyReview(long companyId, long productId, long userId) {
        resolveProduct(companyId, productId);
        String cacheKey = "review:me:" + companyId + ":" + productId + ":" + userId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtlShort, () -> {
            ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));
            return toResponse(review);
        }, ReviewResponse.class);
    }

    @Override
    public ReviewResponse createReview(long companyId, long productId, long userId, CreateReviewRequest request) {
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

        ReviewResponse response = toResponse(reviewRepository.save(review));

        Long marketplaceId = product.getMarketplaceId();
        if (marketplaceId != null && request.getRating() != 3) {
            ActivityType type = request.getRating() >= 4 ? ActivityType.REVIEW_POSITIVE : ActivityType.REVIEW_NEGATIVE;
            activityEventPublisher.publish(new UserActivityEvent(
                    userId, null, productId, marketplaceId, type, Instant.now()));
        }

        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + companyId + ":" + productId + ":*");
            singleFlightCache.evict("review:me:" + companyId + ":" + productId + ":" + userId);
        });
        return response;
    }

    @Override
    public ReviewResponse updateReview(long companyId, long productId, long userId, UpdateReviewRequest request) {
        resolveProduct(companyId, productId);
        ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));

        if (request.getRating() != null) review.setRating(request.getRating());
        if (request.getTitle() != null) review.setTitle(request.getTitle());
        if (request.getBody() != null) review.setBody(request.getBody());

        ReviewResponse result = toResponse(reviewRepository.save(review));
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + companyId + ":" + productId + ":*");
            singleFlightCache.evict("review:me:" + companyId + ":" + productId + ":" + userId);
        });
        return result;
    }

    @Override
    public void deleteReview(long companyId, long productId, long userId) {
        resolveProduct(companyId, productId);
        ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));
        reviewRepository.delete(review);
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("reviews:" + companyId + ":" + productId + ":*");
            singleFlightCache.evict("review:me:" + companyId + ":" + productId + ":" + userId);
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

    private Product resolveProduct(long companyId, long productId) {
        return productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
    }

    private ReviewResponse toResponse(ProductReview review) {
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
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
