package backend.services.impl.analytics;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.responses.operations.CancellationMetricResponse;
import backend.dtos.responses.operations.DailyPoint;
import backend.dtos.responses.operations.DurationMetricResponse;
import backend.dtos.responses.operations.OperationsSummaryResponse;
import backend.dtos.responses.operations.StockoutMetricResponse;
import backend.dtos.responses.operations.SupplierLatenessMetricResponse;
import backend.models.enums.CompanyCapability;
import backend.repositories.OperationsMetricsRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.projections.CancellationReasonCountProjection;
import backend.repositories.projections.DailyCountProjection;
import backend.repositories.projections.DailyDurationProjection;
import backend.repositories.projections.DurationStatsProjection;
import backend.repositories.projections.SupplierLatenessProjection;
import backend.services.intf.CacheService;
import backend.services.intf.analytics.OperationsDashboardService;
import backend.services.intf.company.CompanyAccessService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class OperationsDashboardServiceImpl implements OperationsDashboardService {

    private static final Logger log = LoggerFactory.getLogger(OperationsDashboardServiceImpl.class);

    private static final int MIN_LOOKBACK_DAYS = 7;
    private static final int MAX_LOOKBACK_DAYS = 365;
    private static final long CACHE_TTL_SECONDS = 15 * 60;
    private static final String CACHE_PREFIX = "ops:";

    private static final double SECONDS_PER_HOUR = 3600.0;

    private final CompanyAccessService companyAccessService;
    private final OperationsMetricsRepository metricsRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public OperationsDashboardServiceImpl(
            CompanyAccessService companyAccessService,
            OperationsMetricsRepository metricsRepository,
            OrderRepository orderRepository,
            ProductRepository productRepository,
            CacheService cacheService,
            ObjectMapper objectMapper) {
        this.companyAccessService = companyAccessService;
        this.metricsRepository = metricsRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public OperationsSummaryResponse getSummary(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwnership(companyId, ownerId);
        int days = clampLookback(lookbackDays);
        Window w = window(days);
        String key = cacheKey(companyId, "summary", days);

        OperationsSummaryResponse cached = readCache(key, OperationsSummaryResponse.class);
        if (cached != null) return cached;

        OperationsSummaryResponse out = new OperationsSummaryResponse(
                companyId, days, w.from, w.to,
                buildFulfillment(companyId, w),
                buildRefund(companyId, w),
                buildPickDelay(companyId, w),
                buildStockout(companyId, w),
                buildSupplierLateness(companyId, w),
                buildCancellation(companyId, w));
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public DurationMetricResponse getFulfillmentMetric(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwnership(companyId, ownerId);
        return cachedDuration(companyId, "fulfillment", lookbackDays, this::buildFulfillment);
    }

    @Override
    @Transactional(readOnly = true)
    public DurationMetricResponse getRefundMetric(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwnership(companyId, ownerId);
        return cachedDuration(companyId, "refunds", lookbackDays, this::buildRefund);
    }

    @Override
    @Transactional(readOnly = true)
    public DurationMetricResponse getPickDelayMetric(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwnership(companyId, ownerId);
        return cachedDuration(companyId, "pick-delays", lookbackDays, this::buildPickDelay);
    }

    @Override
    @Transactional(readOnly = true)
    public StockoutMetricResponse getStockoutMetric(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwnership(companyId, ownerId);
        int days = clampLookback(lookbackDays);
        Window w = window(days);
        String key = cacheKey(companyId, "stockouts", days);
        StockoutMetricResponse cached = readCache(key, StockoutMetricResponse.class);
        if (cached != null) return cached;

        StockoutMetricResponse out = buildStockout(companyId, w);
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierLatenessMetricResponse getSupplierLatenessMetric(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwnership(companyId, ownerId);
        int days = clampLookback(lookbackDays);
        Window w = window(days);
        String key = cacheKey(companyId, "supplier-lateness", days);
        SupplierLatenessMetricResponse cached = readCache(key, SupplierLatenessMetricResponse.class);
        if (cached != null) return cached;

        SupplierLatenessMetricResponse out = buildSupplierLateness(companyId, w);
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public CancellationMetricResponse getCancellationMetric(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwnership(companyId, ownerId);
        int days = clampLookback(lookbackDays);
        Window w = window(days);
        String key = cacheKey(companyId, "cancellations", days);
        CancellationMetricResponse cached = readCache(key, CancellationMetricResponse.class);
        if (cached != null) return cached;

        CancellationMetricResponse out = buildCancellation(companyId, w);
        writeCache(key, out);
        return out;
    }

    // -------------------------------------------------------------------------
    // Builders (no caching here — caller decides)
    // -------------------------------------------------------------------------

    private DurationMetricResponse buildFulfillment(UUID companyId, Window w) {
        DurationStatsProjection stats = metricsRepository.fulfillmentStats(companyId, w.from, w.to);
        List<DailyDurationProjection> daily = metricsRepository.fulfillmentDaily(companyId, w.from, w.to);
        return toDurationResponse(stats, daily);
    }

    private DurationMetricResponse buildRefund(UUID companyId, Window w) {
        DurationStatsProjection stats = metricsRepository.refundStats(companyId, w.from, w.to);
        List<DailyDurationProjection> daily = metricsRepository.refundDaily(companyId, w.from, w.to);
        return toDurationResponse(stats, daily);
    }

    private DurationMetricResponse buildPickDelay(UUID companyId, Window w) {
        DurationStatsProjection stats = metricsRepository.pickDelayStats(companyId, w.from, w.to);
        List<DailyDurationProjection> daily = metricsRepository.pickDelayDaily(companyId, w.from, w.to);
        return toDurationResponse(stats, daily);
    }

    private StockoutMetricResponse buildStockout(UUID companyId, Window w) {
        long tracked     = productRepository.countTrackedProducts(companyId);
        long outOfStock  = productRepository.countOutOfStock(companyId);
        long totalOrders = orderRepository.countOrdersInWindow(companyId, w.from, w.to);
        long backordered = orderRepository.countOrdersWithBackorderedItemsInWindow(companyId, w.from, w.to);

        double oosRate = tracked > 0 ? (double) outOfStock / tracked : 0.0;
        double boRate  = totalOrders > 0 ? (double) backordered / totalOrders : 0.0;

        return new StockoutMetricResponse(tracked, outOfStock, oosRate, totalOrders, backordered, boRate);
    }

    private SupplierLatenessMetricResponse buildSupplierLateness(UUID companyId, Window w) {
        SupplierLatenessProjection stats = metricsRepository.supplierLatenessStats(companyId, w.from, w.to);
        List<DailyCountProjection> daily = metricsRepository.supplierLatenessDaily(companyId, w.from, w.to);

        long total = stats != null && stats.getTotal() != null ? stats.getTotal() : 0L;
        long late  = stats != null && stats.getLate()  != null ? stats.getLate()  : 0L;
        Double avgLateDays = stats != null ? stats.getAvgLateDays() : null;
        double rate = total > 0 ? (double) late / total : 0.0;

        return new SupplierLatenessMetricResponse(total, late, rate, avgLateDays, toDailyPoints(daily));
    }

    private CancellationMetricResponse buildCancellation(UUID companyId, Window w) {
        List<CancellationReasonCountProjection> rows = metricsRepository.cancellationsByReason(companyId, w.from, w.to);
        List<CancellationMetricResponse.ReasonCount> byReason = new ArrayList<>();
        long total = 0;
        for (CancellationReasonCountProjection r : rows) {
            long c = r.getCount() != null ? r.getCount() : 0L;
            byReason.add(new CancellationMetricResponse.ReasonCount(r.getReason(), c));
            total += c;
        }
        List<DailyCountProjection> daily = metricsRepository.cancellationsDaily(companyId, w.from, w.to);
        return new CancellationMetricResponse(total, byReason, toDailyPoints(daily));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DurationMetricResponse cachedDuration(UUID companyId, String name, int lookbackDays,
                                                  java.util.function.BiFunction<UUID, Window, DurationMetricResponse> builder) {
        int days = clampLookback(lookbackDays);
        Window w = window(days);
        String key = cacheKey(companyId, name, days);
        DurationMetricResponse cached = readCache(key, DurationMetricResponse.class);
        if (cached != null) return cached;

        DurationMetricResponse out = builder.apply(companyId, w);
        writeCache(key, out);
        return out;
    }

    private DurationMetricResponse toDurationResponse(DurationStatsProjection stats,
                                                      List<DailyDurationProjection> daily) {
        long count = stats != null && stats.getCount() != null ? stats.getCount() : 0L;
        Double avgSec = stats != null ? stats.getAvgSeconds() : null;
        Double avgHours = avgSec != null ? avgSec / SECONDS_PER_HOUR : null;

        List<DailyPoint> points = new ArrayList<>(daily.size());
        for (DailyDurationProjection d : daily) {
            Double h = d.getAvgSeconds() != null ? d.getAvgSeconds() / SECONDS_PER_HOUR : null;
            points.add(new DailyPoint(d.getDay(), d.getCount() != null ? d.getCount() : 0L, h));
        }
        return new DurationMetricResponse(count, avgHours, points);
    }

    private List<DailyPoint> toDailyPoints(List<DailyCountProjection> rows) {
        List<DailyPoint> out = new ArrayList<>(rows.size());
        for (DailyCountProjection r : rows) {
            out.add(new DailyPoint(r.getDay(), r.getCount() != null ? r.getCount() : 0L, null));
        }
        return out;
    }

    int clampLookback(int lookbackDays) {
        if (lookbackDays < MIN_LOOKBACK_DAYS) return MIN_LOOKBACK_DAYS;
        if (lookbackDays > MAX_LOOKBACK_DAYS) return MAX_LOOKBACK_DAYS;
        return lookbackDays;
    }

    private Window window(int days) {
        Instant now = Instant.now();
        return new Window(now.minus(days, ChronoUnit.DAYS), now);
    }

    private String cacheKey(UUID companyId, String metric, int days) {
        return CACHE_PREFIX + companyId + ":" + metric + ":" + days;
    }

    private <T> T readCache(String key, Class<T> type) {
        String raw = cacheService.get(key);
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("[OPS] Cache deserialise error for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, Object value) {
        try {
            cacheService.set(key, objectMapper.writeValueAsString(value), CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[OPS] Cache serialise error for key {}: {}", key, e.getMessage());
        }
    }

    private void verifyOwnership(UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_ANALYTICS);
    }

    private record Window(Instant from, Instant to) {}
}
