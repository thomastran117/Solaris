package backend.controllers.impl.feedback;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.feedback.SubmitFeedbackRequest;
import backend.dtos.responses.feedback.FeedbackResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.TooManyRequestException;
import backend.models.enums.FeedbackCategory;
import backend.services.intf.RateLimitService;
import backend.services.intf.feedback.FeedbackService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeedbackControllerTest {

    private static final UUID USER_ID     = TestIds.uuid(1);
    private static final UUID FEEDBACK_ID = TestIds.uuid(2);

    private FeedbackService feedbackService;
    private RateLimitService rateLimitService;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        feedbackService  = mock(FeedbackService.class);
        rateLimitService = mock(RateLimitService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedbackController(feedbackService, rateLimitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /feedback ───────────────────────────────────────────────────────

    @Test
    void submitFeedback_withinRateLimit_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(feedbackService.submitFeedback(eq(USER_ID), any())).thenReturn(makeFeedbackResponse());

        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeSubmitRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("BUG_REPORT"));
    }

    @Test
    void submitFeedback_rateLimitExceeded_returns429() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new TooManyRequestException())
                .when(rateLimitService).enforce(anyString(), anyString(), anyInt(), anyInt());

        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeSubmitRequest())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void submitFeedback_serviceThrows_returns500() throws Exception {
        authenticateAs(USER_ID);
        when(feedbackService.submitFeedback(any(), any())).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeSubmitRequest())))
                .andExpect(status().isInternalServerError());
    }

    // ─── GET /feedback/mine ───────────────────────────────────────────────────

    @Test
    void getMyFeedback_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(feedbackService.getMyFeedback(eq(USER_ID), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/feedback/mine"))
                .andExpect(status().isOk());
    }

    @Test
    void getMyFeedback_serviceThrows_returns500() throws Exception {
        authenticateAs(USER_ID);
        when(feedbackService.getMyFeedback(any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/feedback/mine"))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private FeedbackResponse makeFeedbackResponse() {
        return new FeedbackResponse(
                FEEDBACK_ID, USER_ID, "alice@example.com", "Alice Smith",
                "BUG_REPORT", "OPEN",
                "The checkout page freezes on mobile.", null,
                "/checkout", null, null,
                Instant.now(), Instant.now());
    }

    private SubmitFeedbackRequest makeSubmitRequest() {
        return new SubmitFeedbackRequest(
                FeedbackCategory.BUG_REPORT,
                "The checkout page freezes on mobile when applying a coupon.",
                null, "/checkout");
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
