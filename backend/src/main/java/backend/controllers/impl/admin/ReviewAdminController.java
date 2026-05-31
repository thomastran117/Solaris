package backend.controllers.impl.admin;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.review.ModerateReviewRequest;
import backend.dtos.responses.general.MessageResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.kafka.workers.IndexVersionManager;
import backend.kafka.workers.ReviewIndexingService;
import backend.services.intf.products.ReviewModerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/admin/reviews")
@RequireAuth(roles = {"ADMIN"})
public class ReviewAdminController {

    private final ReviewModerationService reviewModerationService;
    private final ReviewIndexingService reviewIndexingService;
    private final IndexVersionManager indexVersionManager;

    public ReviewAdminController(
            ReviewModerationService reviewModerationService,
            ReviewIndexingService reviewIndexingService,
            IndexVersionManager indexVersionManager) {
        this.reviewModerationService = reviewModerationService;
        this.reviewIndexingService = reviewIndexingService;
        this.indexVersionManager = indexVersionManager;
    }

    @PostMapping("/{reviewId}/moderate")
    public ResponseEntity<Void> moderate(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ModerateReviewRequest request) {
        try {
            UUID moderatorId = resolveUserId();
            reviewModerationService.moderate(reviewId, moderatorId, request);
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

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
