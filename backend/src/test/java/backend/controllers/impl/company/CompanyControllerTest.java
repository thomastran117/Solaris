package backend.controllers.impl.company;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.company.CompanyResponse;
import backend.dtos.responses.company.PublicCompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.company.CompanyService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyControllerTest {

    private CompanyService companyService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);

    @BeforeEach
    void setUp() {
        companyService = mock(CompanyService.class);
        CompanyController controller = new CompanyController(companyService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /companies ───────────────────────────────────────────────────────

    @Test
    void getCompanies_returns200WithPublicPagedResponse() throws Exception {
        PublicCompanyResponse pub = makePublicResponse(COMPANY_ID);
        when(companyService.searchPublicCompanies(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(pub), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(COMPANY_ID.toString()));
    }

    @Test
    void getCompanies_withFilters_passesParamsToService() throws Exception {
        when(companyService.searchPublicCompanies(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of())));

        mockMvc.perform(get("/companies")
                        .param("q", "Tech")
                        .param("industry", "Technology")
                        .param("country", "US"))
                .andExpect(status().isOk());

        verify(companyService).searchPublicCompanies(
                eq("Tech"), eq("Technology"), eq("US"), isNull(),
                anyInt(), anyInt(), any(), any());
    }

    // ─── GET /companies/mine ──────────────────────────────────────────────────

    @Test
    void getMyCompany_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.getMyCompany(USER_ID)).thenReturn(makeCompanyResponse(COMPANY_ID));

        mockMvc.perform(get("/companies/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()));
    }

    @Test
    void getMyCompany_noCompany_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.getMyCompany(USER_ID))
                .thenThrow(new ResourceNotFoundException("No company found for this user"));

        mockMvc.perform(get("/companies/mine"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /companies/{id}/public ───────────────────────────────────────────

    @Test
    void getPublicCompany_returns200() throws Exception {
        when(companyService.getPublicCompany(COMPANY_ID)).thenReturn(makePublicResponse(COMPANY_ID));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()));
    }

    @Test
    void getPublicCompany_notFound_returns404() throws Exception {
        when(companyService.getPublicCompany(any()))
                .thenThrow(new ResourceNotFoundException("Company not found"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/public"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /companies/{id} ──────────────────────────────────────────────────

    @Test
    void getCompany_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.getCompany(COMPANY_ID, USER_ID)).thenReturn(makeCompanyResponse(COMPANY_ID));

        mockMvc.perform(get("/companies/" + COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()));
    }

    @Test
    void getCompany_forbidden_returns403() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.getCompany(any(), any()))
                .thenThrow(new ForbiddenException("You do not have access to this company"));

        mockMvc.perform(get("/companies/" + COMPANY_ID))
                .andExpect(status().isForbidden());
    }

    // ─── POST /companies/batch ────────────────────────────────────────────────

    @Test
    void getCompaniesByIds_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.getCompaniesByIds(anyList(), eq(USER_ID)))
                .thenReturn(List.of(makeCompanyResponse(COMPANY_ID)));

        mockMvc.perform(post("/companies/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", List.of(COMPANY_ID)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(COMPANY_ID.toString()));
    }

    // ─── POST /companies ──────────────────────────────────────────────────────

    @Test
    void createCompany_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.createCompany(eq(USER_ID), any()))
                .thenReturn(makeCompanyResponse(COMPANY_ID));

        mockMvc.perform(post("/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()));
    }

    @Test
    void createCompany_delegatesAuthenticatedUserId() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.createCompany(any(), any())).thenReturn(makeCompanyResponse(COMPANY_ID));

        mockMvc.perform(post("/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\"}"));

        verify(companyService).createCompany(eq(USER_ID), any());
    }

    @Test
    void createCompany_conflict_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.createCompany(any(), any()))
                .thenThrow(new ConflictException("A company with this name already exists"));

        mockMvc.perform(post("/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dup\"}"))
                .andExpect(status().isConflict());
    }

    // ─── PATCH /companies/{id} ────────────────────────────────────────────────

    @Test
    void updateCompany_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.updateCompany(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeCompanyResponse(COMPANY_ID));

        mockMvc.perform(patch("/companies/" + COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"London\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()));
    }

    @Test
    void updateCompany_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.updateCompany(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Company not found"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{id}/logo/presign ────────────────────────────────────

    @Test
    void presignLogoUpload_returns200WithUploadUrl() throws Exception {
        authenticateAs(USER_ID);
        when(companyService.generateLogoUploadUrl(eq(COMPANY_ID), eq(USER_ID), eq("image/png")))
                .thenReturn(new PresignUploadResponse("https://s3.url", "https://cdn.url", "key", 300));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/logo/presign")
                        .param("contentType", "image/png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.url"));
    }

    // ─── DELETE /companies/{id} ───────────────────────────────────────────────

    @Test
    void deleteCompany_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/" + COMPANY_ID))
                .andExpect(status().isNoContent());

        verify(companyService).deleteCompany(COMPANY_ID, USER_ID);
    }

    @Test
    void deleteCompany_forbidden_returns403() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new ForbiddenException("Your role does not allow this action"))
                .when(companyService).deleteCompany(any(), any());

        mockMvc.perform(delete("/companies/" + COMPANY_ID))
                .andExpect(status().isForbidden());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private CompanyResponse makeCompanyResponse(UUID id) {
        return new CompanyResponse(
                id, USER_ID, "Test Company",
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                "ACTIVE", false, null, null);
    }

    private PublicCompanyResponse makePublicResponse(UUID id) {
        return new PublicCompanyResponse(id, "Test Company", "London", "UK",
                null, null, null, "Technology", 2020);
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
