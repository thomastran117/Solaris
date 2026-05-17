package backend.services.intf.products;

import backend.dtos.requests.review.ModerateReviewRequest;
import backend.dtos.requests.review.ReportReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewReportResponse;
import backend.models.enums.ReportStatus;

public interface ReviewReportService {
    void reportReview(long companyId, long productId, long reviewId, long reporterId, ReportReviewRequest request);
    PagedResponse<ReviewReportResponse> listReports(ReportStatus status, int page, int size);
    void moderate(long reviewId, long moderatorId, ModerateReviewRequest request);
}
