package backend.controllers.impl.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.review.ModerateReviewRequest;
import backend.dtos.responses.general.MessageResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewReportResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.kafka.workers.IndexVersionManager;
import backend.kafka.workers.ReviewIndexingService;
import backend.models.enums.ReportStatus;
import backend.services.intf.products.ReviewReportService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/admin/reviews")
@RequireAuth(roles = {"ADMIN"})
public class ReviewModerationController {

    private final ReviewReportService reviewReportService;
    private final ReviewIndexingService reviewIndexingService;
    private final IndexVersionManager indexVersionManager;

    public ReviewModerationController(ReviewReportService reviewReportService,
                                       ReviewIndexingService reviewIndexingService,
                                       IndexVersionManager indexVersionManager) {
        this.reviewReportService = reviewReportService;
        this.reviewIndexingService = reviewIndexingService;
        this.indexVersionManager = indexVersionManager;
    }

    @GetMapping("/reports")
    public ResponseEntity<PagedResponse<ReviewReportResponse>> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(reviewReportService.listReports(status, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{reviewId}/moderate")
    public ResponseEntity<Void> moderate(
            @PathVariable long reviewId,
            @Valid @RequestBody ModerateReviewRequest request) {
        try {
            long moderatorId = resolveUserId();
            reviewReportService.moderate(reviewId, moderatorId, request);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/reindex")
    public ResponseEntity<MessageResponse> reindex() {
        try {
            indexVersionManager.rolloverIndex("reviews");
            reviewIndexingService.reindexAll();
            return ResponseEntity.ok(new MessageResponse("Review index rollover and full reindex triggered"));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
