package backend.controllers.impl.promotions;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.loyalty.AdjustPointsRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyPolicyRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyTierRequest;
import backend.dtos.requests.loyalty.IssueBonusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.loyalty.LoyaltyAccountResponse;
import backend.dtos.responses.loyalty.LoyaltyPolicyResponse;
import backend.dtos.responses.loyalty.LoyaltyRedemptionQuoteResponse;
import backend.dtos.responses.loyalty.LoyaltyTierResponse;
import backend.dtos.responses.loyalty.LoyaltyTransactionResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.promotions.LoyaltyService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoyaltyControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final UUID ACCOUNT_ID = TestIds.uuid(3);
    private static final UUID TIER_ID    = TestIds.uuid(4);
    private static final UUID POLICY_ID  = TestIds.uuid(5);
    private static final UUID TX_ID      = TestIds.uuid(6);

    private LoyaltyService loyaltyService;
    private CompanyAccessService companyAccessService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        loyaltyService       = mock(LoyaltyService.class);
        companyAccessService = mock(CompanyAccessService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new LoyaltyController(loyaltyService, companyAccessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /loyalty/account ─────────────────────────────────────────────────

    @Test
    void getAccount_authenticated_returns200() throws Exception {
        when(loyaltyService.getAccount(USER_ID, COMPANY_ID)).thenReturn(makeAccountResponse());

        mockMvc.perform(get("/loyalty/account").param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsBalance").value(100));
    }

    // ─── GET /loyalty/transactions ────────────────────────────────────────────

    @Test
    void getTransactions_authenticated_returns200() throws Exception {
        when(loyaltyService.getTransactions(eq(USER_ID), eq(COMPANY_ID), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/loyalty/transactions").param("companyId", COMPANY_ID.toString()))
                .andExpect(status().isOk());
    }

    // ─── GET /loyalty/quote ───────────────────────────────────────────────────

    @Test
    void getRedemptionQuote_authenticated_returns200() throws Exception {
        when(loyaltyService.getRedemptionQuote(USER_ID, COMPANY_ID, 100))
                .thenReturn(new LoyaltyRedemptionQuoteResponse(
                        USER_ID, COMPANY_ID, 100, 500L, 200L, 100L, true, null));

        mockMvc.perform(get("/loyalty/quote")
                        .param("companyId", COMPANY_ID.toString())
                        .param("points", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    // ─── GET /companies/{companyId}/loyalty/policy ────────────────────────────

    @Test
    void getPolicy_returns200() throws Exception {
        when(loyaltyService.getPolicy(COMPANY_ID)).thenReturn(makePolicyResponse());

        mockMvc.perform(get("/companies/{cid}/loyalty/policy", COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Policy"));
    }

    @Test
    void getPolicy_notFound_returns404() throws Exception {
        when(loyaltyService.getPolicy(any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/companies/{cid}/loyalty/policy", COMPANY_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{companyId}/loyalty/policy ───────────────────────────

    @Test
    void createOrUpdatePolicy_returns201() throws Exception {
        when(loyaltyService.createOrUpdatePolicy(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makePolicyResponse());

        mockMvc.perform(post("/companies/{cid}/loyalty/policy", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreatePolicyRequest())))
                .andExpect(status().isCreated());
    }

    // ─── GET /companies/{companyId}/loyalty/tiers ─────────────────────────────

    @Test
    void listTiers_returns200() throws Exception {
        when(loyaltyService.listTiers(COMPANY_ID)).thenReturn(List.of(makeTierResponse()));

        mockMvc.perform(get("/companies/{cid}/loyalty/tiers", COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Gold"));
    }

    // ─── POST /companies/{companyId}/loyalty/tiers ────────────────────────────

    @Test
    void createTier_returns201() throws Exception {
        when(loyaltyService.createTier(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeTierResponse());

        mockMvc.perform(post("/companies/{cid}/loyalty/tiers", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateTierRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gold"));
    }

    // ─── PUT /companies/{companyId}/loyalty/tiers/{tierId} ───────────────────

    @Test
    void updateTier_returns200() throws Exception {
        when(loyaltyService.updateTier(eq(TIER_ID), eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeTierResponse());

        mockMvc.perform(put("/companies/{cid}/loyalty/tiers/{tid}", COMPANY_ID, TIER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateTierRequest())))
                .andExpect(status().isOk());
    }

    // ─── POST /companies/{companyId}/loyalty/bonus ────────────────────────────

    @Test
    void issueBonus_returns201() throws Exception {
        when(loyaltyService.issueBonus(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeTxResponse());

        IssueBonusRequest req = new IssueBonusRequest();
        req.setUserId(USER_ID);
        req.setPoints(100);
        req.setReason("VIP reward");

        mockMvc.perform(post("/companies/{cid}/loyalty/bonus", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ─── POST /companies/{companyId}/loyalty/accounts/{accountId}/adjust ──────

    @Test
    void adjustPoints_returns200() throws Exception {
        when(loyaltyService.adjustPoints(eq(ACCOUNT_ID), eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeTxResponse());

        AdjustPointsRequest req = new AdjustPointsRequest();
        req.setPointsDelta(50);
        req.setReason("Correction");

        mockMvc.perform(post("/companies/{cid}/loyalty/accounts/{aid}/adjust", COMPANY_ID, ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void adjustPoints_insufficientBalance_returns400() throws Exception {
        when(loyaltyService.adjustPoints(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("insufficient balance"));

        AdjustPointsRequest req = new AdjustPointsRequest();
        req.setPointsDelta(-999);

        mockMvc.perform(post("/companies/{cid}/loyalty/accounts/{aid}/adjust", COMPANY_ID, ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private LoyaltyAccountResponse makeAccountResponse() {
        return new LoyaltyAccountResponse(
                ACCOUNT_ID, USER_ID, COMPANY_ID, 100L, 500L,
                null, null, null, null, 0, null, null);
    }

    private LoyaltyPolicyResponse makePolicyResponse() {
        return new LoyaltyPolicyResponse(
                POLICY_ID, COMPANY_ID, "Test Policy",
                BigDecimal.ONE, 1, 100,
                null, 0, 0,
                BigDecimal.ZERO, "POINTS", true,
                0, 0, 0, null, null);
    }

    private LoyaltyTierResponse makeTierResponse() {
        return new LoyaltyTierResponse(
                TIER_ID, COMPANY_ID, "Gold", 1000L,
                new BigDecimal("2.00"), null, "gold", 0, null, null);
    }

    private LoyaltyTransactionResponse makeTxResponse() {
        return new LoyaltyTransactionResponse(
                TX_ID, ACCOUNT_ID, USER_ID, COMPANY_ID,
                "EARN_BONUS", 100L, 100L,
                null, null, "VIP reward", null);
    }

    private CreateLoyaltyPolicyRequest makeCreatePolicyRequest() {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("Test Policy");
        req.setEarnRatePerDollar(BigDecimal.ONE);
        req.setPointValueCents(1);
        req.setMinRedemptionPoints(100);
        req.setCashbackRatePercent(BigDecimal.ZERO);
        req.setEarnMode("POINTS");
        return req;
    }

    private CreateLoyaltyTierRequest makeCreateTierRequest() {
        CreateLoyaltyTierRequest req = new CreateLoyaltyTierRequest();
        req.setName("Gold");
        req.setMinPoints(1000L);
        req.setEarnMultiplier(new BigDecimal("2.00"));
        return req;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
