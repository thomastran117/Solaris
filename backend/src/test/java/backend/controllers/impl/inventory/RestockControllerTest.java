package backend.controllers.impl.inventory;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.RestockRequestResponse;
import backend.models.enums.RestockStatus;
import backend.services.intf.SanitizationService;
import backend.services.intf.inventory.RestockService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RestockControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID = TestIds.uuid(2);
    private static final UUID RESTOCK_ID = TestIds.uuid(3);
    private static final UUID PRODUCT_ID = TestIds.uuid(4);

    private RestockService restockService;
    private SanitizationService sanitizationService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        restockService = mock(RestockService.class);
        sanitizationService = mock(SanitizationService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new RestockController(restockService, sanitizationService))
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
    void listRestockRequests_returnsPagedResponse() throws Exception {
        authenticateAs(USER_ID);
        when(restockService.listRestockRequests(COMPANY_ID, USER_ID, RestockStatus.PENDING, PRODUCT_ID, 1, 10))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(restockResponse()),
                        PageRequest.of(1, 10),
                        1
                )));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/inventory/restock")
                        .param("status", "PENDING")
                        .param("productId", PRODUCT_ID.toString())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(RESTOCK_ID.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void createRestockRequest_returns201AndNormalizes() throws Exception {
        authenticateAs(USER_ID);
        when(restockService.createRestockRequest(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(restockResponse());

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/inventory/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", PRODUCT_ID,
                                "requestedQty", 12
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedQty").value(12));

        verify(sanitizationService).normalize(any(backend.dtos.requests.inventory.CreateRestockRequest.class));
    }

    @Test
    void deleteRestockRequest_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/inventory/restock/" + RESTOCK_ID))
                .andExpect(status().isNoContent());

        verify(restockService).deleteRestockRequest(COMPANY_ID, RESTOCK_ID, USER_ID);
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private RestockRequestResponse restockResponse() {
        return new RestockRequestResponse(
                RESTOCK_ID,
                COMPANY_ID,
                PRODUCT_ID,
                "Desk",
                null,
                null,
                null,
                null,
                12,
                null,
                LocalDate.of(2026, 5, 25),
                "PENDING",
                "restock note",
                USER_ID,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }
}
