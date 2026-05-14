package backend.services.impl.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.responses.forecasting.ForecastSummaryResponse;
import backend.dtos.responses.forecasting.ProductForecastResponse;
import backend.dtos.responses.forecasting.ReorderSuggestionResponse;
import backend.dtos.responses.forecasting.ReorderSuggestionResponse.ReorderReasonCode;
import backend.dtos.responses.forecasting.SeasonalPrepResponse;
import backend.dtos.responses.forecasting.SeasonalPrepResponse.Trend;
import backend.dtos.responses.forecasting.SeasonalPrepSummaryResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Product;
import backend.models.enums.CompanyCapability;
import backend.repositories.ProductRepository;
import backend.repositories.projections.DailyDemandProjection;
import backend.services.intf.CacheService;
import backend.services.intf.analytics.ForecastingService;
import backend.services.intf.company.CompanyAccessService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ForecastingServiceImpl implements ForecastingService {

    private static final Logger log = LoggerFactory.getLogger(ForecastingServiceImpl.class);

    private static final String CACHE_COMPANY_PREFIX = "forecast:company:";
    private static final String CACHE_REORDER_PREFIX = "forecast:reorder:";
    private static final int    STOCKOUT_HORIZON_DAYS = 28;
    private static final int    YOY_WINDOW_DAYS       = 28;

    private final CompanyAccessService companyAccessService;
    private final ProductRepository productRepository;
    private final CacheService      cacheService;
    private final ObjectMapper      objectMapper;

    @Value("${app.forecast.lead-time-days:7}")
    private int leadTimeDays;

    @Value("${app.forecast.safety-days:3}")
    private int safetyDays;

    @Value("${app.forecast.review-days:7}")
    private int reviewDays;

    @Value("${app.forecast.cache-ttl-seconds:600}")
    private int cacheTtlSeconds;

    public ForecastingServiceImpl(
            CompanyAccessService companyAccessService,
            ProductRepository productRepository,
            CacheService cacheService,
            ObjectMapper objectMapper) {
        this.companyAccessService = companyAccessService;
        this.productRepository = productRepository;
        this.cacheService      = cacheService;
        this.objectMapper      = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ForecastSummaryResponse getCompanyForecast(long companyId, long ownerId, int lookbackDays, int limit) {
        verifyOwnership(companyId, ownerId);

        String cacheKey = CACHE_COMPANY_PREFIX + companyId + ":" + lookbackDays;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            try {
                ForecastSummaryResponse full = objectMapper.readValue(cached, ForecastSummaryResponse.class);
                return sliceCompanyForecast(full, limit);
            } catch (Exception e) {
                log.warn("[FORECAST] Cache deserialise error for key {}: {}", cacheKey, e.getMessage());
            }
        }

        return computeAndCacheCompanyForecast(companyId, lookbackDays, limit, cacheKey);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductForecastResponse getProductForecast(long companyId, long productId, long ownerId, int lookbackDays) {
        verifyOwnership(companyId, ownerId);
        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Instant since = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        List<DailyDemandProjection> rows = productRepository.findDailyDemandSince(companyId, since);

        Map<Long, List<DailyDemandProjection>> byProduct = groupByProduct(rows);
        List<DailyDemandProjection> productRows = byProduct.getOrDefault(productId, List.of());

        LocalDate seriesStart = since.atZone(ZoneOffset.UTC).toLocalDate();
        return buildProductForecast(product, productRows, seriesStart, lookbackDays);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReorderSuggestionResponse> getReorderSuggestions(
            long companyId, long ownerId, int lookbackDays, int limit) {
        verifyOwnership(companyId, ownerId);

        String cacheKey = CACHE_REORDER_PREFIX + companyId + ":" + lookbackDays;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            try {
                @SuppressWarnings("unchecked")
                List<ReorderSuggestionResponse> full = objectMapper.readValue(cached, List.class);
                // Re-deserialise properly via ForecastSummaryResponse — use company cache instead
            } catch (Exception ignored) {}
        }

        // Derive from the company forecast (shares the cache)
        ForecastSummaryResponse summary = getCompanyForecast(companyId, ownerId, lookbackDays, Integer.MAX_VALUE);
        return summary.items().stream()
                .filter(f -> f.reorderSuggestedQty() > 0)
                .sorted(Comparator.comparing(ProductForecastResponse::daysOfCoverage,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toReorderSuggestion)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SeasonalPrepSummaryResponse getSeasonalPrep(long companyId, long ownerId, int limit) {
        verifyOwnership(companyId, ownerId);

        Instant now           = Instant.now();
        Instant last28Start   = now.minus(YOY_WINDOW_DAYS, ChronoUnit.DAYS);
        Instant yoyStart      = last28Start.minus(365, ChronoUnit.DAYS);
        Instant yoyEnd        = now.minus(365, ChronoUnit.DAYS);

        // Require at least one year of data: if the earliest PAID order is within 365 days
        // we cannot produce a meaningful YoY comparison.
        List<DailyDemandProjection> yoyRows = productRepository.findDailyDemandBetween(companyId, yoyStart, yoyEnd);
        if (yoyRows.isEmpty()) {
            return SeasonalPrepSummaryResponse.insufficientHistory();
        }

        List<DailyDemandProjection> recentRows = productRepository.findDailyDemandBetween(companyId, last28Start, now);

        Map<Long, List<DailyDemandProjection>> recentByProduct = groupByProduct(recentRows);
        Map<Long, List<DailyDemandProjection>> yoyByProduct    = groupByProduct(yoyRows);

        List<Product> products = productRepository.findAllByCompanyId(companyId);

        List<SeasonalPrepResponse> results = new ArrayList<>();
        for (Product p : products) {
            if (p.getStock() == null) continue;

            double avgRecent = avgDaily(recentByProduct.getOrDefault(p.getId(), List.of()), YOY_WINDOW_DAYS);
            double avgYoY    = avgDaily(yoyByProduct.getOrDefault(p.getId(), List.of()), YOY_WINDOW_DAYS);

            if (avgRecent == 0 && avgYoY == 0) continue;

            double yoyRatio = (avgYoY > 0) ? (avgRecent / avgYoY) : Double.MAX_VALUE;
            Trend trend;
            if (yoyRatio >= 1.5)      trend = Trend.RAMPING_UP;
            else if (yoyRatio <= 0.5) trend = Trend.COOLING_DOWN;
            else                      trend = Trend.STABLE;

            results.add(new SeasonalPrepResponse(
                    p.getId(), p.getName(), p.getSku(), p.getStock(),
                    avgRecent, avgYoY, yoyRatio, trend));
        }

        results.sort(Comparator.comparingDouble(SeasonalPrepResponse::yoyRatio).reversed());

        List<SeasonalPrepResponse> items = results.stream().limit(limit).collect(Collectors.toList());
        return SeasonalPrepSummaryResponse.of(items);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private ForecastSummaryResponse computeAndCacheCompanyForecast(
            long companyId, int lookbackDays, int limit, String cacheKey) {
        Instant now   = Instant.now();
        Instant since = now.minus(lookbackDays, ChronoUnit.DAYS);

        List<DailyDemandProjection> rows = productRepository.findDailyDemandSince(companyId, since);
        Map<Long, List<DailyDemandProjection>> byProduct = groupByProduct(rows);

        List<Product> products = productRepository.findAllByCompanyId(companyId);
        LocalDate seriesStart  = since.atZone(ZoneOffset.UTC).toLocalDate();

        List<ProductForecastResponse> items = new ArrayList<>(products.size());
        for (Product p : products) {
            List<DailyDemandProjection> productRows = byProduct.getOrDefault(p.getId(), List.of());
            items.add(buildProductForecast(p, productRows, seriesStart, lookbackDays));
        }

        // Sort by urgency: urgent reorders first, then by days of coverage ascending
        items.sort(Comparator
                .comparing(ProductForecastResponse::reorderUrgent).reversed()
                .thenComparingDouble(f -> f.daysOfCoverage() == null ? Double.MAX_VALUE : f.daysOfCoverage()));

        int needReorder  = (int) items.stream().filter(ProductForecastResponse::reorderUrgent).count();
        int imminentSO   = (int) items.stream()
                .filter(f -> f.likelyStockoutDate() != null &&
                        !LocalDate.now(ZoneOffset.UTC).plusDays(leadTimeDays).isBefore(f.likelyStockoutDate()))
                .count();

        ForecastSummaryResponse full = new ForecastSummaryResponse(
                companyId, lookbackDays, now, items.size(), needReorder, imminentSO, items);

        try {
            cacheService.set(cacheKey, objectMapper.writeValueAsString(full), cacheTtlSeconds);
        } catch (Exception e) {
            log.warn("[FORECAST] Cache write error for key {}: {}", cacheKey, e.getMessage());
        }

        return sliceCompanyForecast(full, limit);
    }

    private ProductForecastResponse buildProductForecast(
            Product product, List<DailyDemandProjection> productRows,
            LocalDate seriesStart, int lookbackDays) {
        long[] series = buildSeries(productRows, seriesStart, lookbackDays);

        double mu      = ForecastMath.mean(series);
        double sigma   = ForecastMath.stddev(series);
        double[] sf    = ForecastMath.computeSeasonality(series, seriesStart);
        double[] weekly = ForecastMath.predictedWeekly(mu, sigma, sf, LocalDate.now(ZoneOffset.UTC));

        int     stock       = product.getStock() != null ? product.getStock() : 0;
        Double  coverage    = mu > 0 ? stock / mu : null;
        LocalDate stockout  = ForecastMath.projectStockout(
                stock, mu, sf, LocalDate.now(ZoneOffset.UTC).plusDays(1), STOCKOUT_HORIZON_DAYS);

        int reorderQty = 0;
        boolean urgent = false;
        if (product.getStock() != null) {
            reorderQty = ForecastMath.computeReorderQty(
                    mu, stock, leadTimeDays, safetyDays, reviewDays, product.getAutoRestockQty());
            urgent = reorderQty > 0 && (coverage == null || coverage < leadTimeDays + safetyDays);

            // Also flag if stock is below the configured low-stock threshold
            if (!urgent && product.getLowStockThreshold() != null && stock <= product.getLowStockThreshold()) {
                urgent = true;
            }
        }

        return new ProductForecastResponse(
                product.getId(), product.getName(), product.getSku(),
                product.getStock(),
                mu, weekly[0], weekly[1], weekly[2],
                coverage, stockout,
                reorderQty, urgent, sf);
    }

    private ReorderSuggestionResponse toReorderSuggestion(ProductForecastResponse f) {
        ReorderReasonCode reason;
        if (f.likelyStockoutDate() != null &&
                !LocalDate.now(ZoneOffset.UTC).plusDays(leadTimeDays).isBefore(f.likelyStockoutDate())) {
            reason = ReorderReasonCode.STOCKOUT_WITHIN_LEADTIME;
        } else if (f.daysOfCoverage() != null && f.daysOfCoverage() < leadTimeDays + safetyDays) {
            reason = ReorderReasonCode.BELOW_THRESHOLD;
        } else {
            reason = ReorderReasonCode.VELOCITY_SPIKE;
        }
        return new ReorderSuggestionResponse(
                f.productId(), f.productName(), f.sku(), f.currentStock(),
                f.reorderSuggestedQty(), reason, f.likelyStockoutDate());
    }

    /** Builds a zero-filled daily demand array of length lookbackDays. */
    private long[] buildSeries(List<DailyDemandProjection> rows, LocalDate seriesStart, int lookbackDays) {
        long[] series = new long[lookbackDays];
        for (DailyDemandProjection row : rows) {
            LocalDate day = row.getDay();
            int index = (int) seriesStart.until(day, java.time.temporal.ChronoUnit.DAYS);
            if (index >= 0 && index < lookbackDays) {
                series[index] = row.getUnits() != null ? row.getUnits() : 0L;
            }
        }
        return series;
    }

    private Map<Long, List<DailyDemandProjection>> groupByProduct(List<DailyDemandProjection> rows) {
        Map<Long, List<DailyDemandProjection>> map = new HashMap<>();
        for (DailyDemandProjection row : rows) {
            map.computeIfAbsent(row.getProductId(), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private double avgDaily(List<DailyDemandProjection> rows, int windowDays) {
        if (rows.isEmpty()) return 0;
        long total = rows.stream().mapToLong(r -> r.getUnits() != null ? r.getUnits() : 0L).sum();
        return (double) total / windowDays;
    }

    private ForecastSummaryResponse sliceCompanyForecast(ForecastSummaryResponse full, int limit) {
        if (limit >= full.items().size()) return full;
        return new ForecastSummaryResponse(
                full.companyId(), full.windowDays(), full.computedAt(),
                full.productCount(), full.productsNeedingReorder(), full.productsWithImminentStockout(),
                full.items().stream().limit(limit).collect(Collectors.toList()));
    }

    private void verifyOwnership(long companyId, long ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_ANALYTICS);
    }
}
