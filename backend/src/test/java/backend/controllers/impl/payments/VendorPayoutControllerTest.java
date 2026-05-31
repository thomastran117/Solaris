package backend.controllers.impl.payments;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.vendor.VendorAdjustmentResponse;
import backend.dtos.responses.vendor.VendorBalanceResponse;
import backend.dtos.responses.vendor.VendorPayoutResponse;
import backend.services.intf.payments.VendorPayoutService;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VendorPayoutControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID VENDOR_ID = TestIds.uuid(2);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(3);
    private static final UUID PAYOUT_ID = TestIds.uuid(4);

    private VendorPayoutService vendorPayoutService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        vendorPayoutService = mock(VendorPayoutService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new VendorPayoutController(vendorPayoutService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    @Test
    void getBalance_returnsVendorBalance() throws Exception {
        authenticateAs(USER_ID);
        when(vendorPayoutService.getBalance(VENDOR_ID, USER_ID))
                .thenReturn(new VendorBalanceResponse(
                        VENDOR_ID, 100L, 200L, 50L, 500L, 75L, 300L, "USD",
                        Instant.parse("2026-05-19T00:00:00Z")));

        mockMvc.perform(get("/vendors/" + VENDOR_ID + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(VENDOR_ID.toString()))
                .andExpect(jsonPath("$.availableCents").value(200));
    }

    @Test
    void listPayouts_returnsPagedResults() throws Exception {
        authenticateAs(USER_ID);
        when(vendorPayoutService.listPayouts(eq(VENDOR_ID), eq(backend.models.enums.PayoutStatus.PAID), eq(1), eq(10), eq(USER_ID)))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(payoutResponse()),
                        PageRequest.of(1, 10),
                        1
                )));

        mockMvc.perform(get("/vendors/" + VENDOR_ID + "/payouts")
                        .param("status", "PAID")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PAYOUT_ID.toString()));
    }

    @Test
    void getPayoutDetail_returnsPayout() throws Exception {
        authenticateAs(USER_ID);
        when(vendorPayoutService.getPayoutDetail(PAYOUT_ID, VENDOR_ID, USER_ID)).thenReturn(payoutResponse());

        mockMvc.perform(get("/vendors/" + VENDOR_ID + "/payouts/" + PAYOUT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void triggerManualPayout_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(vendorPayoutService.triggerManualPayout(VENDOR_ID, MARKETPLACE_ID, USER_ID)).thenReturn(payoutResponse());

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/payouts/run")
                        .param("vendorId", VENDOR_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PAYOUT_ID.toString()));
    }

    @Test
    void createAdjustment_invalidBodyReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/vendors/" + VENDOR_ID + "/adjustments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountCents", 0,
                                "currency", "US",
                                "reason", ""
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAdjustment_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(vendorPayoutService.createAdjustment(eq(VENDOR_ID), eq(USER_ID), any()))
                .thenReturn(new VendorAdjustmentResponse(
                        TestIds.uuid(10), VENDOR_ID, 500L, "USD", "Goodwill",
                        USER_ID, null, Instant.parse("2026-05-19T00:00:00Z")));

        mockMvc.perform(post("/marketplaces/" + MARKETPLACE_ID + "/vendors/" + VENDOR_ID + "/adjustments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amountCents", 500,
                                "currency", "USD",
                                "reason", "Goodwill"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendorId").value(VENDOR_ID.toString()))
                .andExpect(jsonPath("$.amountCents").value(500));
    }

    private VendorPayoutResponse payoutResponse() {
        return new VendorPayoutResponse(
                PAYOUT_ID,
                VENDOR_ID,
                MARKETPLACE_ID,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-15T00:00:00Z"),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                new BigDecimal("5.00"),
                new BigDecimal("95.00"),
                "USD",
                "PAID",
                "tr_123",
                null,
                Instant.parse("2026-05-16T00:00:00Z"),
                Instant.parse("2026-05-17T00:00:00Z"),
                Instant.parse("2026-05-16T00:00:00Z"),
                Instant.parse("2026-05-17T00:00:00Z"),
                List.of()
        );
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
