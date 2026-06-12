package backend.controllers.impl.pricing;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.marketplace.CommissionPolicyResponse;
import backend.services.intf.pricing.CommissionPolicyService;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommissionPolicyControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(2);
    private static final UUID POLICY_ID = TestIds.uuid(3);

    private CommissionPolicyService commissionPolicyService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        commissionPolicyService = mock(CommissionPolicyService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new CommissionPolicyController(commissionPolicyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    @Test
    void list_returnsPolicies() throws Exception {
        authenticateAs(USER_ID);
        when(commissionPolicyService.listPolicies(MARKETPLACE_ID, USER_ID)).thenReturn(List.of(response()));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/commission-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$[0].rules[0].ruleType").value("CATEGORY"));
    }

    @Test
    void create_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(commissionPolicyService.createPolicy(eq(MARKETPLACE_ID), eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/commission-policies")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Default Policy",
                                "defaultRate", 0.15,
                                "rules", List.of(Map.of(
                                        "ruleType", "CATEGORY",
                                        "matchValue", "Office",
                                        "rate", 0.10,
                                        "priority", 5
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Default Policy"));
    }

    @Test
    void create_invalidBodyReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/commission-policies")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "",
                                "defaultRate", 1.5
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/marketplaces/" + MARKETPLACE_ID + "/commission-policies/" + POLICY_ID))
                .andExpect(status().isNoContent());

        verify(commissionPolicyService).deletePolicy(POLICY_ID, MARKETPLACE_ID, USER_ID);
    }

    @Test
    void delete_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new RuntimeException("boom"))
                .when(commissionPolicyService).deletePolicy(POLICY_ID, MARKETPLACE_ID, USER_ID);

        mockMvc.perform(delete("/marketplaces/" + MARKETPLACE_ID + "/commission-policies/" + POLICY_ID))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private CommissionPolicyResponse response() {
        return new CommissionPolicyResponse(
                POLICY_ID,
                MARKETPLACE_ID,
                "Default Policy",
                new BigDecimal("0.1500"),
                Instant.parse("2026-05-01T00:00:00Z"),
                null,
                true,
                List.of(new CommissionPolicyResponse.RuleResponse(
                        TestIds.uuid(10),
                        "CATEGORY",
                        "Office",
                        new BigDecimal("0.1000"),
                        5
                )),
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }
}
