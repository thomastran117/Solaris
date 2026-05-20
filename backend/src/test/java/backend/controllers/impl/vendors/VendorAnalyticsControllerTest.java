package backend.controllers.impl.vendors;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.vendor.VendorAnalyticsSummaryResponse;
import backend.dtos.responses.vendor.VendorOrdersMetricResponse;
import backend.dtos.responses.vendor.VendorPayoutsMetricResponse;
import backend.dtos.responses.vendor.VendorRefundsMetricResponse;
import backend.dtos.responses.vendor.VendorRevenueResponse;
import backend.dtos.responses.vendor.VendorTopProductsResponse;
import backend.exceptions.http.ForbiddenException;
import backend.services.intf.vendors.VendorAnalyticsService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VendorAnalyticsControllerTest {

    private static final UUID USER_ID        = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(2);
    private static final UUID VENDOR_ID      = TestIds.uuid(3);

    private VendorAnalyticsService vendorAnalyticsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        vendorAnalyticsService = mock(VendorAnalyticsService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new VendorAnalyticsController(vendorAnalyticsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── GET analytics/summary ────────────────────────────────────────────────

    @Test
    void getSummary_returns200() throws Exception {
        when(vendorAnalyticsService.getSummary(eq(VENDOR_ID), eq(MARKETPLACE_ID), eq(30), eq(USER_ID)))
                .thenReturn(mock(VendorAnalyticsSummaryResponse.class));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/summary",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getSummary_forbidden_returns403() throws Exception {
        when(vendorAnalyticsService.getSummary(any(), any(), anyInt(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/summary",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_customDays_passedToService() throws Exception {
        when(vendorAnalyticsService.getSummary(eq(VENDOR_ID), eq(MARKETPLACE_ID), eq(90), eq(USER_ID)))
                .thenReturn(mock(VendorAnalyticsSummaryResponse.class));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/summary",
                        MARKETPLACE_ID, VENDOR_ID)
                        .param("days", "90"))
                .andExpect(status().isOk());

        verify(vendorAnalyticsService).getSummary(VENDOR_ID, MARKETPLACE_ID, 90, USER_ID);
    }

    // ─── GET analytics/revenue ────────────────────────────────────────────────

    @Test
    void getRevenue_returns200() throws Exception {
        when(vendorAnalyticsService.getRevenue(eq(VENDOR_ID), eq(MARKETPLACE_ID), eq(30), eq(USER_ID)))
                .thenReturn(mock(VendorRevenueResponse.class));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/revenue",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getRevenue_forbidden_returns403() throws Exception {
        when(vendorAnalyticsService.getRevenue(any(), any(), anyInt(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/revenue",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isForbidden());
    }

    // ─── GET analytics/top-products ───────────────────────────────────────────

    @Test
    void getTopProducts_returns200() throws Exception {
        when(vendorAnalyticsService.getTopProducts(eq(VENDOR_ID), eq(MARKETPLACE_ID), eq(30), eq(10), eq(USER_ID)))
                .thenReturn(mock(VendorTopProductsResponse.class));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/top-products",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk());
    }

    // ─── GET analytics/orders ─────────────────────────────────────────────────

    @Test
    void getOrders_returns200() throws Exception {
        when(vendorAnalyticsService.getOrders(eq(VENDOR_ID), eq(MARKETPLACE_ID), eq(30), eq(USER_ID)))
                .thenReturn(mock(VendorOrdersMetricResponse.class));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/orders",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk());
    }

    // ─── GET analytics/refunds ────────────────────────────────────────────────

    @Test
    void getRefunds_returns200() throws Exception {
        when(vendorAnalyticsService.getRefunds(eq(VENDOR_ID), eq(MARKETPLACE_ID), eq(30), eq(USER_ID)))
                .thenReturn(mock(VendorRefundsMetricResponse.class));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/refunds",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk());
    }

    // ─── GET analytics/payouts ────────────────────────────────────────────────

    @Test
    void getPayouts_returns200() throws Exception {
        when(vendorAnalyticsService.getPayouts(eq(VENDOR_ID), eq(MARKETPLACE_ID), eq(10), eq(USER_ID)))
                .thenReturn(mock(VendorPayoutsMetricResponse.class));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/payouts",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getPayouts_serviceError_returns500() throws Exception {
        when(vendorAnalyticsService.getPayouts(any(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/analytics/payouts",
                        MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
