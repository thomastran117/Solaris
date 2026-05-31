package backend.services.intf.products;

import backend.dtos.requests.review.ModerateReviewRequest;

import java.util.UUID;

public interface ReviewModerationService {
    void moderate(UUID reviewId, UUID moderatorId, ModerateReviewRequest request);
}
