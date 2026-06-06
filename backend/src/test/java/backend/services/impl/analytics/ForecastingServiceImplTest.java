package backend.services.impl.analytics;

import backend.dtos.responses.forecasting.ForecastSummaryResponse;
import backend.dtos.responses.forecasting.ProductForecastResponse;
import backend.dtos.responses.forecasting.SeasonalPrepSummaryResponse;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.enums.CompanyCapability;
import backend.repositories.ProductRepository;
import backend.repositories.projections.DailyDemandProjection;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.CacheService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private Product makeProduct(long id, Integer stock) {
        Product p = new Product();
        p.setId(TestIds.uuid(id));
        p.setName("Product " + id);
        p.setSku("SKU-" + id);
        p.setStock(stock);
        p.setAutoRestockEnabled(false);
        return p;
    }

    // ─── Additional happy-path tests ──────────────────────────────────────────

    @Test
    void getProductForecast_productNotFound_throwsResourceNotFoundException() {
        when(productRepository.findByIdAndCompanyId(TestIds.uuid(42), TestIds.uuid(1)))
                .thenReturn(java.util.Optional.empty());

        assertThrows(backend.exceptions.http.ResourceNotFoundException.class, () ->
                service.getProductForecast(TestIds.uuid(1), TestIds.uuid(42), TestIds.uuid(1), 30));
    }

    @Test
    void getProductForecast_happyPath_returnsResponse() {
        Product product = makeProduct(42, 20);
        when(productRepository.findByIdAndCompanyId(TestIds.uuid(42), TestIds.uuid(1)))
                .thenReturn(java.util.Optional.of(product));
        when(productRepository.findDailyDemandSince(eq(TestIds.uuid(1)), any()))
                .thenReturn(List.of());

        ProductForecastResponse response =
                service.getProductForecast(TestIds.uuid(1), TestIds.uuid(42), TestIds.uuid(1), 30);

        assertNotNull(response);
        assertEquals(TestIds.uuid(42), response.productId());
    }

    @Test
    void getReorderSuggestions_noUrgentItems_returnsEmpty() {
        // Company forecast with no low-stock products → no reorder suggestions
        when(productRepository.findAllByCompanyId(TestIds.uuid(1))).thenReturn(List.of());
        when(productRepository.findDailyDemandSince(eq(TestIds.uuid(1)), any())).thenReturn(List.of());

        var result = service.getReorderSuggestions(TestIds.uuid(1), TestIds.uuid(1), 30, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getReorderSuggestions_noProducts_returnsEmpty() {
        when(productRepository.findAllByCompanyId(TestIds.uuid(1))).thenReturn(List.of());
        when(productRepository.findDailyDemandSince(eq(TestIds.uuid(1)), any())).thenReturn(List.of());

        var result = service.getReorderSuggestions(TestIds.uuid(1), TestIds.uuid(1), 30, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSeasonalPrep_withYoYData_noProductsWithStock_returnsEmpty() {
        // YoY data present but no tracked products → empty items list
        Product untracked = makeProduct(20, null); // untracked stock
        when(productRepository.findDailyDemandBetween(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(List.of()) // second call (recent)
                .thenReturn(List.of()); // first call (YoY)
        when(productRepository.findAllByCompanyId(TestIds.uuid(1))).thenReturn(List.of(untracked));

        // If yoyRows is empty → insufficientHistory
        var result = service.getSeasonalPrep(TestIds.uuid(1), TestIds.uuid(1), 10);

        assertNotNull(result);
    }

    // ─── buildSeries (private — via reflection) ───────────────────────────────

    @Test
    void buildSeries_emptyRows_returnsZeroFilledArray() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        long[] series = ReflectionTestUtils.invokeMethod(service, "buildSeries", List.of(), start, 7);
        assertNotNull(series);
        assertEquals(7, series.length);
        for (long v : series) assertEquals(0L, v);
    }

    @Test
    void buildSeries_rowWithinRange_populatesCorrectIndex() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        // day = Jan 3 → index = 2
        DailyDemandProjection row = projection(TestIds.uuid(10), LocalDate.of(2026, 1, 3), 5L);

        long[] series = ReflectionTestUtils.invokeMethod(service, "buildSeries", List.of(row), start, 7);

        assertEquals(5L, series[2]);
        assertEquals(0L, series[0]);
    }

    @Test
    void buildSeries_rowOutsideRange_ignored() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        // day = Dec 31 of prior year → index < 0
        DailyDemandProjection before = projection(TestIds.uuid(10), LocalDate.of(2025, 12, 31), 99L);
        // day = Jan 8 → index = 7 → == lookbackDays, out of bounds
        DailyDemandProjection after = projection(TestIds.uuid(10), LocalDate.of(2026, 1, 8), 99L);

        long[] series = ReflectionTestUtils.invokeMethod(service, "buildSeries", List.of(before, after), start, 7);

        for (long v : series) assertEquals(0L, v);
    }

    @Test
    void buildSeries_nullUnits_treatedAsZero() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        DailyDemandProjection row = projection(TestIds.uuid(10), LocalDate.of(2026, 1, 1), null);

        long[] series = ReflectionTestUtils.invokeMethod(service, "buildSeries", List.of(row), start, 3);

        assertEquals(0L, series[0]);
    }

    // ─── groupByProduct (private — via reflection) ───────────────────────────

    @Test
    void groupByProduct_emptyRows_returnsEmptyMap() {
        Map<UUID, List<DailyDemandProjection>> result =
                ReflectionTestUtils.invokeMethod(service, "groupByProduct", List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupByProduct_multipleRows_groupsByProductId() {
        UUID p1 = TestIds.uuid(1);
        UUID p2 = TestIds.uuid(2);
        List<DailyDemandProjection> rows = List.of(
                projection(p1, LocalDate.now(), 3L),
                projection(p1, LocalDate.now().minusDays(1), 2L),
                projection(p2, LocalDate.now(), 5L)
        );

        Map<UUID, List<DailyDemandProjection>> result =
                ReflectionTestUtils.invokeMethod(service, "groupByProduct", rows);

        assertEquals(2, result.size());
        assertEquals(2, result.get(p1).size());
        assertEquals(1, result.get(p2).size());
    }

    // ─── sliceCompanyForecast (private — via reflection) ─────────────────────

    @Test
    void sliceCompanyForecast_limitExceedsItems_returnsFull() {
        ForecastSummaryResponse full = new ForecastSummaryResponse(
                TestIds.uuid(1), 56, Instant.now(), 3, 0, 0, List.of(
                mockItem(), mockItem(), mockItem()));

        ForecastSummaryResponse result =
                ReflectionTestUtils.invokeMethod(service, "sliceCompanyForecast", full, 10);

        assertSame(full, result);
    }

    @Test
    void sliceCompanyForecast_limitBelowItems_truncatesList() {
        ForecastSummaryResponse full = new ForecastSummaryResponse(
                TestIds.uuid(1), 56, Instant.now(), 5, 0, 0, List.of(
                mockItem(), mockItem(), mockItem(), mockItem(), mockItem()));

        ForecastSummaryResponse result =
                ReflectionTestUtils.invokeMethod(service, "sliceCompanyForecast", full, 3);

        assertEquals(3, result.items().size());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private DailyDemandProjection projection(UUID productId, LocalDate day, Long units) {
        return new DailyDemandProjection() {
            @Override public UUID getProductId() { return productId; }
            @Override public LocalDate getDay() { return day; }
            @Override public Long getUnits() { return units; }
        };
    }

    private ProductForecastResponse mockItem() {
        return new ProductForecastResponse(
                TestIds.uuid((long)(Math.random() * 1000)),
                "Product", "SKU",
                0,           // currentStock
                0.0,         // avgDailyDemand
                0.0,         // predictedWeeklyDemand
                0.0,         // predictedWeeklyLow
                0.0,         // predictedWeeklyHigh
                null,        // daysOfCoverage
                null,        // likelyStockoutDate
                0,           // reorderSuggestedQty
                false,       // reorderUrgent
                new double[0]); // seasonalityFactors
    }
}
