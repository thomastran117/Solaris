package backend.services.intf;

import backend.dtos.requests.review.CreateReviewRequest;
import backend.dtos.requests.review.UpdateReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewResponse;

public interface ReviewService {
    PagedResponse<ReviewResponse> getReviews(long companyId, long productId, int page, int size, String sort, String direction);
    ReviewResponse getMyReview(long companyId, long productId, long userId);
    ReviewResponse createReview(long companyId, long productId, long userId, CreateReviewRequest request);
    ReviewResponse updateReview(long companyId, long productId, long userId, UpdateReviewRequest request);
    void deleteReview(long companyId, long productId, long userId);
}
