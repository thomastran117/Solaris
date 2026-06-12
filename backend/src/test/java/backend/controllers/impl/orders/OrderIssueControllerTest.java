package backend.controllers.impl.orders;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.issue.OrderIssueResponse;
import backend.models.enums.OrderIssueState;
import backend.services.intf.orders.OrderIssueService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIssueControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID ORDER_ID = TestIds.uuid(2);
    private static final UUID ISSUE_ID = TestIds.uuid(3);

    private OrderIssueService orderIssueService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        orderIssueService = mock(OrderIssueService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new OrderIssueController(orderIssueService))
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
    void openIssue_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(orderIssueService.openIssue(eq(ORDER_ID), eq(USER_ID), any())).thenReturn(issueResponse("REPORTED"));

        mockMvc.perform(post("/orders/" + ORDER_ID + "/issues")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "DAMAGED",
                                "description", "Box arrived crushed",
                                "openTicket", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ISSUE_ID.toString()));
    }

    @Test
    void getIssuesByOrder_returnsList() throws Exception {
        authenticateAs(USER_ID);
        when(orderIssueService.getIssuesByOrder(ORDER_ID, USER_ID)).thenReturn(List.of(issueResponse("REPORTED")));

        mockMvc.perform(get("/orders/" + ORDER_ID + "/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("REPORTED"));
    }

    @Test
    void listIssues_returnsPagedResults() throws Exception {
        authenticateAs(USER_ID);
        when(orderIssueService.listIssues(USER_ID, OrderIssueState.REPORTED, 0, 20))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(issueResponse("REPORTED")),
                        PageRequest.of(0, 20),
                        1
                )));

        mockMvc.perform(get("/support/issues").param("state", "REPORTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ISSUE_ID.toString()));
    }

    @Test
    void transition_returnsUpdatedIssue() throws Exception {
        authenticateAs(USER_ID);
        when(orderIssueService.transitionState(eq(ISSUE_ID), eq(USER_ID), any()))
                .thenReturn(issueResponse("INVESTIGATING"));

        mockMvc.perform(post("/support/issues/" + ISSUE_ID + "/transition")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("state", "INVESTIGATING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INVESTIGATING"));
    }

    @Test
    void resolveWithRefund_returnsResolvedIssue() throws Exception {
        authenticateAs(USER_ID);
        when(orderIssueService.resolveWithRefund(eq(ISSUE_ID), eq(USER_ID), any()))
                .thenReturn(issueResponse("RESOLVED_REFUND"));

        mockMvc.perform(post("/support/issues/" + ISSUE_ID + "/resolve/refund")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refundAmountCents", 500,
                                "reason", "Partial goodwill"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RESOLVED_REFUND"));
    }

    @Test
    void resolveWithReplacement_returnsResolvedIssue() throws Exception {
        authenticateAs(USER_ID);
        when(orderIssueService.resolveWithReplacement(eq(ISSUE_ID), eq(USER_ID), any()))
                .thenReturn(issueResponse("RESOLVED_REPLACEMENT"));

        mockMvc.perform(post("/support/issues/" + ISSUE_ID + "/resolve/replacement")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "variantId", TestIds.uuid(20),
                                        "quantity", 1
                                )),
                                "shippingAddress", "123 King St",
                                "shippingCity", "Toronto",
                                "shippingCountry", "CA",
                                "shippingPostalCode", "M5V1K4"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RESOLVED_REPLACEMENT"));
    }

    @Test
    void rejectIssue_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        when(orderIssueService.rejectIssue(eq(ISSUE_ID), eq(USER_ID), any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/support/issues/" + ISSUE_ID + "/reject")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("reason", "No evidence"))))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private OrderIssueResponse issueResponse(String state) {
        return new OrderIssueResponse(
                ISSUE_ID,
                ORDER_ID,
                null,
                USER_ID,
                "DAMAGED",
                state,
                null,
                "Box arrived crushed",
                null,
                null,
                null,
                null,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z"),
                null
        );
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
