package backend.services.intf.products;

import java.util.UUID;
import backend.dtos.requests.review.AttachReviewMediaRequest;
import backend.dtos.responses.review.ReviewMediaResponse;

import java.util.List;

public interface ReviewMediaService {
    ReviewMediaResponse attachMedia(UUID companyId, UUID productId, UUID reviewId, UUID userId, AttachReviewMediaRequest request);
    void deleteMedia(UUID companyId, UUID productId, UUID reviewId, UUID mediaId, UUID userId);
    List<ReviewMediaResponse> listMedia(UUID reviewId);
}
