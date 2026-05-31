package backend.services.impl.analytics;

import backend.dtos.responses.forecasting.ForecastSummaryResponse;
import backend.dtos.responses.forecasting.ProductForecastResponse;
import backend.dtos.responses.forecasting.SeasonalPrepSummaryResponse;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.enums.CompanyCapability;
import backend.repositories.ProductRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.CacheService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ForecastingServiceImplTest {

    private CompanyAccessService companyAccessService;
    private ProductRepository productRepository;
    private CacheService cacheService;
    private ForecastingServiceImpl service;

    @BeforeEach
    void setUp() {
        companyAccessService = Mockito.mock(CompanyAccessService.class);
        productRepository    = Mockito.mock(ProductRepository.class);
        cacheService         = Mockito.mock(CacheService.class);
        ObjectMapper mapper  = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ForecastingServiceImpl(companyAccessService, productRepository, cacheService, mapper);
        // Default: ownership check passes
        when(companyAccessService.require(any(), any(), any())).thenReturn(new Company());
    }

    // ─── ownership enforcement ────────────────────────────────────────────────

    @Test
    void getCompanyForecast_nonOwner_throwsForbidden() {
        doThrow(new ForbiddenException("no access"))
                .when(companyAccessService).require(eq(TestIds.uuid(1)), eq(TestIds.uuid(99)), any());
        assertThrows(ForbiddenException.class,
                () -> service.getCompanyForecast(TestIds.uuid(1), TestIds.uuid(99), 56, 50));
    }

    @Test
    void getProductForecast_nonOwner_throwsForbidden() {
        doThrow(new ForbiddenException("no access"))
                .when(companyAccessService).require(eq(TestIds.uuid(1)), eq(TestIds.uuid(99)), any());
        assertThrows(ForbiddenException.class,
                () -> service.getProductForecast(TestIds.uuid(1), TestIds.uuid(42), TestIds.uuid(99), 56));
    }

    @Test
    void getReorderSuggestions_nonOwner_throwsForbidden() {
        doThrow(new ForbiddenException("no access"))
                .when(companyAccessService).require(eq(TestIds.uuid(1)), eq(TestIds.uuid(99)), any());
        assertThrows(ForbiddenException.class,
                () -> service.getReorderSuggestions(TestIds.uuid(1), TestIds.uuid(99), 56, 20));
    }

    @Test
    void getSeasonalPrep_nonOwner_throwsForbidden() {
        doThrow(new ForbiddenException("no access"))
                .when(companyAccessService).require(eq(TestIds.uuid(1)), eq(TestIds.uuid(99)), any());
        assertThrows(ForbiddenException.class,
                () -> service.getSeasonalPrep(TestIds.uuid(1), TestIds.uuid(99), 50));
    }

    // ─── cache hit — company forecast ─────────────────────────────────────────

    @Test
    void getCompanyForecast_cacheHit_doesNotQueryDb() throws Exception {
        ForecastSummaryResponse cached = new ForecastSummaryResponse(
                TestIds.uuid(1), 56, Instant.now(), 0, 0, 0, List.of());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(cacheService.get(anyString())).thenReturn(mapper.writeValueAsString(cached));

        ForecastSummaryResponse result = service.getCompanyForecast(TestIds.uuid(1), TestIds.uuid(1), 56, 50);

        assertEquals(TestIds.uuid(1), result.companyId());
        verify(productRepository, never()).findDailyDemandSince(any(), any());
    }

    // ─── cache miss — company forecast happy path ─────────────────────────────

    @Test
    void getCompanyForecast_cacheMiss_returnsCorrectShape() {
        when(cacheService.get(anyString())).thenReturn(null);
        when(productRepository.findDailyDemandSince(eq(TestIds.uuid(1)), any())).thenReturn(List.of());
        when(productRepository.findAllByCompanyId(TestIds.uuid(1))).thenReturn(List.of(makeProduct(10L, 50)));

        ForecastSummaryResponse result = service.getCompanyForecast(TestIds.uuid(1), TestIds.uuid(1), 56, 50);

        assertEquals(TestIds.uuid(1), result.companyId());
        assertEquals(56, result.windowDays());
        assertEquals(1, result.items().size());
    }

    // ─── zero-sales product ───────────────────────────────────────────────────

    @Test
    void getCompanyForecast_noSales_productHasZeroDemand() {
        when(cacheService.get(anyString())).thenReturn(null);
        when(productRepository.findDailyDemandSince(eq(TestIds.uuid(1)), any())).thenReturn(List.of());
        when(productRepository.findAllByCompanyId(TestIds.uuid(1))).thenReturn(List.of(makeProduct(10L, 5)));

        ForecastSummaryResponse result = service.getCompanyForecast(TestIds.uuid(1), TestIds.uuid(1), 56, 50);

        ProductForecastResponse item = result.items().get(0);
        assertEquals(0.0, item.avgDailyDemand());
        assertNull(item.likelyStockoutDate());
        assertEquals(0, item.reorderSuggestedQty());
    }

    // ─── reorder urgency ──────────────────────────────────────────────────────

    @Test
    void getCompanyForecast_lowStockBelowThreshold_flaggedUrgent() {
        when(cacheService.get(anyString())).thenReturn(null);
        Product p = makeProduct(10L, 2);
        p.setLowStockThreshold(5);
        when(productRepository.findAllByCompanyId(TestIds.uuid(1))).thenReturn(List.of(p));
        when(productRepository.findDailyDemandSince(eq(TestIds.uuid(1)), any())).thenReturn(List.of());

        ForecastSummaryResponse result = service.getCompanyForecast(TestIds.uuid(1), TestIds.uuid(1), 56, 50);

        assertTrue(result.items().get(0).reorderUrgent());
    }

    // ─── seasonal prep — insufficient history ────────────────────────────────

    @Test
    void getSeasonalPrep_noYoYData_returnsInsufficientHistory() {
        when(productRepository.findDailyDemandBetween(eq(TestIds.uuid(1)), any(), any())).thenReturn(List.of());

        SeasonalPrepSummaryResponse result = service.getSeasonalPrep(TestIds.uuid(1), TestIds.uuid(1), 50);

        assertTrue(result.items().isEmpty());
        assertEquals("INSUFFICIENT_HISTORY", result.reason());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Product makeProduct(long id, int stock) {
        Product p = new Product();
        p.setId(TestIds.uuid(id));
        p.setName("Product " + id);
        p.setSku("SKU-" + id);
        p.setStock(stock);
        p.setAutoRestockEnabled(false);
        return p;
    }
}
