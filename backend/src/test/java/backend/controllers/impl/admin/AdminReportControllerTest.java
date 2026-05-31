package backend.controllers.impl.admin;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.report.ResolveReportRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.report.ReportResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.kafka.workers.IndexVersionManager;
import backend.kafka.workers.ReportIndexingService;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import backend.services.intf.ReportSearchService;
import backend.services.intf.ReportService;
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

class AdminReportControllerTest {

    private static final UUID ADMIN_ID  = TestIds.uuid(1);
    private static final UUID REPORT_ID = TestIds.uuid(2);
    private static final UUID TARGET_ID = TestIds.uuid(3);

    private ReportService reportService;
    private ReportSearchService reportSearchService;
    private ReportIndexingService reportIndexingService;
    private IndexVersionManager indexVersionManager;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        reportService         = mock(ReportService.class);
        reportSearchService   = mock(ReportSearchService.class);
        reportIndexingService = mock(ReportIndexingService.class);
        indexVersionManager   = mock(IndexVersionManager.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminReportController(
                        reportService, reportSearchService,
                        reportIndexingService, indexVersionManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /admin/reports ───────────────────────────────────────────────────

    @Test
    void listReports_noFilters_returns200() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportService.listReports(isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk());
    }

    @Test
    void listReports_withTargetTypeAndStatus_returns200() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportService.listReports(eq(ReportTargetType.PRODUCT), eq(ReportStatus.OPEN), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(makeReportResponse()), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/admin/reports")
                        .param("targetType", "PRODUCT")
                        .param("status", "OPEN"))
                .andExpect(status().isOk());
    }

    @Test
    void listReports_serviceError_returns500() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportService.listReports(any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isInternalServerError());
    }

    // ─── GET /admin/reports/search ────────────────────────────────────────────

    @Test
    void search_noParams_returns200() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportSearchService.search(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/admin/reports/search"))
                .andExpect(status().isOk());
    }

    @Test
    void search_withAllParams_delegates() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportSearchService.search(eq("spam"), eq(ReportTargetType.PRODUCT), eq(ReportStatus.OPEN),
                eq(ReportReason.SPAM), any(), any(), eq("newest"), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(makeReportResponse()), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/admin/reports/search")
                        .param("q", "spam")
                        .param("targetType", "PRODUCT")
                        .param("status", "OPEN")
                        .param("reason", "SPAM")
                        .param("sort", "newest"))
                .andExpect(status().isOk());
    }

    @Test
    void search_serviceError_returns500() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportSearchService.search(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/admin/reports/search"))
                .andExpect(status().isInternalServerError());
    }

    // ─── GET /admin/reports/{id} ──────────────────────────────────────────────

    @Test
    void getReport_found_returns200() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportService.getReport(REPORT_ID)).thenReturn(makeReportResponse());

        mockMvc.perform(get("/admin/reports/{id}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REPORT_ID.toString()));
    }

    @Test
    void getReport_notFound_returns404() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportService.getReport(REPORT_ID))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(get("/admin/reports/{id}", REPORT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReport_serviceError_returns500() throws Exception {
        authenticateAs(ADMIN_ID);
        when(reportService.getReport(any()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/admin/reports/{id}", REPORT_ID))
                .andExpect(status().isInternalServerError());
    }

    // ─── PATCH /admin/reports/{id} ────────────────────────────────────────────

    @Test
    void resolve_actioned_returns204() throws Exception {
        authenticateAs(ADMIN_ID);
        doNothing().when(reportService).resolve(eq(REPORT_ID), eq(ADMIN_ID), any());

        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.ACTIONED);

        mockMvc.perform(patch("/admin/reports/{id}", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(reportService).resolve(eq(REPORT_ID), eq(ADMIN_ID), any());
    }

    @Test
    void resolve_badRequest_returns400() throws Exception {
        authenticateAs(ADMIN_ID);
        doThrow(new BadRequestException("Already resolved"))
                .when(reportService).resolve(any(), any(), any());

        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.DISMISSED);

        mockMvc.perform(patch("/admin/reports/{id}", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolve_notFound_returns404() throws Exception {
        authenticateAs(ADMIN_ID);
        doThrow(new ResourceNotFoundException("Report not found"))
                .when(reportService).resolve(any(), any(), any());

        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.ACTIONED);

        mockMvc.perform(patch("/admin/reports/{id}", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolve_serviceError_returns500() throws Exception {
        authenticateAs(ADMIN_ID);
        doThrow(new RuntimeException("unexpected"))
                .when(reportService).resolve(any(), any(), any());

        ResolveReportRequest req = new ResolveReportRequest();
        req.setResolution(ReportStatus.ACTIONED);

        mockMvc.perform(patch("/admin/reports/{id}", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError());
    }

    // ─── POST /admin/reports/reindex ──────────────────────────────────────────

    @Test
    void reindex_returns200() throws Exception {
        authenticateAs(ADMIN_ID);
        doNothing().when(indexVersionManager).rolloverIndex("reports");
        doNothing().when(reportIndexingService).reindexAll();

        mockMvc.perform(post("/admin/reports/reindex"))
                .andExpect(status().isOk());

        verify(indexVersionManager).rolloverIndex("reports");
        verify(reportIndexingService).reindexAll();
    }

    @Test
    void reindex_serviceError_returns500() throws Exception {
        authenticateAs(ADMIN_ID);
        doThrow(new RuntimeException("ES down")).when(reportIndexingService).reindexAll();

        mockMvc.perform(post("/admin/reports/reindex"))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ReportResponse makeReportResponse() {
        backend.models.core.Report r = new backend.models.core.Report();
        r.setId(REPORT_ID);
        r.setTargetType(ReportTargetType.PRODUCT);
        r.setTargetId(TARGET_ID);
        r.setReporterId(ADMIN_ID);
        r.setReason(ReportReason.SPAM);
        r.setTitle("Spam content");
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
