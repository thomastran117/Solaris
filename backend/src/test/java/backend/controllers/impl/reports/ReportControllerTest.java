package backend.controllers.impl.reports;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.report.CreateReportRequest;
import backend.dtos.responses.report.ReportResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.TooManyRequestException;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import backend.services.intf.ReportService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class ReportControllerTest {

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID REPORT_ID  = TestIds.uuid(2);
    private static final UUID TARGET_ID  = TestIds.uuid(3);

    private ReportService reportService;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /reports ────────────────────────────────────────────────────────

    @Test
    void create_happyPath_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(reportService.create(eq(USER_ID), any())).thenReturn(makeReportResponse());

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void create_duplicateReport_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(reportService.create(any(), any())).thenThrow(new ConflictException("Already reported"));

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void create_rateLimitExceeded_returns429() throws Exception {
        authenticateAs(USER_ID);
        when(reportService.create(any(), any()))
                .thenThrow(new TooManyRequestException("Too many reports"));

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void create_selfReport_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(reportService.create(any(), any()))
                .thenThrow(new BadRequestException("You cannot report yourself"));

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_targetNotFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(reportService.create(any(), any()))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_unexpectedError_returns500() throws Exception {
        authenticateAs(USER_ID);
        when(reportService.create(any(), any())).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private CreateReportRequest makeCreateRequest() {
        CreateReportRequest req = new CreateReportRequest();
        req.setTargetType(ReportTargetType.PRODUCT);
        req.setTargetId(TARGET_ID);
        req.setReason(ReportReason.SPAM);
        req.setTitle("Spam content on listing");
        req.setDescription("This listing contains spam content repeated many times.");
        return req;
    }

    private ReportResponse makeReportResponse() {
        backend.models.core.Report r = new backend.models.core.Report();
        r.setId(REPORT_ID);
        r.setTargetType(ReportTargetType.PRODUCT);
        r.setTargetId(TARGET_ID);
        r.setReporterId(USER_ID);
        r.setReason(ReportReason.SPAM);
        r.setTitle("Spam content on listing");
        r.setDescription("This listing contains spam content repeated many times.");
        r.setStatus(ReportStatus.OPEN);
        r.setCreatedAt(Instant.now());
        return new ReportResponse(r, "Blue Widget", "Alice Smith");
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
