package backend.controllers.impl.orders;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CommissionRecordResponse;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.SubOrderResponse;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.SubOrderStatus;
import backend.services.intf.orders.SubOrderService;
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

class SubOrderControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID VENDOR_ID = TestIds.uuid(2);
    private static final UUID SUB_ORDER_ID = TestIds.uuid(3);

    private SubOrderService subOrderService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        subOrderService = mock(SubOrderService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new SubOrderController(subOrderService))
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
    void list_returnsPagedSubOrders() throws Exception {
        authenticateAs(USER_ID);
        when(subOrderService.listVendorSubOrders(VENDOR_ID, SubOrderStatus.PENDING, 0, 20, USER_ID))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(subOrderResponse()),
                        PageRequest.of(0, 20),
                        1
                )));

        mockMvc.perform(get("/vendors/" + VENDOR_ID + "/sub-orders").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(SUB_ORDER_ID.toString()));
    }

    @Test
    void get_returnsSubOrder() throws Exception {
        authenticateAs(USER_ID);
        when(subOrderService.getSubOrder(SUB_ORDER_ID, VENDOR_ID, USER_ID)).thenReturn(subOrderResponse());

        mockMvc.perform(get("/vendors/" + VENDOR_ID + "/sub-orders/" + SUB_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorCompanyName").value("Vendor Co"));
    }

    @Test
    void ship_invalidBodyReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/vendors/" + VENDOR_ID + "/sub-orders/" + SUB_ORDER_ID + "/ship")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "trackingNumber", "",
                                "carrier", ""
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_delegatesRequestBody() throws Exception {
        authenticateAs(USER_ID);
        when(subOrderService.cancelSubOrder(eq(SUB_ORDER_ID), eq(VENDOR_ID), any(), eq(USER_ID)))
                .thenReturn(subOrderResponse());

        mockMvc.perform(post("/vendors/" + VENDOR_ID + "/sub-orders/" + SUB_ORDER_ID + "/cancel")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Out of stock"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SUB_ORDER_ID.toString()));
    }

    @Test
    void commission_returnsComputedRecord() throws Exception {
        authenticateAs(USER_ID);
        when(subOrderService.getCommissionRecord(SUB_ORDER_ID, VENDOR_ID, USER_ID))
                .thenReturn(new CommissionRecordResponse(
                        TestIds.uuid(20),
                        SUB_ORDER_ID,
                        VENDOR_ID,
                        TestIds.uuid(21),
                        new BigDecimal("0.10"),
                        new BigDecimal("50.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("45.00"),
                        "USD",
                        Instant.parse("2026-05-19T00:00:00Z")
                ));

        mockMvc.perform(get("/vendors/" + VENDOR_ID + "/sub-orders/" + SUB_ORDER_ID + "/commission"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subOrderId").value(SUB_ORDER_ID.toString()))
                .andExpect(jsonPath("$.commissionAmount").value(5.00));
    }

    @Test
    void pack_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        when(subOrderService.markPacked(SUB_ORDER_ID, VENDOR_ID, USER_ID))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/vendors/" + VENDOR_ID + "/sub-orders/" + SUB_ORDER_ID + "/pack"))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private SubOrderResponse subOrderResponse() {
        return new SubOrderResponse(
                SUB_ORDER_ID,
                TestIds.uuid(10),
                VENDOR_ID,
                TestIds.uuid(11),
                "Vendor Co",
                "PENDING",
                new BigDecimal("49.99"),
                new BigDecimal("49.99"),
                "USD",
                new BigDecimal("5.00"),
                new BigDecimal("44.99"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z"),
                List.of(new OrderItemResponse(
                        TestIds.uuid(12),
                        TestIds.uuid(13),
                        "Desk",
                        null,
                        null,
                        null,
                        1,
                        new BigDecimal("49.99"),
                        null,
                        null,
                        FulfillmentStatus.PENDING,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.ZERO,
                        FulfillmentMethod.DELIVERY
                ))
        );
    }
}
