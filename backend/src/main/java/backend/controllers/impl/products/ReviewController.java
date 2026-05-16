package backend.controllers.impl.products;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.review.CreateReviewRequest;
import backend.dtos.requests.review.UpdateReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.RateLimitService;
import backend.services.intf.products.ReviewService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/companies/{companyId}/products/{productId}/reviews")
public class ReviewController {

    private static final int REVIEW_LIMIT = 5;
    private static final int REVIEW_WINDOW_SECONDS = 3600;

    private final ReviewService reviewService;
    private final RateLimitService rateLimitService;

    public ReviewController(ReviewService reviewService, RateLimitService rateLimitService) {
        this.reviewService = reviewService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ReviewResponse>> getReviews(
            @PathVariable long companyId,
            @PathVariable long productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        try {
            return ResponseEntity.ok(reviewService.getReviews(companyId, productId, page, size, sort, direction));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/me")
    @RequireAuth
    public ResponseEntity<ReviewResponse> getMyReview(
            @PathVariable long companyId,
            @PathVariable long productId) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody CreateReviewRequest request,
            HttpServletRequest httpRequest) {
        try {
            long userId = resolveUserId();
            rateLimitService.enforce("review:create", Long.toString(userId), REVIEW_LIMIT, REVIEW_WINDOW_SECONDS);
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody UpdateReviewRequest request) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId) {
        try {
            long userId = resolveUserId();
            reviewService.deleteReview(companyId, productId, userId);
            return ResponseEntity.noContent().build();
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
