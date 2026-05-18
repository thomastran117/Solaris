package backend.services.intf.products;

import java.util.UUID;
import backend.dtos.requests.review.ModerateReviewRequest;
import backend.dtos.requests.review.ReportReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewReportResponse;
import backend.models.enums.ReportStatus;

public interface ReviewReportService {
    void reportReview(UUID companyId, UUID productId, UUID reviewId, UUID reporterId, ReportReviewRequest request);
    PagedResponse<ReviewReportResponse> listReports(ReportStatus status, int page, int size);
    void moderate(UUID reviewId, UUID moderatorId, ModerateReviewRequest request);
}
