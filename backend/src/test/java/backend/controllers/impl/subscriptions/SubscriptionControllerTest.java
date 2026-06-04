package backend.controllers.impl.subscriptions;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.subscription.CreateSubscriptionRequest;
import backend.dtos.requests.subscription.UpdateSubscriptionRequest;
import backend.dtos.responses.subscription.SavedPaymentMethodResponse;
import backend.dtos.responses.subscription.SetupIntentResponse;
import backend.dtos.responses.subscription.SubscriptionResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.BillingInterval;
import backend.models.enums.SubscriptionStatus;
import backend.services.intf.subscriptions.SubscriptionService;
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

class SubscriptionControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID SUB_ID  = TestIds.uuid(2);
    private static final UUID PM_ID   = TestIds.uuid(3);

    private SubscriptionService subscriptionService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        subscriptionService = mock(SubscriptionService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionController(subscriptionService))
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

    // ─── POST /subscriptions/setup-intent ────────────────────────────────────

    @Test
    void createSetupIntent_returns200() throws Exception {
        when(subscriptionService.createSetupIntent(USER_ID))
                .thenReturn(new SetupIntentResponse("si_abc", "seti_secret", "cus_123"));

        mockMvc.perform(post("/subscriptions/setup-intent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupIntentId").value("si_abc"));
    }

    @Test
    void createSetupIntent_serviceError_returns500() throws Exception {
        when(subscriptionService.createSetupIntent(any()))
                .thenThrow(new RuntimeException("Stripe down"));

        mockMvc.perform(post("/subscriptions/setup-intent"))
                .andExpect(status().isInternalServerError());
    }

    // ─── GET /subscriptions/payment-methods ──────────────────────────────────

    @Test
    void listPaymentMethods_returns200() throws Exception {
        when(subscriptionService.listPaymentMethods(USER_ID))
                .thenReturn(List.of(new SavedPaymentMethodResponse(PM_ID, "pm_abc", "visa", "4242", 12, 2030, true)));

        mockMvc.perform(get("/subscriptions/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand").value("visa"));
    }

    // ─── DELETE /subscriptions/payment-methods/{id} ───────────────────────────

    @Test
    void detachPaymentMethod_returns204() throws Exception {
        mockMvc.perform(delete("/subscriptions/payment-methods/{id}", PM_ID))
                .andExpect(status().isNoContent());

        verify(subscriptionService).detachPaymentMethod(USER_ID, PM_ID);
    }

    @Test
    void detachPaymentMethod_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("not found"))
                .when(subscriptionService).detachPaymentMethod(any(), any());

        mockMvc.perform(delete("/subscriptions/payment-methods/{id}", PM_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /subscriptions ──────────────────────────────────────────────────

    @Test
    void create_returns201() throws Exception {
        when(subscriptionService.create(eq(USER_ID), any()))
                .thenReturn(makeSubscriptionResponse(SubscriptionStatus.ACTIVE));

        mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSubscriptionRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_badRequest_returns400() throws Exception {
        when(subscriptionService.create(any(), any()))
                .thenThrow(new BadRequestException("non-subscribable product"));

        mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_conflict_returns409() throws Exception {
        when(subscriptionService.create(any(), any()))
                .thenThrow(new ConflictException("already active"));

        mockMvc.perform(post("/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    // ─── GET /subscriptions ───────────────────────────────────────────────────

    @Test
    void list_returns200() throws Exception {
        when(subscriptionService.listForUser(USER_ID))
                .thenReturn(List.of(makeSubscriptionResponse(SubscriptionStatus.ACTIVE)));

        mockMvc.perform(get("/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    // ─── GET /subscriptions/{id} ──────────────────────────────────────────────

    @Test
    void get_returns200() throws Exception {
        when(subscriptionService.get(USER_ID, SUB_ID))
                .thenReturn(makeSubscriptionResponse(SubscriptionStatus.ACTIVE));

        mockMvc.perform(get("/subscriptions/{id}", SUB_ID))
                .andExpect(status().isOk());
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(subscriptionService.get(any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/subscriptions/{id}", SUB_ID))
                .andExpect(status().isNotFound());
    }

    // ─── PATCH /subscriptions/{id} ────────────────────────────────────────────

    @Test
    void update_returns200() throws Exception {
        when(subscriptionService.update(eq(USER_ID), eq(SUB_ID), any()))
                .thenReturn(makeSubscriptionResponse(SubscriptionStatus.ACTIVE));

        mockMvc.perform(patch("/subscriptions/{id}", SUB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSubscriptionRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void update_conflict_returns409() throws Exception {
        when(subscriptionService.update(any(), any(), any()))
                .thenThrow(new ConflictException("not modifiable"));

        mockMvc.perform(patch("/subscriptions/{id}", SUB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    // ─── POST /subscriptions/{id}/pause ──────────────────────────────────────

    @Test
    void pause_returns200() throws Exception {
        when(subscriptionService.pause(USER_ID, SUB_ID))
                .thenReturn(makeSubscriptionResponse(SubscriptionStatus.PAUSED));

        mockMvc.perform(post("/subscriptions/{id}/pause", SUB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void pause_conflict_returns409() throws Exception {
        when(subscriptionService.pause(any(), any()))
                .thenThrow(new ConflictException("already paused"));

        mockMvc.perform(post("/subscriptions/{id}/pause", SUB_ID))
                .andExpect(status().isConflict());
    }

    // ─── POST /subscriptions/{id}/resume ─────────────────────────────────────

    @Test
    void resume_returns200() throws Exception {
        when(subscriptionService.resume(USER_ID, SUB_ID))
                .thenReturn(makeSubscriptionResponse(SubscriptionStatus.ACTIVE));

        mockMvc.perform(post("/subscriptions/{id}/resume", SUB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void resume_conflict_returns409() throws Exception {
        when(subscriptionService.resume(any(), any()))
                .thenThrow(new ConflictException("not paused"));

        mockMvc.perform(post("/subscriptions/{id}/resume", SUB_ID))
                .andExpect(status().isConflict());
    }

    // ─── POST /subscriptions/{id}/skip-next ──────────────────────────────────

    @Test
    void skipNext_returns200() throws Exception {
        SubscriptionResponse resp = makeSubscriptionResponse(SubscriptionStatus.ACTIVE);
        resp.setSkipNextCycle(true);
        when(subscriptionService.skipNext(USER_ID, SUB_ID)).thenReturn(resp);

        mockMvc.perform(post("/subscriptions/{id}/skip-next", SUB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipNextCycle").value(true));
    }

    @Test
    void skipNext_conflict_returns409() throws Exception {
        when(subscriptionService.skipNext(any(), any()))
                .thenThrow(new ConflictException("not active"));

        mockMvc.perform(post("/subscriptions/{id}/skip-next", SUB_ID))
                .andExpect(status().isConflict());
    }

    // ─── DELETE /subscriptions/{id} ───────────────────────────────────────────

    @Test
    void cancel_atPeriodEnd_returns200() throws Exception {
        SubscriptionResponse resp = makeSubscriptionResponse(SubscriptionStatus.ACTIVE);
        resp.setCancelAtPeriodEnd(true);
        when(subscriptionService.cancel(USER_ID, SUB_ID, true)).thenReturn(resp);

        mockMvc.perform(delete("/subscriptions/{id}", SUB_ID)
                        .param("atPeriodEnd", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));
    }

    @Test
    void cancel_immediate_returns200() throws Exception {
        when(subscriptionService.cancel(USER_ID, SUB_ID, false))
                .thenReturn(makeSubscriptionResponse(SubscriptionStatus.CANCELLED));

        mockMvc.perform(delete("/subscriptions/{id}", SUB_ID)
                        .param("atPeriodEnd", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_alreadyCancelled_returns409() throws Exception {
        when(subscriptionService.cancel(any(), any(), anyBoolean()))
                .thenThrow(new ConflictException("already cancelled"));

        mockMvc.perform(delete("/subscriptions/{id}", SUB_ID))
                .andExpect(status().isConflict());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private SubscriptionResponse makeSubscriptionResponse(SubscriptionStatus status) {
        SubscriptionResponse resp = new SubscriptionResponse();
        resp.setId(SUB_ID);
        resp.setStatus(status);
        resp.setBillingInterval(BillingInterval.MONTH);
        resp.setIntervalCount(1);
        resp.setCurrentPeriodStart(Instant.now().minusSeconds(86400));
        resp.setCurrentPeriodEnd(Instant.now().plusSeconds(86400 * 29L));
        resp.setNextBillingAt(resp.getCurrentPeriodEnd());
        resp.setCurrency("USD");
        resp.setUnitAmountCents(1000L);
        resp.setItems(List.of());
        resp.setCreatedAt(Instant.now());
        resp.setUpdatedAt(Instant.now());
        return resp;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
