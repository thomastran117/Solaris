package backend.services.intf.products;

import backend.dtos.requests.review.AttachReviewMediaRequest;
import backend.dtos.responses.review.ReviewMediaResponse;

import java.util.List;

public interface ReviewMediaService {
    ReviewMediaResponse attachMedia(long companyId, long productId, long reviewId, long userId, AttachReviewMediaRequest request);
    void deleteMedia(long companyId, long productId, long reviewId, long mediaId, long userId);
    List<ReviewMediaResponse> listMedia(long reviewId);
}
