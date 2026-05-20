package backend.controllers.impl.products;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.review.CreateReviewRequest;
import backend.dtos.requests.review.UpdateReviewRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.HelpfulVoteResponse;
import backend.dtos.responses.review.ReviewResponse;
import backend.dtos.responses.review.ReviewSummaryResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.TooManyRequestException;
import backend.kafka.workers.ReviewIndexingService;
import backend.services.intf.RateLimitService;
import backend.services.intf.products.ReviewMediaService;
import backend.services.intf.products.ReviewReportService;
import backend.services.intf.products.ReviewSearchService;
import backend.services.intf.products.ReviewService;
import backend.services.intf.products.ReviewVoteService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReviewControllerTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID PRODUCT_ID  = TestIds.uuid(2);
    private static final UUID USER_ID     = TestIds.uuid(3);
    private static final UUID REVIEW_ID   = TestIds.uuid(4);

    private ReviewService reviewService;
    private ReviewVoteService reviewVoteService;
    private ReviewMediaService reviewMediaService;
    private ReviewReportService reviewReportService;
    private ReviewSearchService reviewSearchService;
    private RateLimitService rateLimitService;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        reviewService        = mock(ReviewService.class);
        reviewVoteService    = mock(ReviewVoteService.class);
        reviewMediaService   = mock(ReviewMediaService.class);
        reviewReportService  = mock(ReviewReportService.class);
        reviewSearchService  = mock(ReviewSearchService.class);
        rateLimitService     = mock(RateLimitService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReviewController(reviewService, reviewVoteService,
                                reviewMediaService, reviewReportService,
                                reviewSearchService, rateLimitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /companies/{companyId}/products/{productId}/reviews ──────────────

    @Test
    void getReviews_returns200() throws Exception {
        when(reviewService.getReviews(any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/companies/{cid}/products/{pid}/reviews", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getReviews_serviceError_returns500() throws Exception {
        when(reviewService.getReviews(any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/companies/{cid}/products/{pid}/reviews", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isInternalServerError());
    }

    // ─── GET /companies/{companyId}/products/{productId}/reviews/summary ──────

    @Test
    void getSummary_returns200() throws Exception {
        when(reviewService.getReviewSummary(COMPANY_ID, PRODUCT_ID))
                .thenReturn(new ReviewSummaryResponse(PRODUCT_ID, 4.5, 10L, 7L, Map.of(5, 7L, 4, 3L)));

        mockMvc.perform(get("/companies/{cid}/products/{pid}/reviews/summary", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10));
    }

    // ─── GET /companies/{companyId}/products/{productId}/reviews/me ──────────

    @Test
    void getMyReview_authenticated_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.getMyReview(COMPANY_ID, PRODUCT_ID, USER_ID))
                .thenReturn(makeReviewResponse());

        mockMvc.perform(get("/companies/{cid}/products/{pid}/reviews/me", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void getMyReview_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.getMyReview(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/companies/{cid}/products/{pid}/reviews/me", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{companyId}/products/{productId}/reviews ─────────────

    @Test
    void createReview_withinRateLimit_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.createReview(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(makeReviewResponse());

        mockMvc.perform(post("/companies/{cid}/products/{pid}/reviews", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateReviewRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void createReview_rateLimitExceeded_returns429() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new TooManyRequestException())
                .when(rateLimitService).enforce(anyString(), anyString(), anyInt(), anyInt());

        mockMvc.perform(post("/companies/{cid}/products/{pid}/reviews", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateReviewRequest())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void createReview_noDeliveredOrder_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.createReview(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("no delivered order"));

        mockMvc.perform(post("/companies/{cid}/products/{pid}/reviews", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateReviewRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReview_duplicateReview_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.createReview(any(), any(), any(), any()))
                .thenThrow(new ConflictException("already reviewed"));

        mockMvc.perform(post("/companies/{cid}/products/{pid}/reviews", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateReviewRequest())))
                .andExpect(status().isConflict());
    }

    // ─── PATCH /companies/{companyId}/products/{productId}/reviews/me ─────────

    @Test
    void updateReview_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(reviewService.updateReview(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(makeReviewResponse());

        UpdateReviewRequest req = new UpdateReviewRequest();
        req.setRating(4);

        mockMvc.perform(patch("/companies/{cid}/products/{pid}/reviews/me", COMPANY_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ─── DELETE /companies/{companyId}/products/{productId}/reviews/me ─────────

    @Test
    void deleteReview_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/{cid}/products/{pid}/reviews/me", COMPANY_ID, PRODUCT_ID))
                .andExpect(status().isNoContent());

        verify(reviewService).deleteReview(COMPANY_ID, PRODUCT_ID, USER_ID);
    }

    // ─── POST /companies/{companyId}/products/{productId}/reviews/{reviewId}/helpful

    @Test
    void voteHelpful_withinRateLimit_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(reviewVoteService.voteHelpful(REVIEW_ID, USER_ID))
                .thenReturn(new HelpfulVoteResponse(REVIEW_ID, 5, true));

        mockMvc.perform(post("/companies/{cid}/products/{pid}/reviews/{rid}/helpful",
                        COMPANY_ID, PRODUCT_ID, REVIEW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(5));
    }

    @Test
    void voteHelpful_rateLimitExceeded_returns429() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new TooManyRequestException())
                .when(rateLimitService).enforce(anyString(), anyString(), anyInt(), anyInt());

        mockMvc.perform(post("/companies/{cid}/products/{pid}/reviews/{rid}/helpful",
                        COMPANY_ID, PRODUCT_ID, REVIEW_ID))
                .andExpect(status().isTooManyRequests());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ReviewResponse makeReviewResponse() {
        return new ReviewResponse(
                REVIEW_ID, PRODUCT_ID, USER_ID, "John", "Doe",
                5, "Great product", "Loved it", "PUBLISHED",
                true, 0, List.of(), Instant.now(), Instant.now());
    }

    private CreateReviewRequest makeCreateReviewRequest() {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setRating(5);
        req.setTitle("Great product");
        req.setBody("Loved it.");
        return req;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
