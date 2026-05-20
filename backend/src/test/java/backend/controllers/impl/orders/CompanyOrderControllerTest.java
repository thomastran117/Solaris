package backend.controllers.impl.orders;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CompanyOrderResponse;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.return_.ReturnResponse;
import backend.dtos.responses.risk.RiskAssessmentResponse;
import backend.dtos.responses.risk.RiskReviewResponse;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskMode;
import backend.models.enums.RiskReviewStatus;
import backend.services.intf.orders.OrderService;
import backend.services.intf.returns.ReturnService;
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

class CompanyOrderControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID ORDER_ID = TestIds.uuid(3);

    private OrderService orderService;
    private ReturnService returnService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        returnService = mock(ReturnService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new CompanyOrderController(orderService, returnService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCompanyOrders_returnsPagedResults() throws Exception {
        authenticateAs(USER_ID);
        when(orderService.getCompanyOrders(COMPANY_ID, USER_ID, OrderStatus.PAID, 1, 10))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(companyOrderResponse()),
                        PageRequest.of(1, 10),
                        1
                )));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/orders")
                        .param("status", "PAID")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderId").value(ORDER_ID.toString()));
    }

    @Test
    void markAsShipped_delegatesRequestBody() throws Exception {
        authenticateAs(USER_ID);
        when(orderService.markAsShipped(eq(COMPANY_ID), eq(ORDER_ID), eq(USER_ID), any()))
                .thenReturn(companyOrderResponse());

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/orders/" + ORDER_ID + "/ship")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trackingNumber", "TRACK-123",
                                "carrier", "UPS",
                                "note", "Left dock"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-123"));
    }

    @Test
    void merchantInitiateReturn_returnsResponse() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.merchantInitiateReturn(eq(ORDER_ID), eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(returnResponse());

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/orders/" + ORDER_ID + "/returns")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "orderItemId", TestIds.uuid(10),
                                        "quantity", 1
                                )),
                                "reason", "DEFECTIVE",
                                "restockItems", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TestIds.uuid(30).toString()));
    }

    @Test
    void listRiskReviews_passesFilterAndPaging() throws Exception {
        authenticateAs(USER_ID);
        when(orderService.listRiskReviews(COMPANY_ID, USER_ID, RiskReviewStatus.PENDING, 0, 20))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(riskReviewResponse()),
                        PageRequest.of(0, 20),
                        1
                )));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/orders/risk-review")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void approveRiskReview_acceptsNullBody() throws Exception {
        authenticateAs(USER_ID);
        when(orderService.approveRiskReview(COMPANY_ID, ORDER_ID, USER_ID, null))
                .thenReturn(orderResponse("RESERVED"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/orders/" + ORDER_ID + "/risk/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()));

        verify(orderService).approveRiskReview(COMPANY_ID, ORDER_ID, USER_ID, null);
    }

    @Test
    void getOrderRisk_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        when(orderService.getOrderRisk(COMPANY_ID, ORDER_ID, USER_ID))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/orders/" + ORDER_ID + "/risk"))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private CompanyOrderResponse companyOrderResponse() {
        return new CompanyOrderResponse(
                ORDER_ID,
                TestIds.uuid(9),
                "PAID",
                "USD",
                new BigDecimal("49.98"),
                List.of(new OrderItemResponse(
                        TestIds.uuid(10),
                        TestIds.uuid(11),
                        "Desk",
                        null,
                        null,
                        null,
                        2,
                        new BigDecimal("24.99"),
                        null,
                        null,
                        FulfillmentStatus.PACKED,
                        null,
                        null,
                        BigDecimal.ZERO
                )),
                "TRACK-123",
                "UPS",
                Instant.parse("2026-05-19T00:00:00Z"),
                null,
                null,
                "Left dock",
                0L,
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }

    private ReturnResponse returnResponse() {
        return new ReturnResponse(
                TestIds.uuid(30),
                ORDER_ID,
                USER_ID,
                "PENDING",
                "DAMAGED",
                null,
                null,
                true,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                0L,
                "NONE",
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z"),
                null,
                null
        );
    }

    private RiskReviewResponse riskReviewResponse() {
        return new RiskReviewResponse(
                TestIds.uuid(40),
                ORDER_ID,
                TestIds.uuid(41),
                RiskReviewStatus.PENDING,
                null,
                null,
                null,
                Instant.parse("2026-05-19T00:00:00Z"),
                82,
                "Velocity spike"
        );
    }

    private OrderResponse orderResponse(String status) {
        return new OrderResponse(
                ORDER_ID,
                USER_ID,
                List.of(),
                new BigDecimal("49.98"),
                "USD",
                status,
                "pi_1",
                "secret",
                null,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
