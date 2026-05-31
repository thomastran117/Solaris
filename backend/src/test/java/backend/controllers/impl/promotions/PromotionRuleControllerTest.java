package backend.controllers.impl.promotions;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.pricing.CreatePromotionRuleRequest;
import backend.dtos.requests.pricing.UpdatePromotionRuleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.pricing.PromotionRuleAnalyticsResponse;
import backend.dtos.responses.pricing.PromotionRuleResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.DiscountStatus;
import backend.models.enums.PromotionRuleType;
import backend.services.intf.pricing.PricingReportService;
import backend.services.intf.promotions.PromotionRuleService;
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

class PromotionRuleControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final UUID RULE_ID    = TestIds.uuid(3);

    private PromotionRuleService promotionRuleService;
    private PricingReportService pricingReportService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        promotionRuleService = mock(PromotionRuleService.class);
        pricingReportService = mock(PricingReportService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PromotionRuleController(promotionRuleService, pricingReportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET /companies/{companyId}/promotion-rules ───────────────────────────

    @Test
    void listRules_returns200() throws Exception {
        when(promotionRuleService.listRules(eq(COMPANY_ID), eq(USER_ID), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        mockMvc.perform(get("/companies/{cid}/promotion-rules", COMPANY_ID))
                .andExpect(status().isOk());
    }

    // ─── GET /companies/{companyId}/promotion-rules/{ruleId} ─────────────────

    @Test
    void getRule_returns200() throws Exception {
        when(promotionRuleService.getRule(COMPANY_ID, RULE_ID, USER_ID))
                .thenReturn(makeRuleResponse());

        mockMvc.perform(get("/companies/{cid}/promotion-rules/{rid}", COMPANY_ID, RULE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Rule"));
    }

    @Test
    void getRule_notFound_returns404() throws Exception {
        when(promotionRuleService.getRule(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/companies/{cid}/promotion-rules/{rid}", COMPANY_ID, RULE_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /companies/{companyId}/promotion-rules ──────────────────────────

    @Test
    void createRule_returns201() throws Exception {
        when(promotionRuleService.createRule(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeRuleResponse());

        mockMvc.perform(post("/companies/{cid}/promotion-rules", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Rule"));
    }

    @Test
    void createRule_invalidRuleType_returns400() throws Exception {
        when(promotionRuleService.createRule(any(), any(), any()))
                .thenThrow(new BadRequestException("invalid ruleType"));

        mockMvc.perform(post("/companies/{cid}/promotion-rules", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeCreateRequest())))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /companies/{companyId}/promotion-rules/{ruleId} ───────────────

    @Test
    void updateRule_returns200() throws Exception {
        when(promotionRuleService.updateRule(eq(COMPANY_ID), eq(RULE_ID), eq(USER_ID), any()))
                .thenReturn(makeRuleResponse());

        mockMvc.perform(patch("/companies/{cid}/promotion-rules/{rid}", COMPANY_ID, RULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateRule_expiredStatus_returns400() throws Exception {
        when(promotionRuleService.updateRule(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("EXPIRED not allowed"));

        mockMvc.perform(patch("/companies/{cid}/promotion-rules/{rid}", COMPANY_ID, RULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EXPIRED\"}"))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /companies/{companyId}/promotion-rules/{ruleId} ──────────────

    @Test
    void deleteRule_returns204() throws Exception {
        mockMvc.perform(delete("/companies/{cid}/promotion-rules/{rid}", COMPANY_ID, RULE_ID))
                .andExpect(status().isNoContent());

        verify(promotionRuleService).deleteRule(COMPANY_ID, RULE_ID, USER_ID);
    }

    // ─── GET /companies/{companyId}/promotion-rules/{ruleId}/analytics ────────

    @Test
    void getRuleAnalytics_returns200() throws Exception {
        when(pricingReportService.getRuleAnalytics(eq(COMPANY_ID), eq(RULE_ID), eq(USER_ID), any(), any()))
                .thenReturn(new PromotionRuleAnalyticsResponse(
                        RULE_ID, null, null, 5L, new BigDecimal("50.00"), 4L, 3L));

        mockMvc.perform(get("/companies/{cid}/promotion-rules/{rid}/analytics", COMPANY_ID, RULE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptionCount").value(5));
    }

    @Test
    void getRuleAnalytics_notFound_returns404() throws Exception {
        when(pricingReportService.getRuleAnalytics(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/companies/{cid}/promotion-rules/{rid}/analytics", COMPANY_ID, RULE_ID))
                .andExpect(status().isNotFound());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private PromotionRuleResponse makeRuleResponse() {
        return new PromotionRuleResponse(
                RULE_ID, COMPANY_ID, "Test Rule", null,
                PromotionRuleType.PERCENTAGE_OFF, null,
                DiscountStatus.ACTIVE, 100, false,
                null, null, null, null, 0, null,
                null, List.of(), List.of(), List.of(),
                null, null);
    }

    private CreatePromotionRuleRequest makeCreateRequest() {
        CreatePromotionRuleRequest req = new CreatePromotionRuleRequest();
        req.setName("Test Rule");
        req.setRuleType("PERCENTAGE_OFF");
        return req;
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
