package backend.controllers.impl.products;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.review.AttachReviewMediaRequest;
import backend.dtos.requests.review.CreateReviewRequest;
import backend.dtos.requests.review.ReportReviewRequest;
import backend.dtos.requests.review.UpdateReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.HelpfulVoteResponse;
import backend.dtos.responses.review.ReviewMediaResponse;
import backend.dtos.responses.review.ReviewResponse;
import backend.dtos.responses.review.ReviewSearchHit;
import backend.dtos.responses.review.ReviewSummaryResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.RateLimitService;
import backend.services.intf.products.ReviewMediaService;
import backend.services.intf.products.ReviewReportService;
import backend.services.intf.products.ReviewSearchService;
import backend.services.intf.products.ReviewService;
import backend.services.intf.products.ReviewVoteService;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/companies/{companyId}/products/{productId}/reviews")
public class ReviewController {

    private static final int REVIEW_LIMIT = 5;
    private static final int REVIEW_WINDOW_SECONDS = 3600;
    private static final int VOTE_LIMIT = 60;
    private static final int VOTE_WINDOW_SECONDS = 3600;
    private static final int REPORT_LIMIT = 5;
    private static final int REPORT_WINDOW_SECONDS = 86400;

    private final ReviewService reviewService;
    private final ReviewVoteService reviewVoteService;
    private final ReviewMediaService reviewMediaService;
    private final ReviewReportService reviewReportService;
    private final ReviewSearchService reviewSearchService;
    private final RateLimitService rateLimitService;

    public ReviewController(ReviewService reviewService, ReviewVoteService reviewVoteService,
                            ReviewMediaService reviewMediaService, ReviewReportService reviewReportService,
                            ReviewSearchService reviewSearchService, RateLimitService rateLimitService) {
        this.reviewService = reviewService;
        this.reviewVoteService = reviewVoteService;
        this.reviewMediaService = reviewMediaService;
        this.reviewReportService = reviewReportService;
        this.reviewSearchService = reviewSearchService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ReviewResponse>> getReviews(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "^[a-zA-Z.]+$", message = "Invalid sort field") String sort,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "^(?i)(asc|desc)$", message = "Direction must be asc or desc") String direction,
            @RequestParam(required = false) List<Integer> rating,
            @RequestParam(required = false) Boolean verifiedOnly,
            @RequestParam(required = false) Boolean hasMedia) {
        try {
            return ResponseEntity.ok(
                    reviewService.getReviews(companyId, productId, page, size, sort, direction, rating, verifiedOnly, hasMedia));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<ReviewSearchHit>> searchReviews(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Integer> rating,
            @RequestParam(required = false) Boolean verifiedOnly,
            @RequestParam(defaultValue = "relevance") @Pattern(regexp = "^[a-zA-Z]+$", message = "Invalid sort field") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(
                    reviewSearchService.search(companyId, productId, q, rating, verifiedOnly, sort, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<ReviewSummaryResponse> getSummary(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            return ResponseEntity.ok(reviewService.getReviewSummary(companyId, productId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/me")
    @RequireAuth
    public ResponseEntity<ReviewResponse> getMyReview(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(reviewService.getMyReview(companyId, productId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateReviewRequest request,
            HttpServletRequest httpRequest) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("review:create", userId.toString(), REVIEW_LIMIT, REVIEW_WINDOW_SECONDS);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(reviewService.createReview(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/me")
    @RequireAuth
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateReviewRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(reviewService.updateReview(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/me")
    @RequireAuth
    public ResponseEntity<Void> deleteReview(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            UUID userId = resolveUserId();
            reviewService.deleteReview(companyId, productId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{reviewId}/helpful")
    @RequireAuth
    public ResponseEntity<HelpfulVoteResponse> voteHelpful(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID reviewId) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("review:vote", userId.toString(), VOTE_LIMIT, VOTE_WINDOW_SECONDS);
            return ResponseEntity.ok(reviewVoteService.voteHelpful(reviewId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{reviewId}/helpful")
    @RequireAuth
    public ResponseEntity<HelpfulVoteResponse> removeHelpful(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID reviewId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(reviewVoteService.removeHelpful(reviewId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{reviewId}/report")
    @RequireAuth
    public ResponseEntity<Void> reportReview(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReportReviewRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("review:report", userId.toString(), REPORT_LIMIT, REPORT_WINDOW_SECONDS);
            reviewReportService.reportReview(companyId, productId, reviewId, userId, request);
            return ResponseEntity.accepted().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{reviewId}/media")
    @RequireAuth
    public ResponseEntity<ReviewMediaResponse> attachMedia(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody AttachReviewMediaRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(reviewMediaService.attachMedia(companyId, productId, reviewId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{reviewId}/media/{mediaId}")
    @RequireAuth
    public ResponseEntity<Void> deleteMedia(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID reviewId,
            @PathVariable UUID mediaId) {
        try {
            UUID userId = resolveUserId();
            reviewMediaService.deleteMedia(companyId, productId, reviewId, mediaId, userId);
            return ResponseEntity.noContent().build();
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
