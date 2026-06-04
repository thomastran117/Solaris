package backend.controllers.impl.admin;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.feedback.UpdateFeedbackStatusRequest;
import backend.dtos.responses.feedback.FeedbackResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.FeedbackStatus;
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

class AdminFeedbackControllerTest {

    private static final UUID ADMIN_ID    = TestIds.uuid(1);
    private static final UUID FEEDBACK_ID = TestIds.uuid(2);

    private FeedbackService feedbackService;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        feedbackService = mock(FeedbackService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminFeedbackController(feedbackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /admin/feedback ──────────────────────────────────────────────────

    @Test
    void listAll_noFilters_returns200() throws Exception {
        authenticateAs(ADMIN_ID);
        when(feedbackService.listAllFeedback(eq(ADMIN_ID), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/admin/feedback"))
                .andExpect(status().isOk());
    }

    @Test
    void listAll_withStatusFilter_returns200() throws Exception {
        authenticateAs(ADMIN_ID);
        when(feedbackService.listAllFeedback(eq(ADMIN_ID), eq(FeedbackStatus.OPEN), isNull(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(makeFeedbackResponse()), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/admin/feedback").param("status", "OPEN"))
                .andExpect(status().isOk());
    }

    @Test
    void listAll_serviceThrows_returns500() throws Exception {
        authenticateAs(ADMIN_ID);
        when(feedbackService.listAllFeedback(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/admin/feedback"))
                .andExpect(status().isInternalServerError());
    }

    // ─── PATCH /admin/feedback/{id}/status ───────────────────────────────────

    @Test
    void updateStatus_returns200() throws Exception {
        authenticateAs(ADMIN_ID);
        when(feedbackService.updateFeedbackStatus(eq(FEEDBACK_ID), eq(ADMIN_ID), any()))
                .thenReturn(makeFeedbackResponse());

        mockMvc.perform(patch("/admin/feedback/{id}/status", FEEDBACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateFeedbackStatusRequest(FeedbackStatus.UNDER_REVIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void updateStatus_notFound_returns404() throws Exception {
        authenticateAs(ADMIN_ID);
        when(feedbackService.updateFeedbackStatus(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Feedback not found"));

        mockMvc.perform(patch("/admin/feedback/{id}/status", FEEDBACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_forbidden_returns403() throws Exception {
        authenticateAs(ADMIN_ID);
        when(feedbackService.updateFeedbackStatus(any(), any(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(patch("/admin/feedback/{id}/status", FEEDBACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_serviceThrows_returns500() throws Exception {
        authenticateAs(ADMIN_ID);
        when(feedbackService.updateFeedbackStatus(any(), any(), any()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(patch("/admin/feedback/{id}/status", FEEDBACK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED))))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private FeedbackResponse makeFeedbackResponse() {
        return new FeedbackResponse(
                FEEDBACK_ID, ADMIN_ID, "alice@example.com", "Alice Smith",
                "BUG_REPORT", "OPEN",
                "The checkout page freezes on mobile.", null,
                "/checkout", null, null,
                Instant.now(), Instant.now());
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
