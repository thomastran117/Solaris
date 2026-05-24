package backend.controllers.impl.feedback;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.feedback.SubmitFeedbackRequest;
import backend.dtos.responses.feedback.FeedbackResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.RateLimitService;
import backend.services.intf.feedback.FeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    private static final int FEEDBACK_LIMIT = 10;
    private static final int FEEDBACK_WINDOW_SECONDS = 3600;

    private final FeedbackService feedbackService;
    private final RateLimitService rateLimitService;

    public FeedbackController(FeedbackService feedbackService, RateLimitService rateLimitService) {
        this.feedbackService = feedbackService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<FeedbackResponse> submit(@Valid @RequestBody SubmitFeedbackRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("feedback:submit", userId.toString(), FEEDBACK_LIMIT, FEEDBACK_WINDOW_SECONDS);
            return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submitFeedback(userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/mine")
    @RequireAuth
    public ResponseEntity<PagedResponse<FeedbackResponse>> getMine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(feedbackService.getMyFeedback(resolveUserId(), page, size));
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
