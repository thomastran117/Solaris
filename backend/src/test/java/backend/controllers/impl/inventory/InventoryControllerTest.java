package backend.controllers.impl.inventory;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.general.CursorPagedResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.AdjustmentResponse;
import backend.dtos.responses.inventory.InventoryItemResponse;
import backend.dtos.responses.inventory.InventorySummaryResponse;
import backend.dtos.responses.inventory.ProductSalesMetricResponse;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.ProductStatus;
import backend.services.intf.SanitizationService;
import backend.services.intf.inventory.InventoryService;
import backend.services.intf.inventory.LocationInventoryService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID OTHER_PRODUCT_ID = TestIds.uuid(4);

    private InventoryService inventoryService;
    private LocationInventoryService locationInventoryService;
    private SanitizationService sanitizationService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        locationInventoryService = mock(LocationInventoryService.class);
        sanitizationService = mock(SanitizationService.class);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InventoryController(inventoryService, locationInventoryService, sanitizationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    @Test
    void getInventory_returnsCursorPageAndPassesFilters() throws Exception {
        authenticateAs(USER_ID);
        when(inventoryService.getInventory(
                COMPANY_ID, USER_ID, "LOW_STOCK", "desk", "Office", "Acme", ProductStatus.ACTIVE,
                2, 10, "cursor-1", 15))
                .thenReturn(new CursorPagedResponse<>(
                        List.of(itemResponse(PRODUCT_ID, "Desk", "LOW_STOCK", 4)),
                        "next-cursor",
                        true,
                        1
                ));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/inventory")
                        .param("stockStatus", "LOW_STOCK")
                        .param("q", "desk")
                        .param("category", "Office")
                        .param("brand", "Acme")
                        .param("status", "ACTIVE")
                        .param("minStock", "2")
                        .param("maxStock", "10")
                        .param("cursor", "cursor-1")
                        .param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.items[0].stockStatus").value("LOW_STOCK"))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void adjustStock_normalizesBodyAndDelegates() throws Exception {
        authenticateAs(USER_ID);
        when(inventoryService.adjustStock(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any()))
                .thenReturn(itemResponse(PRODUCT_ID, "Desk", "IN_STOCK", 9));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/inventory/" + PRODUCT_ID + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "delta", 3,
                                "reason", "RESTOCK",
                                "note", "Arrived from supplier"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.stock").value(9));

        verify(sanitizationService).normalize(any(backend.dtos.requests.inventory.AdjustStockRequest.class));
    }

    @Test
    void bulkAdjust_invalidBodyReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/inventory/bulk-adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTopPurchasedProducts_translatesDatesToInstants() throws Exception {
        authenticateAs(USER_ID);
        when(inventoryService.getTopPurchasedProducts(
                COMPANY_ID,
                USER_ID,
                5,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-03T23:59:59.999999999Z")))
                .thenReturn(List.of(new ProductSalesMetricResponse(
                        PRODUCT_ID, "Desk", "DESK-1", 8, new BigDecimal("19.99"),
                        "USD", 12L, new BigDecimal("239.88"), new BigDecimal("159.92"))));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/inventory/analytics/top-purchased")
                        .param("limit", "5")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$[0].totalUnitsSold").value(12));
    }

    @Test
    void getTopRevenueProducts_invalidDateRangeReturns400() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/inventory/analytics/top-revenue")
                        .param("from", "2026-05-10")
                        .param("to", "2026-05-09"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSummary_unexpectedRuntimeReturns500() throws Exception {
        authenticateAs(USER_ID);
        when(inventoryService.getSummary(COMPANY_ID, USER_ID)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/inventory/summary"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getCompanyAdjustmentHistory_passesReasonDatesAndPaging() throws Exception {
        authenticateAs(USER_ID);
        when(inventoryService.getCompanyAdjustmentHistory(
                COMPANY_ID,
                USER_ID,
                AdjustmentReason.RESTOCK,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-02T23:59:59.999999999Z"),
                PRODUCT_ID,
                OTHER_PRODUCT_ID,
                1,
                10))
                .thenReturn(new PagedResponse<>(new PageImpl<>(
                        List.of(new AdjustmentResponse(
                                TestIds.uuid(10), PRODUCT_ID, "Desk", null, null, null, USER_ID,
                                5, 1, 6, "RESTOCK", "warehouse count", Instant.parse("2026-05-02T12:00:00Z"))),
                        PageRequest.of(1, 10),
                        1
                )));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/inventory/adjustments")
                        .param("reason", "RESTOCK")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-02")
                        .param("productId", PRODUCT_ID.toString())
                        .param("userId", OTHER_PRODUCT_ID.toString())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reason").value("RESTOCK"));
    }

    @Test
    void updateSettings_propagatesAppHttpException() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new backend.exceptions.http.BadRequestException("invalid settings"))
                .when(inventoryService).updateSettings(eq(COMPANY_ID), eq(PRODUCT_ID), eq(USER_ID), any());

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/inventory/" + PRODUCT_ID + "/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("autoRestockEnabled", true))))
                .andExpect(status().isBadRequest());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private InventoryItemResponse itemResponse(UUID productId, String name, String stockStatus, int stock) {
        return new InventoryItemResponse(
                productId,
                name,
                "SKU-" + name,
                stock,
                2,
                null,
                20,
                false,
                null,
                "LOW_STOCK".equals(stockStatus),
                "OUT_OF_STOCK".equals(stockStatus),
                stockStatus,
                new BigDecimal("19.99"),
                "USD",
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }
}
