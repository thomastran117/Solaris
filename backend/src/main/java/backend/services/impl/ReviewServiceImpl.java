package backend.services.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.UserRepository;
import backend.services.intf.ReviewService;

import java.util.Set;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "rating");

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewServiceImpl(
            ProductReviewRepository reviewRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PagedResponse<ReviewResponse> getReviews(long companyId, long productId, int page, int size, String sort, String direction) {
        resolveProduct(companyId, productId);

        if (size > 50) size = 50;

        String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortField));

        return new PagedResponse<>(
                reviewRepository.findAllByProductIdAndStatus(productId, ReviewStatus.PUBLISHED, pageable)
                        .map(this::toResponse)
        );
    }

    @Override
    public ReviewResponse getMyReview(long companyId, long productId, long userId) {
        resolveProduct(companyId, productId);
        ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));
        return toResponse(review);
    }

    @Override
    public ReviewResponse createReview(long companyId, long productId, long userId, CreateReviewRequest request) {
        Product product = resolveProduct(companyId, productId);
        User reviewer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (reviewRepository.existsByProductIdAndReviewerId(productId, userId)) {
            throw new ConflictException("You have already reviewed this product. Please update your existing review instead.");
        }

        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setReviewer(reviewer);
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setBody(request.getBody());

        return toResponse(reviewRepository.save(review));
    }

    @Override
    public ReviewResponse updateReview(long companyId, long productId, long userId, UpdateReviewRequest request) {
        resolveProduct(companyId, productId);
        ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));

        if (request.getRating() != null) review.setRating(request.getRating());
        if (request.getTitle() != null) review.setTitle(request.getTitle());
        if (request.getBody() != null) review.setBody(request.getBody());

        return toResponse(reviewRepository.save(review));
    }

    @Override
    public void deleteReview(long companyId, long productId, long userId) {
        resolveProduct(companyId, productId);
        ProductReview review = reviewRepository.findByProductIdAndReviewerId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product yet"));
        reviewRepository.delete(review);
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
