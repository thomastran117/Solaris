package backend.controllers.impl.customers;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.credit.CreditBalanceResponse;
import backend.dtos.responses.credit.CreditEntryResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.customers.CustomerCreditService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerCreditControllerTest {

    private CustomerCreditService creditService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID  = TestIds.uuid(1);
    private static final UUID ENTRY_ID = TestIds.uuid(2);

    @BeforeEach
    void setUp() {
        creditService = mock(CustomerCreditService.class);
        CustomerCreditController controller = new CustomerCreditController(creditService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /me/credits ──────────────────────────────────────────────────────

    @Test
    void getMyCredits_returns200WithBalance() throws Exception {
        when(creditService.getBalance(USER_ID)).thenReturn(makeBalance(USER_ID));

        mockMvc.perform(get("/me/credits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(500));
    }

    @Test
    void getMyCredits_userNotFound_returns404() throws Exception {
        when(creditService.getBalance(USER_ID))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get("/me/credits"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /support/customers/{userId}/credits ──────────────────────────────

    @Test
    void getCustomerCredits_returns200() throws Exception {
        UUID targetUser = TestIds.uuid(99);
        when(creditService.getBalance(targetUser)).thenReturn(makeBalance(targetUser));

        mockMvc.perform(get("/support/customers/" + targetUser + "/credits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(500));
    }

    // ─── POST /support/customers/{userId}/credits ─────────────────────────────

    @Test
    void issueCredit_returns201WithEntry() throws Exception {
        when(creditService.issueCredit(eq(USER_ID), any(), eq(USER_ID), any(), any()))
                .thenReturn(makeEntry());

        mockMvc.perform(post("/support/customers/" + USER_ID + "/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amountCents", 500, "type", "COMPENSATION_ISSUED"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ENTRY_ID.toString()));
    }

    @Test
    void issueCredit_serviceThrowsBadRequest_returns400() throws Exception {
        when(creditService.issueCredit(any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestException("Invalid credit type"));

        mockMvc.perform(post("/support/customers/" + USER_ID + "/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amountCents", 500, "type", "COMPENSATION_ISSUED"))))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /support/credits/{entryId}/reverse ──────────────────────────────

    @Test
    void reverseCredit_returns200WithEntry() throws Exception {
        when(creditService.reverseCredit(ENTRY_ID, USER_ID)).thenReturn(makeEntry());

        mockMvc.perform(post("/support/credits/" + ENTRY_ID + "/reverse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ENTRY_ID.toString()));
    }

    @Test
    void reverseCredit_notFound_returns404() throws Exception {
        when(creditService.reverseCredit(ENTRY_ID, USER_ID))
                .thenThrow(new ResourceNotFoundException("Entry not found"));

        mockMvc.perform(post("/support/credits/" + ENTRY_ID + "/reverse"))
                .andExpect(status().isNotFound());
    }

    // ─── error handling ───────────────────────────────────────────────────────

    @Test
    void unexpectedException_returns500() throws Exception {
        when(creditService.getBalance(USER_ID))
                .thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/me/credits"))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private CreditBalanceResponse makeBalance(UUID userId) {
        return new CreditBalanceResponse(userId, 500L, "USD", List.of());
    }

    private CreditEntryResponse makeEntry() {
        return new CreditEntryResponse(
                ENTRY_ID, 500L, "USD", "COMPENSATION_ISSUED",
                "Sorry for the trouble", USER_ID, null, null, null,
                null, Instant.now());
    }
}
