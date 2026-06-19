package backend.controllers.impl.marketing;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.marketing.WorkflowAnalyticsResponse;
import backend.dtos.responses.marketing.WorkflowResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;
import backend.services.intf.marketing.MarketingWorkflowService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketingWorkflowControllerTest {

    private MarketingWorkflowService workflowService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID     = TestIds.uuid(1);
    private static final UUID COMPANY_ID  = TestIds.uuid(2);
    private static final UUID WORKFLOW_ID = TestIds.uuid(3);

    @BeforeEach
    void setUp() {
        workflowService = mock(MarketingWorkflowService.class);
        MarketingWorkflowController controller = new MarketingWorkflowController(workflowService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST create ──────────────────────────────────────────────────────────

    @Test
    void create_validRequest_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(workflowService.createWorkflow(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(stubWorkflowResponse("ACTIVE"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/marketing/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Post-delivery review",
                                "trigger", "ORDER_DELIVERED",
                                "delayHours", 72,
                                "actionType", "EMAIL",
                                "cooldownDays", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(WORKFLOW_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_forbidden_returns403() throws Exception {
        authenticateAs(USER_ID);
        when(workflowService.createWorkflow(any(), any(), any()))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/marketing/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "X", "trigger", "ORDER_DELIVERED",
                                "delayHours", 0, "actionType", "EMAIL", "cooldownDays", 0))))
                .andExpect(status().isForbidden());
    }

    // ─── GET list ─────────────────────────────────────────────────────────────

    @Test
    void list_returns200WithWorkflows() throws Exception {
        authenticateAs(USER_ID);
        when(workflowService.getWorkflows(eq(COMPANY_ID), eq(USER_ID)))
                .thenReturn(List.of(stubWorkflowResponse("ACTIVE")));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/marketing/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(WORKFLOW_ID.toString()));
    }

    // ─── PATCH update ─────────────────────────────────────────────────────────

    @Test
    void update_pause_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(workflowService.updateWorkflow(eq(COMPANY_ID), eq(WORKFLOW_ID), eq(USER_ID), any()))
                .thenReturn(stubWorkflowResponse("PAUSED"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/marketing/workflows/" + WORKFLOW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PAUSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void update_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(workflowService.updateWorkflow(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/marketing/workflows/" + WORKFLOW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PAUSED"))))
                .andExpect(status().isNotFound());
    }

    // ─── GET analytics ────────────────────────────────────────────────────────

    @Test
    void analytics_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(workflowService.getAnalytics(eq(COMPANY_ID), eq(WORKFLOW_ID), eq(USER_ID)))
                .thenReturn(new WorkflowAnalyticsResponse(WORKFLOW_ID, 50L, 42L));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/marketing/workflows/" + WORKFLOW_ID + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolledCount").value(50))
                .andExpect(jsonPath("$.sentCount").value(42));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private WorkflowResponse stubWorkflowResponse(String status) {
        return new WorkflowResponse(
                WORKFLOW_ID, COMPANY_ID, "Post-delivery review",
                WorkflowTrigger.ORDER_DELIVERED, 72, null,
                WorkflowActionType.EMAIL, "How was your order?", null,
                30, WorkflowStatus.valueOf(status), Instant.now(), Instant.now());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static class NoOpValidator implements Validator {
        public boolean supports(Class<?> c) { return true; }
        public void validate(Object t, Errors e) {}
    }
}
