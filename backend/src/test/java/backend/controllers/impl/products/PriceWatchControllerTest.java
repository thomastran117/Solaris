package backend.controllers.impl.products;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.products.PriceWatcherResponse;
import backend.services.intf.RateLimitService;
import backend.services.intf.products.PriceWatchService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PriceWatchControllerTest {

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID PRODUCT_ID = TestIds.uuid(2);
    private static final UUID WATCHER_ID = TestIds.uuid(3);

    private PriceWatchService priceWatchService;
    private RateLimitService rateLimitService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        priceWatchService = mock(PriceWatchService.class);
        rateLimitService  = mock(RateLimitService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PriceWatchController(priceWatchService, rateLimitService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void watch_authenticatedUser_delegatesAndReturns200() throws Exception {
        authenticateAs(USER_ID);
        when(priceWatchService.watchProduct(USER_ID, PRODUCT_ID))
                .thenReturn(new PriceWatcherResponse(
                        WATCHER_ID, PRODUCT_ID, "Desk", 4999,
                        Instant.parse("2026-05-31T00:00:00Z")));

        mockMvc.perform(post("/products/{productId}/price-watch", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(WATCHER_ID.toString()))
                .andExpect(jsonPath("$.watchPriceCents").value(4999));

        verify(rateLimitService).enforce("price-watch:watch", USER_ID.toString(), 30, 60);
    }

    @Test
    void unwatch_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/products/{productId}/price-watch", PRODUCT_ID))
                .andExpect(status().isNoContent());

        verify(priceWatchService).unwatchProduct(USER_ID, PRODUCT_ID);
    }

    @Test
    void list_returnsPaginatedWatches() throws Exception {
        authenticateAs(USER_ID);
        when(priceWatchService.getWatchedProducts(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(
                        new PriceWatcherResponse(WATCHER_ID, PRODUCT_ID, "Desk", 4999,
                                Instant.parse("2026-05-31T00:00:00Z")))));

        mockMvc.perform(get("/price-watches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.content[0].watchPriceCents").value(4999));
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
