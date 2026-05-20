package backend.controllers.impl.inventory;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.inventory.LocationResponse;
import backend.dtos.responses.inventory.LocationStockResponse;
import backend.models.enums.LocationType;
import backend.services.intf.SanitizationService;
import backend.services.intf.inventory.LocationInventoryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocationInventoryControllerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID = TestIds.uuid(2);
    private static final UUID LOCATION_ID = TestIds.uuid(3);
    private static final UUID PRODUCT_ID = TestIds.uuid(4);
    private static final UUID VARIANT_ID = TestIds.uuid(5);

    private LocationInventoryService locationInventoryService;
    private SanitizationService sanitizationService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        locationInventoryService = mock(LocationInventoryService.class);
        sanitizationService = mock(SanitizationService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new LocationInventoryController(locationInventoryService, sanitizationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createLocation_returns201AndNormalizesRequest() throws Exception {
        authenticateAs(USER_ID);
        when(locationInventoryService.createLocation(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(locationResponse());

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/inventory/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Toronto Warehouse",
                                "code", "TOR-1",
                                "type", "WAREHOUSE"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(LOCATION_ID.toString()))
                .andExpect(jsonPath("$.code").value("TOR-1"));

        verify(sanitizationService).normalize(any(backend.dtos.requests.inventory.CreateLocationRequest.class));
    }

    @Test
    void setLocationStock_passesVariantIdAndReturns200() throws Exception {
        authenticateAs(USER_ID);
        when(locationInventoryService.setLocationStock(
                eq(COMPANY_ID), eq(LOCATION_ID), eq(PRODUCT_ID), eq(USER_ID), any(), eq(VARIANT_ID)))
                .thenReturn(locationStockResponse());

        mockMvc.perform(put("/companies/" + COMPANY_ID + "/inventory/locations/" + LOCATION_ID
                        + "/stock/" + PRODUCT_ID)
                        .param("variantId", VARIANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stock", 7,
                                "lowStockThreshold", 2
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(VARIANT_ID.toString()))
                .andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void deleteLocation_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/inventory/locations/" + LOCATION_ID))
                .andExpect(status().isNoContent());

        verify(locationInventoryService).deleteLocation(COMPANY_ID, LOCATION_ID, USER_ID);
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private LocationResponse locationResponse() {
        return new LocationResponse(
                LOCATION_ID,
                COMPANY_ID,
                "Toronto Warehouse",
                "TOR-1",
                null,
                "Toronto",
                "Canada",
                true,
                0,
                null,
                null,
                BigDecimal.ZERO,
                LocationType.WAREHOUSE,
                1,
                null,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }

    private LocationStockResponse locationStockResponse() {
        return new LocationStockResponse(
                TestIds.uuid(10),
                LOCATION_ID,
                "Toronto Warehouse",
                PRODUCT_ID,
                VARIANT_ID,
                7,
                2,
                "IN_STOCK",
                Instant.parse("2026-05-19T00:00:00Z")
        );
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
