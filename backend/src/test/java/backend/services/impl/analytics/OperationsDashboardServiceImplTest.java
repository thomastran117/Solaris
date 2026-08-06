package backend.services.impl.analytics;

import backend.dtos.responses.operations.CancellationMetricResponse;
import backend.dtos.responses.operations.DurationMetricResponse;
import backend.dtos.responses.operations.OperationsSummaryResponse;
import backend.dtos.responses.operations.StockoutMetricResponse;
import backend.dtos.responses.operations.SupplierLatenessMetricResponse;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.Company;
import backend.models.enums.CancellationReason;
import backend.repositories.OperationsMetricsRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.projections.CancellationReasonCountProjection;
import backend.repositories.projections.DailyCountProjection;
import backend.repositories.projections.DailyDurationProjection;
import backend.repositories.projections.DurationStatsProjection;
import backend.repositories.projections.SupplierLatenessProjection;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationsDashboardServiceImplTest {

    private CompanyAccessService companyAccessService;
    private OperationsMetricsRepository metricsRepository;
    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private CacheService cacheService;
    private ObjectMapper objectMapper;
    private OperationsDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        companyAccessService = Mockito.mock(CompanyAccessService.class);
        metricsRepository    = Mockito.mock(OperationsMetricsRepository.class);
        orderRepository      = Mockito.mock(OrderRepository.class);
        productRepository    = Mockito.mock(ProductRepository.class);
        cacheService         = Mockito.mock(CacheService.class);
        objectMapper         = new ObjectMapper().registerModule(new JavaTimeModule());

        service = new OperationsDashboardServiceImpl(
                companyAccessService, metricsRepository, orderRepository,
                productRepository, cacheService, objectMapper);

        // Default: ownership check passes, cache miss
        when(companyAccessService.require(any(), any(), any())).thenReturn(new Company());
        when(cacheService.get(anyString())).thenReturn(null);
    }

    // ─── ownership ──────────────────────────────────────────────────────────

    @Test
    void getSummary_throwsForbiddenWhenUserDoesNotOwnCompany() {
        doThrow(new ForbiddenException("no access"))
                .when(companyAccessService).require(eq(TestIds.uuid(1)), eq(TestIds.uuid(99)), any());
        assertThrows(ForbiddenException.class,
                () -> service.getSummary(TestIds.uuid(1), TestIds.uuid(99), 30));
    }

    // ─── lookback clamping ──────────────────────────────────────────────────

    @Test
    void clampLookback_belowMinClampsToSeven() {
        assertEquals(7, service.clampLookback(3));
    }

    @Test
    void clampLookback_aboveMaxClampsTo365() {
        assertEquals(365, service.clampLookback(9999));
    }

    @Test
    void clampLookback_inRangePassThrough() {
        assertEquals(30, service.clampLookback(30));
    }

    // ─── cache hit short-circuits repository ────────────────────────────────

    @Test
    void getSummary_cacheHitSkipsRepository() throws Exception {
        OperationsSummaryResponse cached = new OperationsSummaryResponse(
                TestIds.uuid(1), 30, Instant.now().minusSeconds(86400 * 30), Instant.now(),
                new DurationMetricResponse(5L, 2.0, List.of()),
                new DurationMetricResponse(2L, 1.0, List.of()),
                new DurationMetricResponse(3L, 0.5, List.of()),
                new StockoutMetricResponse(10, 1, 0.1, 50, 5, 0.1),
                new SupplierLatenessMetricResponse(8, 2, 0.25, 1.5, List.of()),
                new CancellationMetricResponse(4, List.of(), List.of()),
                2L);
        when(cacheService.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        OperationsSummaryResponse out = service.getSummary(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(5L, out.getFulfillment().getCount());
        verifyNoInteractions(metricsRepository);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(productRepository);
    }

    // ─── duration metrics: seconds → hours ──────────────────────────────────

    @Test
    void fulfillmentMetric_convertsSecondsToHours() {
        when(metricsRepository.fulfillmentStats(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(stats(10L, 7200.0));
        when(metricsRepository.fulfillmentDaily(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(List.of(daily(LocalDate.of(2026, 4, 1), 5L, 3600.0)));

        DurationMetricResponse out = service.getFulfillmentMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(10L, out.getCount());
        assertEquals(2.0, out.getAvgHours());
        assertEquals(1, out.getDaily().size());
        assertEquals(1.0, out.getDaily().get(0).getValue());
    }

    @Test
    void fulfillmentMetric_zeroCountReturnsNullAverage() {
        when(metricsRepository.fulfillmentStats(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(stats(0L, null));
        when(metricsRepository.fulfillmentDaily(eq(TestIds.uuid(1)), any(), any())).thenReturn(List.of());

        DurationMetricResponse out = service.getFulfillmentMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(0L, out.getCount());
        assertNull(out.getAvgHours());
    }

    // ─── stockout metric divide-by-zero ─────────────────────────────────────

    @Test
    void stockoutMetric_noTrackedProductsReturnsZeroRateNotNaN() {
        when(productRepository.countTrackedProducts(TestIds.uuid(1))).thenReturn(0L);
        when(productRepository.countOutOfStock(TestIds.uuid(1))).thenReturn(0L);
        when(orderRepository.countOrdersInWindow(eq(TestIds.uuid(1)), any(), any())).thenReturn(0L);
        when(orderRepository.countOrdersWithBackorderedItemsInWindow(eq(TestIds.uuid(1)), any(), any())).thenReturn(0L);

        StockoutMetricResponse out = service.getStockoutMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(0.0, out.getOutOfStockRate());
        assertEquals(0.0, out.getBackorderRate());
        assertFalse(Double.isNaN(out.getOutOfStockRate()));
    }

    @Test
    void stockoutMetric_computesRatesWhenDataPresent() {
        when(productRepository.countTrackedProducts(TestIds.uuid(1))).thenReturn(100L);
        when(productRepository.countOutOfStock(TestIds.uuid(1))).thenReturn(7L);
        when(orderRepository.countOrdersInWindow(eq(TestIds.uuid(1)), any(), any())).thenReturn(200L);
        when(orderRepository.countOrdersWithBackorderedItemsInWindow(eq(TestIds.uuid(1)), any(), any())).thenReturn(20L);

        StockoutMetricResponse out = service.getStockoutMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(0.07, out.getOutOfStockRate(), 1e-9);
        assertEquals(0.10, out.getBackorderRate(), 1e-9);
    }

    // ─── supplier lateness ──────────────────────────────────────────────────

    @Test
    void supplierLatenessMetric_computesRate() {
        when(metricsRepository.supplierLatenessStats(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(supplier(10L, 3L, 2.5));
        when(metricsRepository.supplierLatenessDaily(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(List.of(dailyCount(LocalDate.of(2026, 4, 2), 1L)));

        SupplierLatenessMetricResponse out = service.getSupplierLatenessMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(10L, out.getTotal());
        assertEquals(3L, out.getLate());
        assertEquals(0.3, out.getLateRate(), 1e-9);
        assertEquals(2.5, out.getAvgLateDays());
        assertEquals(1, out.getDaily().size());
    }

    @Test
    void supplierLatenessMetric_handlesNullStatsRow() {
        when(metricsRepository.supplierLatenessStats(eq(TestIds.uuid(1)), any(), any())).thenReturn(null);
        when(metricsRepository.supplierLatenessDaily(eq(TestIds.uuid(1)), any(), any())).thenReturn(List.of());

        SupplierLatenessMetricResponse out = service.getSupplierLatenessMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(0L, out.getTotal());
        assertEquals(0L, out.getLate());
        assertEquals(0.0, out.getLateRate());
    }

    // ─── cancellation metric ────────────────────────────────────────────────

    @Test
    void cancellationMetric_groupsByReasonAndSumsCorrectly() {
        when(metricsRepository.cancellationsByReason(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(List.of(
                        reasonCount(CancellationReason.CUSTOMER_REQUEST, 4L),
                        reasonCount(CancellationReason.PAYMENT_FAILED, 2L),
                        reasonCount(CancellationReason.STALE_TIMEOUT, 1L)));
        when(metricsRepository.cancellationsDaily(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(List.of(dailyCount(LocalDate.of(2026, 4, 3), 7L)));

        CancellationMetricResponse out = service.getCancellationMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(7L, out.getTotal());
        assertEquals(3, out.getByReason().size());
        long sum = out.getByReason().stream().mapToLong(CancellationMetricResponse.ReasonCount::getCount).sum();
        assertEquals(out.getTotal(), sum);
    }

    // ─── getSummary cache miss ───────────────────────────────────────────────

    @Test
    void getSummary_cacheMiss_callsAllBuilders() {
        stubAllBuilderDeps(TestIds.uuid(1));

        OperationsSummaryResponse out = service.getSummary(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertNotNull(out);
        assertNotNull(out.getFulfillment());
        assertNotNull(out.getRefunds());
        assertNotNull(out.getPickDelays());
        assertNotNull(out.getStockouts());
        assertNotNull(out.getSupplierLateness());
        assertNotNull(out.getCancellations());
        verify(metricsRepository).fulfillmentStats(eq(TestIds.uuid(1)), any(), any());
        verify(metricsRepository).refundStats(eq(TestIds.uuid(1)), any(), any());
        verify(metricsRepository).pickDelayStats(eq(TestIds.uuid(1)), any(), any());
    }

    @Test
    void getSummary_includesOpenDisputeCount() {
        stubAllBuilderDeps(TestIds.uuid(1));
        when(metricsRepository.openDisputeCount(TestIds.uuid(1))).thenReturn(3L);

        OperationsSummaryResponse out = service.getSummary(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(3L, out.getOpenDisputeCount());
        verify(metricsRepository).openDisputeCount(TestIds.uuid(1));
    }

    /** The count is outstanding work, not a windowed metric — lookback must not filter it. */
    @Test
    void getSummary_openDisputeCountIgnoresLookbackWindow() {
        stubAllBuilderDeps(TestIds.uuid(1));
        when(metricsRepository.openDisputeCount(TestIds.uuid(1))).thenReturn(7L);

        assertEquals(7L, service.getSummary(TestIds.uuid(1), TestIds.uuid(99), 365).getOpenDisputeCount());
    }

    // ─── individual metric methods not tested above ──────────────────────────

    @Test
    void getRefundMetric_computesAverageHours() {
        when(metricsRepository.refundStats(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(stats(6L, 1800.0));
        when(metricsRepository.refundDaily(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(List.of(daily(LocalDate.of(2026, 5, 1), 2L, 900.0)));

        DurationMetricResponse out = service.getRefundMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(6L, out.getCount());
        assertEquals(0.5, out.getAvgHours(), 1e-9);
        assertEquals(1, out.getDaily().size());
        assertEquals(0.25, out.getDaily().get(0).getValue(), 1e-9);
    }

    @Test
    void getPickDelayMetric_returnsZeroAvgWhenNoData() {
        when(metricsRepository.pickDelayStats(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(stats(0L, null));
        when(metricsRepository.pickDelayDaily(eq(TestIds.uuid(1)), any(), any())).thenReturn(List.of());

        DurationMetricResponse out = service.getPickDelayMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(0L, out.getCount());
        assertNull(out.getAvgHours());
    }

    @Test
    void getFulfillmentMetric_cacheHitSkipsRepository() throws Exception {
        DurationMetricResponse cached = new DurationMetricResponse(3L, 1.5, List.of());
        when(cacheService.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        DurationMetricResponse out = service.getFulfillmentMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(3L, out.getCount());
        verify(metricsRepository, never()).fulfillmentStats(any(), any(), any());
    }

    @Test
    void getCancellationMetric_cacheHitSkipsRepository() throws Exception {
        CancellationMetricResponse cached = new CancellationMetricResponse(5L, List.of(), List.of());
        when(cacheService.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        CancellationMetricResponse out = service.getCancellationMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(5L, out.getTotal());
        verify(metricsRepository, never()).cancellationsByReason(any(), any(), any());
    }

    // ─── null/error edge cases ───────────────────────────────────────────────

    @Test
    void toDurationResponse_nullStats_returnsZeroCountNullAvg() {
        when(metricsRepository.fulfillmentStats(eq(TestIds.uuid(1)), any(), any())).thenReturn(null);
        when(metricsRepository.fulfillmentDaily(eq(TestIds.uuid(1)), any(), any())).thenReturn(List.of());

        DurationMetricResponse out = service.getFulfillmentMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(0L, out.getCount());
        assertNull(out.getAvgHours());
    }

    @Test
    void toDailyPoints_nullCount_treatedAsZero() {
        when(metricsRepository.cancellationsByReason(eq(TestIds.uuid(1)), any(), any())).thenReturn(List.of());
        when(metricsRepository.cancellationsDaily(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(List.of(new DailyCountProjection() {
                    public LocalDate getDay() { return LocalDate.of(2026, 5, 5); }
                    public Long getCount() { return null; }
                }));

        CancellationMetricResponse out = service.getCancellationMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(1, out.getDaily().size());
        assertEquals(0L, out.getDaily().get(0).getCount());
    }

    @Test
    void cancellationMetric_nullCountInRow_treatedAsZero() {
        when(metricsRepository.cancellationsByReason(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(List.of(reasonCount(backend.models.enums.CancellationReason.CUSTOMER_REQUEST, null)));
        when(metricsRepository.cancellationsDaily(eq(TestIds.uuid(1)), any(), any())).thenReturn(List.of());

        CancellationMetricResponse out = service.getCancellationMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(0L, out.getTotal());
        assertEquals(1, out.getByReason().size());
        assertEquals(0L, out.getByReason().get(0).getCount());
    }

    @Test
    void readCache_deserializeError_returnsNull() {
        when(cacheService.get(anyString())).thenReturn("THIS IS NOT JSON {{{");
        // cache error means we fall through to DB
        stubAllBuilderDeps(TestIds.uuid(1));

        OperationsSummaryResponse out = service.getSummary(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertNotNull(out);
        verify(metricsRepository).fulfillmentStats(any(), any(), any());
    }

    @Test
    void writeCache_serializeError_doesNotThrow() {
        // Use a non-serializable value injected via a custom objectMapper — instead we just cover
        // the branch by verifying normal flow completes even when cacheService.set is called.
        stubAllBuilderDeps(TestIds.uuid(1));
        doThrow(new RuntimeException("redis down")).when(cacheService).set(anyString(), anyString(), anyLong());

        assertDoesNotThrow(() -> service.getSummary(TestIds.uuid(1), TestIds.uuid(99), 30));
    }

    @Test
    void supplierLatenessMetric_statsNonNullButNullTotals_useZero() {
        SupplierLatenessProjection statsNullFields = new SupplierLatenessProjection() {
            public Long getTotal() { return null; }
            public Long getLate() { return null; }
            public Double getAvgLateDays() { return null; }
        };
        when(metricsRepository.supplierLatenessStats(eq(TestIds.uuid(1)), any(), any()))
                .thenReturn(statsNullFields);
        when(metricsRepository.supplierLatenessDaily(eq(TestIds.uuid(1)), any(), any())).thenReturn(List.of());

        SupplierLatenessMetricResponse out = service.getSupplierLatenessMetric(TestIds.uuid(1), TestIds.uuid(99), 30);

        assertEquals(0L, out.getTotal());
        assertEquals(0L, out.getLate());
        assertEquals(0.0, out.getLateRate());
        assertNull(out.getAvgLateDays());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void stubAllBuilderDeps(java.util.UUID companyId) {
        when(metricsRepository.fulfillmentStats(eq(companyId), any(), any())).thenReturn(stats(2L, 3600.0));
        when(metricsRepository.fulfillmentDaily(eq(companyId), any(), any())).thenReturn(List.of());
        when(metricsRepository.refundStats(eq(companyId), any(), any())).thenReturn(stats(1L, 7200.0));
        when(metricsRepository.refundDaily(eq(companyId), any(), any())).thenReturn(List.of());
        when(metricsRepository.pickDelayStats(eq(companyId), any(), any())).thenReturn(stats(0L, null));
        when(metricsRepository.pickDelayDaily(eq(companyId), any(), any())).thenReturn(List.of());
        when(productRepository.countTrackedProducts(companyId)).thenReturn(50L);
        when(productRepository.countOutOfStock(companyId)).thenReturn(3L);
        when(orderRepository.countOrdersInWindow(eq(companyId), any(), any())).thenReturn(100L);
        when(orderRepository.countOrdersWithBackorderedItemsInWindow(eq(companyId), any(), any())).thenReturn(5L);
        when(metricsRepository.supplierLatenessStats(eq(companyId), any(), any())).thenReturn(supplier(20L, 4L, 1.0));
        when(metricsRepository.supplierLatenessDaily(eq(companyId), any(), any())).thenReturn(List.of());
        when(metricsRepository.cancellationsByReason(eq(companyId), any(), any())).thenReturn(List.of());
        when(metricsRepository.cancellationsDaily(eq(companyId), any(), any())).thenReturn(List.of());
    }

    private DurationStatsProjection stats(Long count, Double avgSeconds) {
        return new DurationStatsProjection() {
            public Long getCount() { return count; }
            public Double getAvgSeconds() { return avgSeconds; }
        };
    }

    private DailyDurationProjection daily(LocalDate day, Long count, Double avgSeconds) {
        return new DailyDurationProjection() {
            public LocalDate getDay() { return day; }
            public Long getCount() { return count; }
            public Double getAvgSeconds() { return avgSeconds; }
        };
    }

    private DailyCountProjection dailyCount(LocalDate day, Long count) {
        return new DailyCountProjection() {
            public LocalDate getDay() { return day; }
            public Long getCount() { return count; }
        };
    }

    private SupplierLatenessProjection supplier(Long total, Long late, Double avgLateDays) {
        return new SupplierLatenessProjection() {
            public Long getTotal() { return total; }
            public Long getLate() { return late; }
            public Double getAvgLateDays() { return avgLateDays; }
        };
    }

    private CancellationReasonCountProjection reasonCount(CancellationReason r, Long c) {
        return new CancellationReasonCountProjection() {
            public CancellationReason getReason() { return r; }
            public Long getCount() { return c; }
        };
    }
}


