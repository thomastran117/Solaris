package backend.controllers.impl.payments;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.dispute.DisputeCaseDetailResponse;
import backend.dtos.responses.dispute.DisputeCaseResponse;
import backend.dtos.responses.dispute.DisputeEvidenceResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.DisputeEvidenceType;
import backend.models.enums.DisputeOutcome;
import backend.models.enums.DisputeStatus;
import backend.services.intf.payments.DisputeService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DisputeControllerTest {

    private static final UUID STAFF_ID = TestIds.uuid(1);
    private static final UUID CASE_ID = TestIds.uuid(2);
    private static final UUID ORDER_ID = TestIds.uuid(3);
    private static final UUID EVIDENCE_ID = TestIds.uuid(4);

    private DisputeService disputeService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        disputeService = mock(DisputeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DisputeController(disputeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listOpenDisputes_returnsPagedOpenCases() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(disputeService.getOpenDisputes(any()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(dispute()), pageable, 1)));

        mockMvc.perform(get("/admin/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stripeDisputeId").value("dp_1"))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listOpenDisputes_honoursPageAndSizeParams() throws Exception {
        when(disputeService.getOpenDisputes(any()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0)));

        mockMvc.perform(get("/admin/disputes").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(disputeService).getOpenDisputes(PageRequest.of(2, 5));
    }

    @Test
    void getDispute_returnsCaseWithEvidence() throws Exception {
        when(disputeService.getDispute(CASE_ID))
                .thenReturn(new DisputeCaseDetailResponse(dispute(), List.of(evidence())));

        mockMvc.perform(get("/admin/disputes/" + CASE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispute.stripeDisputeId").value("dp_1"))
                .andExpect(jsonPath("$.evidence[0].evidenceType").value("ORDER_DETAILS"));
    }

    @Test
    void getDispute_returnsNotFoundForUnknownId() throws Exception {
        when(disputeService.getDispute(CASE_ID)).thenThrow(new ResourceNotFoundException("nope"));

        mockMvc.perform(get("/admin/disputes/" + CASE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void addEvidence_createsEntryAttributedToTheStaffMember() throws Exception {
        authenticateAs(STAFF_ID);
        when(disputeService.addManualEvidence(eq(CASE_ID), any(), eq(STAFF_ID))).thenReturn(evidence());

        mockMvc.perform(post("/admin/disputes/" + CASE_ID + "/evidence")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "evidenceType", "ORDER_DETAILS",
                                "content", "Signed proof of delivery attached."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EVIDENCE_ID.toString()));

        verify(disputeService).addManualEvidence(eq(CASE_ID), any(), eq(STAFF_ID));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private DisputeCaseResponse dispute() {
        return new DisputeCaseResponse(
                CASE_ID, ORDER_ID, "dp_1", "ch_1", 2500L, "usd", "fraudulent",
                DisputeStatus.OPEN, DisputeOutcome.PENDING, "needs_response",
                Instant.parse("2026-09-01T00:00:00Z"), null, 2L,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));
    }

    private DisputeEvidenceResponse evidence() {
        return new DisputeEvidenceResponse(EVIDENCE_ID, DisputeEvidenceType.ORDER_DETAILS,
                "ORDER …", null, STAFF_ID, Instant.parse("2026-08-01T00:00:00Z"));
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    /** Standalone MockMvc has no Spring validator; bean validation is covered by the IT. */
    private static class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) { }
    }
}
