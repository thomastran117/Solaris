package backend.controllers.impl.returns;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.return_.ReturnItemResponse;
import backend.dtos.responses.return_.ReturnResponse;
import backend.models.enums.RefundStatus;
import backend.models.enums.ReturnReason;
import backend.models.enums.ReturnStatus;
import backend.services.intf.returns.ReturnService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class ReturnControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID ORDER_ID = TestIds.uuid(2);

    private ReturnService returnService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        returnService = mock(ReturnService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ReturnController(returnService))
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
    void requestReturn_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.requestReturn(eq(ORDER_ID), eq(USER_ID), any())).thenReturn(response());

        mockMvc.perform(post("/orders/" + ORDER_ID + "/returns")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "orderItemId", TestIds.uuid(10),
                                        "quantityToReturn", 1
                                )),
                                "reason", "WRONG_ITEM",
                                "buyerNote", "Received wrong item",
                                "evidenceUrls", List.of("https://example.test/evidence.jpg")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TestIds.uuid(20).toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void getReturnsByOrder_returnsList() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.getReturnsByOrder(ORDER_ID, USER_ID)).thenReturn(List.of(response()));

        mockMvc.perform(get("/orders/" + ORDER_ID + "/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$[0].reason").value("WRONG_ITEM"));
    }

    @Test
    void requestReturn_invalidBodyReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/orders/" + ORDER_ID + "/returns")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of(
                                        "orderItemId", TestIds.uuid(10),
                                        "quantityToReturn", 0
                                )),
                                "buyerNote", "",
                                "reason", "WRONG_ITEM"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsByOrder_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        when(returnService.getReturnsByOrder(ORDER_ID, USER_ID)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/orders/" + ORDER_ID + "/returns"))
                .andExpect(status().isInternalServerError());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ReturnResponse response() {
        return new ReturnResponse(
                TestIds.uuid(20),
                ORDER_ID,
                USER_ID,
                ReturnStatus.REQUESTED.name(),
                ReturnReason.WRONG_ITEM.name(),
                "Received wrong item",
                null,
                false,
                List.of(new ReturnItemResponse(
                        TestIds.uuid(21),
                        TestIds.uuid(10),
                        "Desk Lamp",
                        null,
                        null,
                        1,
                        new BigDecimal("19.99"),
                        false,
                        null
                )),
                List.of("https://example.test/evidence.jpg"),
                null,
                null,
                null,
                null,
                0L,
                RefundStatus.NONE.name(),
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z"),
                null,
                null
        );
    }
}
