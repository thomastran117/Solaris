package backend.controllers.impl.support;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.support.AssignTicketRequest;
import backend.dtos.requests.support.CreateTicketRequest;
import backend.dtos.requests.support.TicketMessageRequest;
import backend.dtos.requests.support.UpdateTicketPriorityRequest;
import backend.dtos.requests.support.UpdateTicketStatusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.support.TicketMessageResponse;
import backend.dtos.responses.support.TicketResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.TooManyRequestException;
import backend.models.enums.TicketCategory;
import backend.models.enums.TicketPriority;
import backend.models.enums.TicketStatus;
import backend.services.intf.RateLimitService;
import backend.services.intf.support.SupportTicketService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportTicketControllerTest {

    private static final UUID USER_ID   = TestIds.uuid(1);
    private static final UUID TICKET_ID = TestIds.uuid(2);

    private SupportTicketService ticketService;
    private RateLimitService rateLimitService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        ticketService    = mock(SupportTicketService.class);
        rateLimitService = mock(RateLimitService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SupportTicketController(ticketService, rateLimitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /support/tickets ────────────────────────────────────────────────

    @Test
    void createTicket_returns201() throws Exception {
        when(ticketService.createTicket(eq(USER_ID), any())).thenReturn(makeTicketResponse());

        mockMvc.perform(post("/support/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTicketRequest("Subject", "Description", TicketCategory.OTHER, null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Test ticket"));
    }

    @Test
    void createTicket_rateLimitExceeded_returns429() throws Exception {
        doThrow(new TooManyRequestException("too many tickets"))
                .when(rateLimitService).enforce(anyString(), anyString(), anyInt(), anyInt());

        mockMvc.perform(post("/support/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void createTicket_serviceError_returns500() throws Exception {
        when(ticketService.createTicket(any(), any()))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(post("/support/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError());
    }

    // ─── GET /support/tickets ─────────────────────────────────────────────────

    @Test
    void listTickets_returns200() throws Exception {
        PagedResponse<TicketResponse> page = new PagedResponse<>(new PageImpl<>(List.of(makeTicketResponse())));
        when(ticketService.listTickets(eq(USER_ID), isNull(), isNull(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/support/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].subject").value("Test ticket"));
    }

    @Test
    void listTickets_withStatusFilter_passedToService() throws Exception {
        when(ticketService.listTickets(eq(USER_ID), eq(TicketStatus.OPEN), isNull(), eq(0), eq(20)))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of())));

        mockMvc.perform(get("/support/tickets").param("status", "OPEN"))
                .andExpect(status().isOk());

        verify(ticketService).listTickets(eq(USER_ID), eq(TicketStatus.OPEN), isNull(), eq(0), eq(20));
    }

    // ─── GET /support/tickets/{id} ────────────────────────────────────────────

    @Test
    void getTicket_returns200() throws Exception {
        when(ticketService.getTicket(TICKET_ID, USER_ID)).thenReturn(makeTicketResponse());

        mockMvc.perform(get("/support/tickets/{id}", TICKET_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getTicket_notFound_returns404() throws Exception {
        when(ticketService.getTicket(any(), any()))
                .thenThrow(new ResourceNotFoundException("ticket not found"));

        mockMvc.perform(get("/support/tickets/{id}", TICKET_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTicket_forbidden_returns403() throws Exception {
        when(ticketService.getTicket(any(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(get("/support/tickets/{id}", TICKET_ID))
                .andExpect(status().isForbidden());
    }

    // ─── POST /support/tickets/{id}/messages ─────────────────────────────────

    @Test
    void addMessage_returns201() throws Exception {
        when(ticketService.addMessage(eq(TICKET_ID), eq(USER_ID), any()))
                .thenReturn(makeMessageResponse());

        mockMvc.perform(post("/support/tickets/{id}/messages", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TicketMessageRequest("Hello"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Reply body"));
    }

    @Test
    void addMessage_closedTicket_returns409() throws Exception {
        when(ticketService.addMessage(any(), any(), any()))
                .thenThrow(new ConflictException("ticket closed"));

        mockMvc.perform(post("/support/tickets/{id}/messages", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addMessage_rateLimitExceeded_returns429() throws Exception {
        doThrow(new TooManyRequestException("rate limit"))
                .when(rateLimitService).enforce(anyString(), anyString(), anyInt(), anyInt());

        mockMvc.perform(post("/support/tickets/{id}/messages", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    // ─── PATCH /support/tickets/{id}/status ──────────────────────────────────

    @Test
    void updateStatus_returns200() throws Exception {
        when(ticketService.updateStatus(eq(TICKET_ID), eq(USER_ID), any()))
                .thenReturn(makeTicketResponse());

        mockMvc.perform(patch("/support/tickets/{id}/status", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTicketStatusRequest(TicketStatus.RESOLVED))))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatus_forbidden_returns403() throws Exception {
        when(ticketService.updateStatus(any(), any(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(patch("/support/tickets/{id}/status", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ─── PATCH /support/tickets/{id}/assign ──────────────────────────────────

    @Test
    void assignTicket_returns200() throws Exception {
        when(ticketService.assignTicket(eq(TICKET_ID), eq(USER_ID), any()))
                .thenReturn(makeTicketResponse());

        mockMvc.perform(patch("/support/tickets/{id}/assign", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignTicketRequest(TestIds.uuid(3)))))
                .andExpect(status().isOk());
    }

    @Test
    void assignTicket_nonStaff_returns403() throws Exception {
        when(ticketService.assignTicket(any(), any(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(patch("/support/tickets/{id}/assign", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ─── PATCH /support/tickets/{id}/priority ────────────────────────────────

    @Test
    void updatePriority_returns200() throws Exception {
        when(ticketService.updatePriority(eq(TICKET_ID), eq(USER_ID), any()))
                .thenReturn(makeTicketResponse());

        mockMvc.perform(patch("/support/tickets/{id}/priority", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTicketPriorityRequest(TicketPriority.HIGH))))
                .andExpect(status().isOk());
    }

    @Test
    void updatePriority_nonStaff_returns403() throws Exception {
        when(ticketService.updatePriority(any(), any(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(patch("/support/tickets/{id}/priority", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private TicketResponse makeTicketResponse() {
        return new TicketResponse(
                TICKET_ID, USER_ID, "Alice Smith",
                USER_ID, null, null, null,
                "Test ticket", "Description",
                "OPEN", "NORMAL", "OTHER",
                List.of(), Instant.now(), Instant.now(), null, null);
    }

    private TicketMessageResponse makeMessageResponse() {
        return new TicketMessageResponse(
                TestIds.uuid(5), USER_ID, "Alice Smith", "CUSTOMER", "Reply body", Instant.now());
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
