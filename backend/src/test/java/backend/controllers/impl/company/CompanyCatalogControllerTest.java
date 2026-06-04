package backend.controllers.impl.company;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.product.CatalogSearchResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.products.ProductService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyCatalogControllerTest {

    private ProductService productService;
    private MockMvc mockMvc;

    private static final UUID COMPANY_ID = TestIds.uuid(1);

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        CompanyCatalogController controller = new CompanyCatalogController(productService);

        // @Validated enables AOP method validation; it does not run in standalone MockMvc.
        // NoOpValidator bypasses any residual constraint-processing wired by standaloneSetup.
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    // ─── GET /companies/{companyId}/catalog/search ────────────────────────────

    @Test
    void searchCatalog_returns200WithResults() throws Exception {
        when(productService.searchCompanyCatalog(eq(COMPANY_ID), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(makeCatalogResponse());

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void searchCatalog_passesAllFiltersToService() throws Exception {
        when(productService.searchCompanyCatalog(any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(makeCatalogResponse());

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search")
                        .param("q", "laptop")
                        .param("category", "Electronics")
                        .param("brand", "Acme")
                        .param("minPrice", "10.00")
                        .param("maxPrice", "500.00")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk());

        verify(productService).searchCompanyCatalog(
                eq(COMPANY_ID),
                eq("laptop"),
                eq("Electronics"),
                eq("Acme"),
                eq(new BigDecimal("10.00")),
                eq(new BigDecimal("500.00")),
                eq(1),
                eq(10),
                eq("name"),
                eq("asc"));
    }

    @Test
    void searchCatalog_defaultPaginationParams_passedToService() throws Exception {
        when(productService.searchCompanyCatalog(any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(makeCatalogResponse());

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search"))
                .andExpect(status().isOk());

        verify(productService).searchCompanyCatalog(
                eq(COMPANY_ID), isNull(), isNull(), isNull(),
                isNull(), isNull(),
                eq(0), eq(20), eq("createdAt"), eq("desc"));
    }

    @Test
    void searchCatalog_nullableFilters_passedAsNull() throws Exception {
        when(productService.searchCompanyCatalog(any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(makeCatalogResponse());

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search")
                        .param("q", "shoes"))
                .andExpect(status().isOk());

        verify(productService).searchCompanyCatalog(
                eq(COMPANY_ID), eq("shoes"),
                isNull(), isNull(), isNull(), isNull(),
                anyInt(), anyInt(), any(), any());
    }

    @Test
    void searchCatalog_serviceThrowsAppHttpException_propagates404() throws Exception {
        when(productService.searchCompanyCatalog(any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Company not found"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchCatalog_unexpectedServiceException_returns500() throws Exception {
        when(productService.searchCompanyCatalog(any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("ES cluster down"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/catalog/search"))
                .andExpect(status().isInternalServerError());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CatalogSearchResponse makeCatalogResponse() {
        return new CatalogSearchResponse(new PageImpl<>(List.of()), null);
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
