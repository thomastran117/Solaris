package backend.controllers.impl.imports;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.imports.ImportDownloadResponse;
import backend.dtos.responses.imports.ImportJobResponse;
import backend.dtos.responses.imports.ImportJobRowResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.ImportJobStatus;
import backend.models.enums.ImportJobType;
import backend.models.enums.ImportMode;
import backend.services.intf.imports.ImportService;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID = TestIds.uuid(2);
    private static final UUID JOB_ID = TestIds.uuid(3);
    private static final UUID ROW_ID = TestIds.uuid(4);

    private ImportService importService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        importService = mock(ImportService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ImportController(importService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createJob_returns201AndDelegatesAuthenticatedUser() throws Exception {
        authenticateAs(USER_ID);
        when(importService.createJob(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(jobResponse(JOB_ID, ImportJobStatus.PENDING, 0, 0, false));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobType", "PRODUCT_UPSERT",
                                "csvS3Key", "imports/catalog.csv",
                                "fileName", "catalog.csv"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.jobType").value("PRODUCT_UPSERT"))
                .andExpect(jsonPath("$.fileName").value("catalog.csv"));
    }

    @Test
    void createJob_invalidBodyReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "jobType", "PRODUCT_UPSERT",
                                "csvS3Key", ""
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listJobs_returnsPagedResponseAndPassesPaging() throws Exception {
        authenticateAs(USER_ID);
        when(importService.listJobs(COMPANY_ID, USER_ID, 2, 10))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(jobResponse(JOB_ID, ImportJobStatus.COMPLETED, 10, 10, false)),
                        PageRequest.of(2, 10),
                        21
                )));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/imports")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items[0].id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.items[0].progressPercent").value(100));
    }

    @Test
    void listJobs_invalidSizeReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/imports")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJob_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(importService.getJob(COMPANY_ID, USER_ID, JOB_ID))
                .thenReturn(jobResponse(JOB_ID, ImportJobStatus.PROCESSING, 12, 6, false));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/imports/" + JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void listErrors_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(importService.listErrors(COMPANY_ID, USER_ID, JOB_ID, 0, 20))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(new ImportJobRowResponse(ROW_ID, JOB_ID, 2, "SKU-2", "name is required")),
                        PageRequest.of(0, 20),
                        1
                )));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/imports/" + JOB_ID + "/errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rowNumber").value(2))
                .andExpect(jsonPath("$.items[0].errorMessage").value("name is required"));
    }

    @Test
    void getErrorReport_notFoundMapsTo404() throws Exception {
        authenticateAs(USER_ID);
        when(importService.getErrorReport(COMPANY_ID, USER_ID, JOB_ID))
                .thenThrow(new ResourceNotFoundException("No error report available for this job"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/imports/" + JOB_ID + "/error-report"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportCatalogue_returnsDownloadPayload() throws Exception {
        authenticateAs(USER_ID);
        when(importService.exportCatalogue(COMPANY_ID, USER_ID))
                .thenReturn(new ImportDownloadResponse("https://download.test/catalog.csv", 600));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/imports/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://download.test/catalog.csv"))
                .andExpect(jsonPath("$.expiresIn").value(600));
    }

    @Test
    void attachImages_returnsAttachedCount() throws Exception {
        authenticateAs(USER_ID);
        when(importService.attachImages(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(2);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/imports/attach-images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(
                                        Map.of("sku", "SKU-1", "imageUrl", "https://cdn.test/1.jpg", "displayOrder", 0),
                                        Map.of("sku", "SKU-1", "imageUrl", "https://cdn.test/2.jpg", "displayOrder", 1)
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attached").value(2));
    }

    @Test
    void attachImages_invalidRequestReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/imports/attach-images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listJobs_usesAuthenticatedPrincipal() throws Exception {
        authenticateAs(USER_ID);
        when(importService.listJobs(COMPANY_ID, USER_ID, 0, 20))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/imports"))
                .andExpect(status().isOk());

        verify(importService).listJobs(COMPANY_ID, USER_ID, 0, 20);
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ImportJobResponse jobResponse(
            UUID id,
            ImportJobStatus status,
            int totalRows,
            int processedRows,
            boolean hasErrorReport) {
        int progress = totalRows == 0 ? (status.isTerminal() ? 100 : 0) : (processedRows * 100) / totalRows;
        return new ImportJobResponse(
                id,
                COMPANY_ID,
                USER_ID,
                ImportJobType.PRODUCT_UPSERT,
                ImportMode.UPSERT,
                status,
                "catalog.csv",
                totalRows,
                processedRows,
                Math.min(processedRows, totalRows),
                0,
                progress,
                hasErrorReport,
                null,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }
}
