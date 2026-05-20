package backend.controllers.impl.inventory;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.inventory.StockNotificationResponse;
import backend.services.intf.RateLimitService;
import backend.services.intf.inventory.StockNotificationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockNotificationControllerTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID PRODUCT_ID = TestIds.uuid(2);
    private static final UUID NOTIFICATION_ID = TestIds.uuid(3);

    private StockNotificationService stockNotificationService;
    private RateLimitService rateLimitService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        stockNotificationService = mock(StockNotificationService.class);
        rateLimitService = mock(RateLimitService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new StockNotificationController(stockNotificationService, rateLimitService))
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
    void subscribe_enforcesRateLimitAndDelegates() throws Exception {
        authenticateAs(USER_ID);
        when(stockNotificationService.subscribe(eq(USER_ID), any()))
                .thenReturn(new StockNotificationResponse(
                        NOTIFICATION_ID, PRODUCT_ID, "Desk", null, null, "PENDING",
                        Instant.parse("2026-05-19T00:00:00Z")));

        mockMvc.perform(post("/stock-notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", PRODUCT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(rateLimitService).enforce("stock:subscribe", USER_ID.toString(), 30, 60);
    }

    @Test
    void cancel_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/stock-notifications/" + NOTIFICATION_ID))
                .andExpect(status().isNoContent());

        verify(stockNotificationService).cancel(USER_ID, NOTIFICATION_ID);
    }

    @Test
    void list_returnsUserNotifications() throws Exception {
        authenticateAs(USER_ID);
        when(stockNotificationService.listByUser(USER_ID))
                .thenReturn(List.of(new StockNotificationResponse(
                        NOTIFICATION_ID, PRODUCT_ID, "Desk", null, null, "PENDING",
                        Instant.parse("2026-05-19T00:00:00Z"))));

        mockMvc.perform(get("/stock-notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(PRODUCT_ID.toString()));
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
