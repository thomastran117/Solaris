package backend.services.impl.analytics;

import backend.dtos.responses.analytics.CategorySalesResponse;
import backend.dtos.responses.analytics.CompanyRevenueSummaryResponse;
import backend.dtos.responses.analytics.ProductPerformanceResponse;
import backend.dtos.responses.analytics.SlowMoversResponse;
import backend.repositories.CompanyAnalyticsRepository;
import backend.repositories.ProductRepository;
import backend.repositories.projections.CategorySalesProjection;
import backend.repositories.projections.DailyRevProjection;
import backend.repositories.projections.ProductSalesProjection;
import backend.repositories.projections.SlowMoverProjection;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyAnalyticsServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID   = TestIds.uuid(2);

    private ProductRepository productRepository;
    private CompanyAnalyticsRepository analyticsRepository;
    private CacheService cacheService;
    private CompanyAccessService companyAccessService;

    private CompanyAnalyticsServiceImpl service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        productRepository    = mock(ProductRepository.class);
        analyticsRepository  = mock(CompanyAnalyticsRepository.class);
        cacheService         = mock(CacheService.class);
        companyAccessService = mock(CompanyAccessService.class);

        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        service = new CompanyAnalyticsServiceImpl(
                productRepository, analyticsRepository, cacheService,
                mapper, companyAccessService);

        ReflectionTestUtils.setField(service, "cacheTtlSeconds", 3600L);
    }

    // ─── getRevenueSummary ────────────────────────────────────────────────────

    @Test
    void getRevenueSummary_cacheHit_doesNotCallRepository() throws Exception {
        CompanyRevenueSummaryResponse cached = new CompanyRevenueSummaryResponse(
                COMPANY_ID, 30, Instant.now(), Instant.now(),
                BigDecimal.TEN, 1L, BigDecimal.TEN, List.of());
        when(cacheService.get(anyString())).thenReturn(mapper.writeValueAsString(cached));

        service.getRevenueSummary(COMPANY_ID, OWNER_ID, 30);

        verify(analyticsRepository, never()).getDailyRevenue(any(), any(), any());
    }

    @Test
    void getRevenueSummary_cacheMissEmptyRows_returnsZeroTotals() {
        when(cacheService.get(anyString())).thenReturn(null);
        when(analyticsRepository.getDailyRevenue(eq(COMPANY_ID), any(), any())).thenReturn(List.of());

        CompanyRevenueSummaryResponse resp = service.getRevenueSummary(COMPANY_ID, OWNER_ID, 30);

        assertEquals(BigDecimal.ZERO, resp.totalRevenue());
        assertEquals(0L, resp.totalOrders());
        assertEquals(BigDecimal.ZERO, resp.avgOrderValue());
    }

    @Test
    void getRevenueSummary_cacheMissWithRows_computesCorrectTotals() {
        when(cacheService.get(anyString())).thenReturn(null);

        DailyRevProjection row = dailyRevRow(LocalDate.now(), new BigDecimal("100.00"), 2L, 5L);
        when(analyticsRepository.getDailyRevenue(eq(COMPANY_ID), any(), any())).thenReturn(List.of(row));

        CompanyRevenueSummaryResponse resp = service.getRevenueSummary(COMPANY_ID, OWNER_ID, 30);

        assertEquals(new BigDecimal("100.00"), resp.totalRevenue());
        assertEquals(5L, resp.totalOrders());
        assertEquals(new BigDecimal("20.00"), resp.avgOrderValue()); // 100 / 5
    }

    @Test
    void getRevenueSummary_redisThrows_fallsThroughToDb() {
        when(cacheService.get(anyString())).thenThrow(new RuntimeException("Redis down"));
        when(analyticsRepository.getDailyRevenue(eq(COMPANY_ID), any(), any())).thenReturn(List.of());

        CompanyRevenueSummaryResponse resp = service.getRevenueSummary(COMPANY_ID, OWNER_ID, 30);

        assertNotNull(resp);
        verify(analyticsRepository).getDailyRevenue(eq(COMPANY_ID), any(), any());
    }

    // ─── getCategorySales ──────────────────────────────────────────────────────

    @Test
    void getCategorySales_zeroGrandTotal_allSharesAreZero() {
        when(cacheService.get(anyString())).thenReturn(null);
        CategorySalesProjection row = categoryRow("Books", BigDecimal.ZERO, 0L, 0L);
        when(analyticsRepository.getCategorySales(eq(COMPANY_ID), any(), any())).thenReturn(List.of(row));

        CategorySalesResponse resp = service.getCategorySales(COMPANY_ID, OWNER_ID, 30);

        assertEquals(1, resp.categories().size());
        assertEquals(0.0, resp.categories().get(0).revenueSharePercent(), 0.001);
    }

    @Test
    void getCategorySales_multipleCategories_sharesApproximately100() {
        when(cacheService.get(anyString())).thenReturn(null);
        when(analyticsRepository.getCategorySales(eq(COMPANY_ID), any(), any())).thenReturn(List.of(
                categoryRow("A", new BigDecimal("60.00"), 3L, 2L),
                categoryRow("B", new BigDecimal("40.00"), 2L, 1L)
        ));

        CategorySalesResponse resp = service.getCategorySales(COMPANY_ID, OWNER_ID, 30);

        double total = resp.categories().stream().mapToDouble(e -> e.revenueSharePercent()).sum();
        assertEquals(100.0, total, 0.1);
    }

    // ─── getSlowMovers ────────────────────────────────────────────────────────

    @Test
    void getSlowMovers_zeroUnitsSold_velocityZeroAndNoSalesTrue() {
        when(cacheService.get(anyString())).thenReturn(null);
        SlowMoverProjection row = slowMoverRow(TestIds.uuid(10), "Widget", "SKU1", 10, BigDecimal.TEN, "USD", 0L, BigDecimal.ZERO);
        when(analyticsRepository.getSlowMovers(eq(COMPANY_ID), any(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(row));

        SlowMoversResponse resp = service.getSlowMovers(COMPANY_ID, OWNER_ID, 90);

        assertEquals(1, resp.items().size());
        assertEquals(0.0, resp.items().get(0).dailyVelocity(), 0.001);
        assertEquals(true, resp.items().get(0).neverSold());
    }

    // ─── getProductPerformance ─────────────────────────────────────────────────

    @Test
    void getProductPerformance_productInCurrentButNotPrior_revGrowthIs100() {
        when(cacheService.get(anyString())).thenReturn(null);

        UUID pid = TestIds.uuid(20);
        ProductSalesProjection current = productSalesRow(pid, "Widget", "SKU1", new BigDecimal("100.00"), 5L);
        when(productRepository.findTopByRevenue(eq(COMPANY_ID), anyInt(), any(), any()))
                .thenReturn(List.of(current))  // first call = current period
                .thenReturn(List.of());         // second call = prior period (empty)

        ProductPerformanceResponse resp = service.getProductPerformance(COMPANY_ID, OWNER_ID, 30);

        assertEquals(1, resp.products().size());
        assertEquals(100.0, resp.products().get(0).revenueGrowthPercent(), 0.001);
    }

    @Test
    void getProductPerformance_revenueRankOrderedCorrectly() {
        when(cacheService.get(anyString())).thenReturn(null);

        UUID pid1 = TestIds.uuid(21);
        UUID pid2 = TestIds.uuid(22);
        ProductSalesProjection low  = productSalesRow(pid1, "Low",  "SKU-L", new BigDecimal("10.00"), 1L);
        ProductSalesProjection high = productSalesRow(pid2, "High", "SKU-H", new BigDecimal("100.00"), 5L);

        when(productRepository.findTopByRevenue(eq(COMPANY_ID), anyInt(), any(), any()))
                .thenReturn(List.of(low, high))
                .thenReturn(List.of());

        ProductPerformanceResponse resp = service.getProductPerformance(COMPANY_ID, OWNER_ID, 30);

        // rank 1 = highest revenue
        assertEquals(pid2, resp.products().get(0).productId());
        assertEquals(1, resp.products().get(0).revenueRank());
        assertEquals(2, resp.products().get(1).revenueRank());
    }

    // ─── precomputeAll ────────────────────────────────────────────────────────

    @Test
    void precomputeAll_triggersFourComputeMethods() {
        when(cacheService.get(anyString())).thenReturn(null);
        when(analyticsRepository.getDailyRevenue(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.getCategorySales(any(), any(), any())).thenReturn(List.of());
        when(analyticsRepository.getSlowMovers(any(), any(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());
        when(productRepository.findTopByRevenue(any(), anyInt(), any(), any())).thenReturn(List.of());

        service.precomputeAll(COMPANY_ID);

        verify(analyticsRepository).getDailyRevenue(eq(COMPANY_ID), any(), any());
        verify(analyticsRepository).getCategorySales(eq(COMPANY_ID), any(), any());
        verify(analyticsRepository).getSlowMovers(eq(COMPANY_ID), any(), anyDouble(), anyDouble(), anyInt());
        verify(productRepository, org.mockito.Mockito.times(2)).findTopByRevenue(eq(COMPANY_ID), anyInt(), any(), any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static DailyRevProjection dailyRevRow(LocalDate day, BigDecimal rev, Long units, Long orders) {
        return new DailyRevProjection() {
            public LocalDate getDay()          { return day; }
            public BigDecimal getTotalRevenue(){ return rev; }
            public Long getTotalUnits()        { return units; }
            public Long getOrderCount()        { return orders; }
        };
    }

    private static CategorySalesProjection categoryRow(String category, BigDecimal rev, Long units, Long orders) {
        return new CategorySalesProjection() {
            public String getCategory()        { return category; }
            public BigDecimal getTotalRevenue(){ return rev; }
            public Long getTotalUnits()        { return units; }
            public Long getOrderCount()        { return orders; }
        };
    }

    private static SlowMoverProjection slowMoverRow(UUID id, String name, String sku, int stock,
                                                     BigDecimal price, String currency,
                                                     Long units, BigDecimal rev) {
        return new SlowMoverProjection() {
            public UUID getProductId()          { return id; }
            public String getProductName()      { return name; }
            public String getSku()              { return sku; }
            public Integer getCurrentStock()    { return stock; }
            public BigDecimal getPrice()        { return price; }
            public String getCurrency()         { return currency; }
            public Long getTotalUnitsSold()     { return units; }
            public BigDecimal getTotalRevenue() { return rev; }
        };
    }

    private static ProductSalesProjection productSalesRow(UUID id, String name, String sku,
                                                           BigDecimal rev, Long units) {
        return new ProductSalesProjection() {
            public UUID getProductId()          { return id; }
            public String getProductName()      { return name; }
            public String getSku()              { return sku; }
            public Integer getCurrentStock()    { return 10; }
            public BigDecimal getPrice()        { return rev; }
            public String getCurrency()         { return "USD"; }
            public Long getTotalUnitsSold()     { return units; }
            public BigDecimal getTotalRevenue() { return rev; }
        };
    }
}
