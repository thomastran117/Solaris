package backend.services.intf.products;

import java.util.UUID;
import backend.dtos.requests.review.CreateReviewRequest;
import backend.dtos.requests.review.UpdateReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewResponse;
import backend.dtos.responses.review.ReviewSummaryResponse;

import java.util.List;

public interface ReviewService {
    PagedResponse<ReviewResponse> getReviews(
            UUID companyId,
            UUID productId,
            int page,
            int size,
            String sort,
            String direction,
            List<Integer> ratings,
            Boolean verifiedOnly,
            Boolean hasMedia);
    ReviewResponse getMyReview(UUID companyId, UUID productId, UUID userId);
    ReviewSummaryResponse getReviewSummary(UUID companyId, UUID productId);
    ReviewResponse createReview(UUID companyId, UUID productId, UUID userId, CreateReviewRequest request);
    ReviewResponse updateReview(UUID companyId, UUID productId, UUID userId, UpdateReviewRequest request);
    void deleteReview(UUID companyId, UUID productId, UUID userId);
}
