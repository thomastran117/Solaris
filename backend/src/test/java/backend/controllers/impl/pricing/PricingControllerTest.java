package backend.controllers.impl.pricing;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.pricing.AppliedPromotionResponse;
import backend.dtos.responses.pricing.LineBreakdownResponse;
import backend.dtos.responses.pricing.PricingQuoteResponse;
import backend.models.enums.PromotionRuleType;
import backend.services.intf.products.PricingQuoteService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PricingControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);

    private PricingQuoteService pricingQuoteService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        pricingQuoteService = mock(PricingQuoteService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new PricingController(pricingQuoteService))
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
    void quote_authenticatedUserDelegatesWithUserId() throws Exception {
        authenticateAs(USER_ID);
        when(pricingQuoteService.quote(any(), eq(USER_ID))).thenReturn(response());

        mockMvc.perform(post("/pricing/quote")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "productId", TestIds.uuid(10),
                                        "quantity", 2
                                )),
                                "currency", "USD"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalTotal").value(39.98))
                .andExpect(jsonPath("$.appliedPromotions[0].ruleId").value(TestIds.uuid(20).toString()));
    }

    @Test
    void quote_anonymousPrincipalPassesNullUserId() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("guest", null));
        when(pricingQuoteService.quote(any(), eq(null))).thenReturn(response());

        mockMvc.perform(post("/pricing/quote")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "productId", TestIds.uuid(10),
                                        "quantity", 1
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(49.98));
    }

    @Test
    void quote_invalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/pricing/quote")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of("quantity", 0))
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quote_unexpectedRuntimeReturns500() throws Exception {
        when(pricingQuoteService.quote(any(), eq(null))).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/pricing/quote")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "productId", TestIds.uuid(10),
                                        "quantity", 1
                                ))
                        ))))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private PricingQuoteResponse response() {
        return new PricingQuoteResponse(
                List.of(new LineBreakdownResponse(
                        0,
                        TestIds.uuid(10),
                        null,
                        2,
                        new BigDecimal("24.99"),
                        new BigDecimal("10.00"),
                        new BigDecimal("39.98"),
                        List.of(TestIds.uuid(20)),
                        null
                )),
                List.of(new AppliedPromotionResponse(
                        TestIds.uuid(20),
                        "Spring Sale",
                        PromotionRuleType.PERCENTAGE_OFF,
                        new BigDecimal("10.00"),
                        TestIds.uuid(30)
                )),
                new BigDecimal("49.98"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                backend.models.enums.TaxSource.NONE,
                new BigDecimal("39.98"),
                "USD",
                List.of()
        );
    }
}
