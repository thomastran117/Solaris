package backend.controllers.impl.pricing;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.pricing.PayoutAttributionResponse;
import backend.exceptions.http.BadRequestException;
import backend.services.intf.pricing.PricingReportService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PricingReportControllerTest {

    private PricingReportService pricingReportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pricingReportService = mock(PricingReportService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new PricingReportController(pricingReportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPayoutAttribution_returnsReport() throws Exception {
        when(pricingReportService.getPayoutAttribution(
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z")))
                .thenReturn(new PayoutAttributionResponse(
                        Instant.parse("2026-05-01T00:00:00Z"),
                        Instant.parse("2026-05-31T00:00:00Z"),
                        List.of(new PayoutAttributionResponse.Row(
                                TestIds.uuid(1),
                                "Funded Co",
                                new BigDecimal("25.00"),
                                3L,
                                2L
                        )),
                        new BigDecimal("25.00"),
                        3L
                ));

        mockMvc.perform(get("/admin/pricing/payout-attribution")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-05-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].companyName").value("Funded Co"))
                .andExpect(jsonPath("$.grandTotalSavings").value(25.00));
    }

    @Test
    void getPayoutAttribution_propagatesAppHttpException() throws Exception {
        when(pricingReportService.getPayoutAttribution(null, null))
                .thenThrow(new BadRequestException("bad window"));

        mockMvc.perform(get("/admin/pricing/payout-attribution"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPayoutAttribution_unexpectedRuntimeReturns500() throws Exception {
        when(pricingReportService.getPayoutAttribution(null, null))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/admin/pricing/payout-attribution"))
                .andExpect(status().isInternalServerError());
    }
}
