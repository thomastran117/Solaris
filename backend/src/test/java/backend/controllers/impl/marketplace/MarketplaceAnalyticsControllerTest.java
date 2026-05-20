package backend.controllers.impl.marketplace;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.marketplace.MarketplaceAnalyticsSummaryResponse;
import backend.dtos.responses.marketplace.TopVendorResponse;
import backend.dtos.responses.operations.DailyPoint;
import backend.dtos.responses.sla.VendorSLABreachResponse;
import backend.dtos.responses.sla.VendorSLAMetricResponse;
import backend.dtos.responses.sla.VendorSLAPolicyResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.vendors.VendorAnalyticsService;
import backend.services.intf.vendors.VendorSLAService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

class MarketplaceAnalyticsControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(2);
    private static final UUID VENDOR_ID = TestIds.uuid(3);
    private static final UUID BREACH_ID = TestIds.uuid(4);

    private VendorAnalyticsService vendorAnalyticsService;
    private VendorSLAService vendorSLAService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        vendorAnalyticsService = mock(VendorAnalyticsService.class);
        vendorSLAService = mock(VendorSLAService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MarketplaceAnalyticsController(vendorAnalyticsService, vendorSLAService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMarketplaceSummary_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(vendorAnalyticsService.getMarketplaceSummary(MARKETPLACE_ID, USER_ID, 30))
                .thenReturn(new MarketplaceAnalyticsSummaryResponse(
                        MARKETPLACE_ID, 30, Instant.parse("2026-04-19T00:00:00Z"), Instant.parse("2026-05-19T00:00:00Z"),
                        25L, new BigDecimal("1500.00"), new BigDecimal("150.00"), 0.10, 4L,
                        List.of(new DailyPoint(LocalDate.of(2026, 5, 19), 5, null))
                ));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketplaceId").value(MARKETPLACE_ID.toString()))
                .andExpect(jsonPath("$.activeVendors").value(4));
    }

    @Test
    void getTopVendors_passesDaysAndLimit() throws Exception {
        authenticateAs(USER_ID);
        when(vendorAnalyticsService.getTopVendors(MARKETPLACE_ID, USER_ID, 7, 5))
                .thenReturn(List.of(new TopVendorResponse(
                        VENDOR_ID, "Acme", 12L, new BigDecimal("999.99"), new BigDecimal("89.99"), 0.03
                )));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/analytics/top-vendors")
                        .param("days", "7")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vendorId").value(VENDOR_ID.toString()))
                .andExpect(jsonPath("$[0].vendorName").value("Acme"));
    }

    @Test
    void createPolicy_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(vendorSLAService.createPolicy(eq(MARKETPLACE_ID), eq(USER_ID), any()))
                .thenReturn(new VendorSLAPolicyResponse(
                        TestIds.uuid(10), MARKETPLACE_ID, "Default", 48, 24, 0.02, 0.05, 0.10,
                        "WARN", 30, true, Instant.parse("2026-05-19T00:00:00Z"), Instant.parse("2026-05-19T00:00:00Z")
                ));

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/sla/policies")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(policyBody("Default", 48, 24, 0.02, 0.05, 0.10, "WARN", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Default"));

        verify(vendorSLAService).createPolicy(eq(MARKETPLACE_ID), eq(USER_ID), any());
    }

    @Test
    void listPolicies_returnsConfiguredPolicies() throws Exception {
        when(vendorSLAService.listPolicies(MARKETPLACE_ID))
                .thenReturn(List.of(new VendorSLAPolicyResponse(
                        TestIds.uuid(12), MARKETPLACE_ID, "Default", 48, 24, 0.02, 0.05, 0.10,
                        "WARN", 30, true, Instant.parse("2026-05-19T00:00:00Z"), Instant.parse("2026-05-19T00:00:00Z")
                )));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/sla/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].marketplaceId").value(MARKETPLACE_ID.toString()));
    }

    @Test
    void getActivePolicy_returnsCurrentPolicy() throws Exception {
        when(vendorSLAService.getActivePolicy(MARKETPLACE_ID))
                .thenReturn(new VendorSLAPolicyResponse(
                        TestIds.uuid(13), MARKETPLACE_ID, "Current", 48, 24, 0.02, 0.05, 0.10,
                        "WARN", 30, true, Instant.parse("2026-05-19T00:00:00Z"), Instant.parse("2026-05-19T00:00:00Z")
                ));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/sla/policies/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Current"));
    }

    @Test
    void listMetrics_returnsPagedResponse() throws Exception {
        authenticateAs(USER_ID);
        when(vendorSLAService.listMetrics(MARKETPLACE_ID, VENDOR_ID, USER_ID, 0, 30))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(new VendorSLAMetricResponse(
                                TestIds.uuid(11), VENDOR_ID, MARKETPLACE_ID, LocalDate.of(2026, 5, 19),
                                10L, 12.0, 24.0, 0.01, 0.02, 0.03, 0.04, Instant.parse("2026-05-19T00:00:00Z"))),
                        PageRequest.of(0, 30),
                        1
                )));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/vendors/" + VENDOR_ID + "/sla/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].vendorId").value(VENDOR_ID.toString()));
    }

    @Test
    void getLatestMetric_returnsMostRecentMetric() throws Exception {
        authenticateAs(USER_ID);
        when(vendorSLAService.getLatestMetric(MARKETPLACE_ID, VENDOR_ID, USER_ID))
                .thenReturn(new VendorSLAMetricResponse(
                        TestIds.uuid(30), VENDOR_ID, MARKETPLACE_ID, LocalDate.of(2026, 5, 19),
                        10L, 12.0, 24.0, 0.01, 0.02, 0.03, 0.04, Instant.parse("2026-05-19T00:00:00Z")
                ));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/vendors/" + VENDOR_ID + "/sla/metrics/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(VENDOR_ID.toString()));
    }

    @Test
    void listBreaches_passesPagingToService() throws Exception {
        authenticateAs(USER_ID);
        when(vendorSLAService.listBreaches(MARKETPLACE_ID, VENDOR_ID, USER_ID, 1, 10))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(new VendorSLABreachResponse(
                                BREACH_ID, VENDOR_ID, TestIds.uuid(12), "lateShipmentRate", 0.20, 0.10,
                                Instant.parse("2026-05-18T00:00:00Z"), null, "OPEN", null,
                                Instant.parse("2026-05-18T00:00:00Z"))),
                        PageRequest.of(1, 10),
                        1
                )));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/vendors/" + VENDOR_ID + "/sla/breaches")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(BREACH_ID.toString()));
    }

    @Test
    void resolveBreach_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(vendorSLAService.resolveBreach(BREACH_ID, USER_ID, MARKETPLACE_ID))
                .thenReturn(new VendorSLABreachResponse(
                        BREACH_ID, VENDOR_ID, TestIds.uuid(12), "lateShipmentRate", 0.20, 0.10,
                        Instant.parse("2026-05-18T00:00:00Z"), Instant.parse("2026-05-19T00:00:00Z"),
                        "WARNED", null, Instant.parse("2026-05-18T00:00:00Z")
                ));

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/sla/breaches/" + BREACH_ID + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BREACH_ID.toString()))
                .andExpect(jsonPath("$.actionTaken").value("WARNED"));
    }

    @Test
    void listPolicies_propagatesAppHttpException() throws Exception {
        when(vendorSLAService.listPolicies(MARKETPLACE_ID))
                .thenThrow(new ResourceNotFoundException("Marketplace not found"));

        mockMvc.perform(get("/marketplaces/" + MARKETPLACE_ID + "/sla/policies"))
                .andExpect(status().isNotFound());
    }

    private Map<String, Object> policyBody(
            String name,
            int targetShipHours,
            int targetResponseHours,
            double maxCancellationRate,
            double maxRefundRate,
            double maxLateShipmentRate,
            String breachAction,
            int evaluationWindowDays) {
        return Map.of(
                "name", name,
                "targetShipHours", targetShipHours,
                "targetResponseHours", targetResponseHours,
                "maxCancellationRate", maxCancellationRate,
                "maxRefundRate", maxRefundRate,
                "maxLateShipmentRate", maxLateShipmentRate,
                "breachAction", breachAction,
                "evaluationWindowDays", evaluationWindowDays
        );
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
