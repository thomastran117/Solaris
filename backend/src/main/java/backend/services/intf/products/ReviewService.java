package backend.services.intf.products;

import backend.dtos.requests.review.CreateReviewRequest;
import backend.dtos.requests.review.UpdateReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewResponse;
import backend.dtos.responses.review.ReviewSummaryResponse;

import java.util.List;

public interface ReviewService {
    PagedResponse<ReviewResponse> getReviews(
            long companyId,
            long productId,
            int page,
            int size,
            String sort,
            String direction,
            List<Integer> ratings,
            Boolean verifiedOnly,
            Boolean hasMedia);
    ReviewResponse getMyReview(long companyId, long productId, long userId);
    ReviewSummaryResponse getReviewSummary(long companyId, long productId);
    ReviewResponse createReview(long companyId, long productId, long userId, CreateReviewRequest request);
    ReviewResponse updateReview(long companyId, long productId, long userId, UpdateReviewRequest request);
    void deleteReview(long companyId, long productId, long userId);
}
