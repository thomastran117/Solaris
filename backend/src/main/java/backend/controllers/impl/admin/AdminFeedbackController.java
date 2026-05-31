package backend.controllers.impl.admin;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.feedback.UpdateFeedbackStatusRequest;
import backend.dtos.responses.feedback.FeedbackResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.FeedbackCategory;
import backend.models.enums.FeedbackStatus;
import backend.services.intf.feedback.FeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/admin/feedback")
@RequireAuth(roles = {"ADMIN"})
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<FeedbackResponse>> listAll(
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(required = false) FeedbackCategory category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(
                    feedbackService.listAllFeedback(resolveUserId(), status, category, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<FeedbackResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFeedbackStatusRequest request) {
        try {
            return ResponseEntity.ok(feedbackService.updateFeedbackStatus(id, resolveUserId(), request));
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
