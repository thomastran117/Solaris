package backend.controllers.impl.marketplace;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.marketplace.MarketplaceProfileResponse;
import backend.services.intf.marketplace.MarketplaceService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketplaceControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(3);

    private MarketplaceService marketplaceService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        marketplaceService = mock(MarketplaceService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new MarketplaceController(marketplaceService))
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
    void createMarketplace_returns201AndDelegatesAuthenticatedUser() throws Exception {
        authenticateAs(USER_ID);
        when(marketplaceService.createMarketplace(eq(USER_ID), eq(COMPANY_ID), any()))
                .thenReturn(profileResponse());

        mockMvc.perform(post("/marketplaces/companies/" + COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "slug", "market-one",
                                "payoutSchedule", "WEEKLY",
                                "holdPeriodDays", 7,
                                "defaultCurrency", "USD",
                                "acceptingApplications", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MARKETPLACE_ID.toString()))
                .andExpect(jsonPath("$.slug").value("market-one"));
    }

    @Test
    void createMarketplace_invalidSlugReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/marketplaces/companies/" + COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("slug", "Bad Slug"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMarketplace_returns200() throws Exception {
        when(marketplaceService.getMarketplace(MARKETPLACE_ID)).thenReturn(profileResponse());

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()));
    }

    @Test
    void updateMarketplace_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(marketplaceService.updateMarketplace(eq(MARKETPLACE_ID), eq(USER_ID), any()))
                .thenReturn(profileResponse());

        mockMvc.perform(patch("/marketplaces/" + MARKETPLACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payoutSchedule", "MONTHLY",
                                "holdPeriodDays", 14
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutSchedule").value("WEEKLY"));

        verify(marketplaceService).updateMarketplace(eq(MARKETPLACE_ID), eq(USER_ID), any());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private MarketplaceProfileResponse profileResponse() {
        return new MarketplaceProfileResponse(
                MARKETPLACE_ID,
                COMPANY_ID,
                "ShopWave Market",
                "market-one",
                null,
                "WEEKLY",
                7,
                "USD",
                true,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }
}
