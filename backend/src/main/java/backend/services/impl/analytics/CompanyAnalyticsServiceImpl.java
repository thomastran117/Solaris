package backend.services.impl.analytics;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.responses.analytics.CategorySalesResponse;
import backend.dtos.responses.analytics.CategorySalesResponse.CategoryEntry;
import backend.dtos.responses.analytics.CompanyRevenueSummaryResponse;
import backend.dtos.responses.analytics.CompanyRevenueSummaryResponse.DailyRevPoint;
import backend.dtos.responses.analytics.ProductPerformanceResponse;
import backend.dtos.responses.analytics.ProductPerformanceResponse.ProductPerfEntry;
import backend.dtos.responses.analytics.SlowMoversResponse;
import backend.dtos.responses.analytics.SlowMoversResponse.SlowMoverEntry;
import backend.models.enums.CompanyCapability;
import backend.repositories.CompanyAnalyticsRepository;
import backend.repositories.ProductRepository;
import backend.repositories.projections.CategorySalesProjection;
import backend.repositories.projections.DailyRevProjection;
import backend.repositories.projections.ProductSalesProjection;
import backend.repositories.projections.SlowMoverProjection;
import backend.services.intf.CacheService;
import backend.services.intf.analytics.CompanyAnalyticsService;
import backend.services.intf.company.CompanyAccessService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompanyAnalyticsServiceImpl implements CompanyAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(CompanyAnalyticsServiceImpl.class);

    private static final String KEY_REVENUE   = "analytics:revenue-summary:";   // + companyId:days
    private static final String KEY_CATEGORY  = "analytics:category-sales:";    // + companyId:days
    private static final String KEY_SLOW      = "analytics:slow-movers:";        // + companyId
    private static final String KEY_PERF      = "analytics:product-perf:";       // + companyId:days

    private static final int  SLOW_MOVER_DAYS      = 90;
    private static final double SLOW_MOVER_VELOCITY = 0.5;
    private static final int  SLOW_MOVER_LIMIT      = 50;
    private static final int  PERF_TOP_N            = 20;

    @Value("${app.analytics.cache-ttl-seconds:7200}")
    private long cacheTtlSeconds;

    private final ProductRepository         productRepository;
    private final CompanyAnalyticsRepository analyticsRepository;
    private final CacheService              cacheService;
    private final ObjectMapper              objectMapper;
    private final CompanyAccessService      companyAccessService;

    public CompanyAnalyticsServiceImpl(
            ProductRepository productRepository,
            CompanyAnalyticsRepository analyticsRepository,
            CacheService cacheService,
            ObjectMapper objectMapper,
            CompanyAccessService companyAccessService) {
        this.productRepository   = productRepository;
        this.analyticsRepository = analyticsRepository;
        this.cacheService        = cacheService;
        this.objectMapper        = objectMapper;
        this.companyAccessService = companyAccessService;
    }

    // -------------------------------------------------------------------------
    // API-facing methods (ownership checked)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CompanyRevenueSummaryResponse getRevenueSummary(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwner(companyId, ownerId);
        String key = KEY_REVENUE + companyId + ":" + lookbackDays;
        CompanyRevenueSummaryResponse cached = fromCache(key, CompanyRevenueSummaryResponse.class);
        if (cached != null) return cached;
        return computeAndCacheRevenue(companyId, lookbackDays);
    }

    @Override
    @Transactional(readOnly = true)
    public CategorySalesResponse getCategorySales(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwner(companyId, ownerId);
        String key = KEY_CATEGORY + companyId + ":" + lookbackDays;
        CategorySalesResponse cached = fromCache(key, CategorySalesResponse.class);
        if (cached != null) return cached;
        return computeAndCacheCategories(companyId, lookbackDays);
    }

    @Override
    @Transactional(readOnly = true)
    public SlowMoversResponse getSlowMovers(UUID companyId, UUID ownerId, int days) {
        verifyOwner(companyId, ownerId);
        String key = KEY_SLOW + companyId;
        SlowMoversResponse cached = fromCache(key, SlowMoversResponse.class);
        if (cached != null) return cached;
        return computeAndCacheSlowMovers(companyId, days);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPerformanceResponse getProductPerformance(UUID companyId, UUID ownerId, int lookbackDays) {
        verifyOwner(companyId, ownerId);
        String key = KEY_PERF + companyId + ":" + lookbackDays;
        ProductPerformanceResponse cached = fromCache(key, ProductPerformanceResponse.class);
        if (cached != null) return cached;
        return computeAndCachePerformance(companyId, lookbackDays);
    }

    // -------------------------------------------------------------------------
    // Worker-facing method (no ownership check)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public void precomputeAll(UUID companyId) {
        computeAndCacheRevenue(companyId, 30);
        computeAndCacheCategories(companyId, 30);
        computeAndCacheSlowMovers(companyId, SLOW_MOVER_DAYS);
        computeAndCachePerformance(companyId, 30);
    }

    // -------------------------------------------------------------------------
    // Compute methods
    // -------------------------------------------------------------------------

    private CompanyRevenueSummaryResponse computeAndCacheRevenue(UUID companyId, int lookbackDays) {
        Instant to   = Instant.now();
        Instant from = to.minus(lookbackDays, ChronoUnit.DAYS);

        List<DailyRevProjection> rows = analyticsRepository.getDailyRevenue(companyId, from, to);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalOrders = 0;
        List<DailyRevPoint> daily = new ArrayList<>(rows.size());

        for (DailyRevProjection r : rows) {
            BigDecimal rev = r.getTotalRevenue() != null ? r.getTotalRevenue() : BigDecimal.ZERO;
            long orders    = r.getOrderCount()   != null ? r.getOrderCount()   : 0L;
            long units     = r.getTotalUnits()   != null ? r.getTotalUnits()   : 0L;
            totalRevenue   = totalRevenue.add(rev);
            totalOrders   += orders;
            daily.add(new DailyRevPoint(r.getDay(), rev, units, orders));
        }

        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        CompanyRevenueSummaryResponse response =
                new CompanyRevenueSummaryResponse(companyId, lookbackDays, from, to,
                        totalRevenue, totalOrders, avgOrderValue, daily);

        toCache(KEY_REVENUE + companyId + ":" + lookbackDays, response);
        return response;
    }

    private CategorySalesResponse computeAndCacheCategories(UUID companyId, int lookbackDays) {
        Instant to   = Instant.now();
        Instant from = to.minus(lookbackDays, ChronoUnit.DAYS);

        List<CategorySalesProjection> rows = analyticsRepository.getCategorySales(companyId, from, to);

        BigDecimal grandTotal = rows.stream()
                .map(r -> r.getTotalRevenue() != null ? r.getTotalRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryEntry> entries = new ArrayList<>(rows.size());
        for (CategorySalesProjection r : rows) {
            BigDecimal rev   = r.getTotalRevenue() != null ? r.getTotalRevenue() : BigDecimal.ZERO;
            long units       = r.getTotalUnits()   != null ? r.getTotalUnits()   : 0L;
            long orders      = r.getOrderCount()   != null ? r.getOrderCount()   : 0L;
            double share = grandTotal.compareTo(BigDecimal.ZERO) > 0
                    ? rev.divide(grandTotal, 4, RoundingMode.HALF_UP)
                           .multiply(BigDecimal.valueOf(100))
                           .doubleValue()
                    : 0.0;
            entries.add(new CategoryEntry(r.getCategory(), rev, units, (int) orders, share));
        }

        CategorySalesResponse response = new CategorySalesResponse(companyId, lookbackDays, entries);
        toCache(KEY_CATEGORY + companyId + ":" + lookbackDays, response);
        return response;
    }

    private SlowMoversResponse computeAndCacheSlowMovers(UUID companyId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<SlowMoverProjection> rows =
                analyticsRepository.getSlowMovers(companyId, since, days, SLOW_MOVER_VELOCITY, SLOW_MOVER_LIMIT);

        List<SlowMoverEntry> entries = new ArrayList<>(rows.size());
        for (SlowMoverProjection r : rows) {
            long units = r.getTotalUnitsSold() != null ? r.getTotalUnitsSold() : 0L;
            BigDecimal rev = r.getTotalRevenue() != null ? r.getTotalRevenue() : BigDecimal.ZERO;
            double velocity = days > 0 ? (double) units / days : 0.0;
            entries.add(new SlowMoverEntry(
                    bytesToUuid(r.getProductId()), r.getProductName(), r.getSku(),
                    r.getCurrentStock(), r.getPrice(), r.getCurrency(),
                    units, rev, velocity, units == 0));
        }

        SlowMoversResponse response = new SlowMoversResponse(companyId, days, entries);
        toCache(KEY_SLOW + companyId, response);
        return response;
    }

    private ProductPerformanceResponse computeAndCachePerformance(UUID companyId, int lookbackDays) {
        Instant now       = Instant.now();
        Instant from      = now.minus(lookbackDays, ChronoUnit.DAYS);
        Instant priorFrom = from.minus(lookbackDays, ChronoUnit.DAYS);

        List<ProductSalesProjection> current = productRepository.findTopByRevenue(companyId, PERF_TOP_N, from, now);
        List<ProductSalesProjection> prior   = productRepository.findTopByRevenue(companyId, PERF_TOP_N, priorFrom, from);

        Map<UUID, ProductSalesProjection> priorMap = new HashMap<>();
        for (ProductSalesProjection p : prior) {
            priorMap.put(bytesToUuid(p.getProductId()), p);
        }

        // Sort current by revenue descending for ranking
        List<ProductSalesProjection> sorted = new ArrayList<>(current);
        sorted.sort(Comparator.comparing(
                p -> p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO,
                Comparator.reverseOrder()));

        // Build units rank separately
        List<ProductSalesProjection> byUnits = new ArrayList<>(current);
        byUnits.sort(Comparator.comparingLong(
                p -> p.getTotalUnitsSold() != null ? -p.getTotalUnitsSold() : 0L));

        Map<UUID, Integer> unitsRankMap = new HashMap<>();
        for (int i = 0; i < byUnits.size(); i++) {
            unitsRankMap.put(bytesToUuid(byUnits.get(i).getProductId()), i + 1);
        }

        List<ProductPerfEntry> entries = new ArrayList<>(sorted.size());
        int revenueRank = 1;
        for (ProductSalesProjection p : sorted) {
            BigDecimal curRev   = p.getTotalRevenue()   != null ? p.getTotalRevenue()   : BigDecimal.ZERO;
            long curUnits       = p.getTotalUnitsSold() != null ? p.getTotalUnitsSold() : 0L;

            ProductSalesProjection priorP = priorMap.get(bytesToUuid(p.getProductId()));
            BigDecimal priorRev   = priorP != null && priorP.getTotalRevenue()   != null ? priorP.getTotalRevenue()   : BigDecimal.ZERO;
            long priorUnits       = priorP != null && priorP.getTotalUnitsSold() != null ? priorP.getTotalUnitsSold() : 0L;

            double revGrowth   = priorRev.compareTo(BigDecimal.ZERO) > 0
                    ? curRev.subtract(priorRev)
                             .divide(priorRev, 4, RoundingMode.HALF_UP)
                             .multiply(BigDecimal.valueOf(100))
                             .doubleValue()
                    : (curRev.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0);

            double unitsGrowth = priorUnits > 0
                    ? ((double)(curUnits - priorUnits) / priorUnits) * 100.0
                    : (curUnits > 0 ? 100.0 : 0.0);

            entries.add(new ProductPerfEntry(
                    bytesToUuid(p.getProductId()), p.getProductName(), p.getSku(),
                    curRev, curUnits, priorRev, priorUnits,
                    revGrowth, unitsGrowth,
                    revenueRank++,
                    unitsRankMap.getOrDefault(bytesToUuid(p.getProductId()), 0)));
        }

        ProductPerformanceResponse response =
                new ProductPerformanceResponse(companyId, lookbackDays, now, entries);
        toCache(KEY_PERF + companyId + ":" + lookbackDays, response);
        return response;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void verifyOwner(UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_ANALYTICS);
    }

    private <T> T fromCache(String key, Class<T> type) {
        try {
            String raw = cacheService.get(key);
            if (raw != null) return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("[ANALYTICS] Cache read error for key {}: {}", key, e.getMessage());
        }
        return null;
    }

    private void toCache(String key, Object value) {
        try {
            cacheService.set(key, objectMapper.writeValueAsString(value), cacheTtlSeconds);
        } catch (Exception e) {
            log.warn("[ANALYTICS] Cache write error for key {}: {}", key, e.getMessage());
        }
    }

    private static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
