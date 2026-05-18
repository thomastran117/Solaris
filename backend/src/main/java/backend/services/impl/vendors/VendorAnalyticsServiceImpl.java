package backend.services.impl.vendors;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.responses.marketplace.MarketplaceAnalyticsSummaryResponse;
import backend.dtos.responses.marketplace.TopVendorResponse;
import backend.dtos.responses.operations.DailyPoint;
import backend.dtos.responses.vendor.VendorAnalyticsSummaryResponse;
import backend.dtos.responses.vendor.VendorOrdersMetricResponse;
import backend.dtos.responses.vendor.VendorPayoutsMetricResponse;
import backend.dtos.responses.vendor.VendorRefundsMetricResponse;
import backend.dtos.responses.vendor.VendorRevenueResponse;
import backend.dtos.responses.vendor.VendorTopProductsResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.MarketplaceVendor;
import backend.models.enums.PayoutStatus;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorAnalyticsRepository;
import backend.repositories.VendorPayoutRepository;
import backend.repositories.projections.DailyCountProjection;
import backend.repositories.projections.MarketplaceSummaryProjection;
import backend.repositories.projections.TopVendorProjection;
import backend.repositories.projections.VendorRevenueDailyProjection;
import backend.repositories.projections.VendorRevenueSummaryProjection;
import backend.repositories.projections.VendorShipHoursProjection;
import backend.repositories.projections.VendorTopProductProjection;
import backend.services.intf.CacheService;
import backend.services.intf.vendors.VendorAnalyticsService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class VendorAnalyticsServiceImpl implements VendorAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(VendorAnalyticsServiceImpl.class);

    private static final int MIN_LOOKBACK_DAYS = 7;
    private static final int MAX_LOOKBACK_DAYS = 365;
    private static final long CACHE_TTL_SECONDS = 15 * 60;
    private static final String CACHE_PREFIX = "vendor-analytics:";
    private static final double DEFAULT_TARGET_SHIP_HOURS = 48.0;

    private final VendorAnalyticsRepository analyticsRepository;
    private final VendorPayoutRepository payoutRepository;
    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final MarketplaceVendorRepository marketplaceVendorRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public VendorAnalyticsServiceImpl(
            VendorAnalyticsRepository analyticsRepository,
            VendorPayoutRepository payoutRepository,
            MarketplaceProfileRepository marketplaceProfileRepository,
            MarketplaceVendorRepository marketplaceVendorRepository,
            CacheService cacheService,
            ObjectMapper objectMapper) {
        this.analyticsRepository = analyticsRepository;
        this.payoutRepository = payoutRepository;
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Vendor-scoped analytics
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public VendorAnalyticsSummaryResponse getSummary(UUID vendorId, UUID marketplaceId, int lookbackDays, UUID actorUserId) {
        assertVendorAccess(vendorId, marketplaceId, actorUserId);
        int days = clamp(lookbackDays);
        Window w = window(days);
        String key = cacheKey(vendorId, "summary", days);

        VendorAnalyticsSummaryResponse cached = readCache(key, VendorAnalyticsSummaryResponse.class);
        if (cached != null) return cached;

        VendorRevenueSummaryProjection rev = analyticsRepository.vendorRevenueSummary(vendorId, marketplaceId, w.from, w.to);
        long total = analyticsRepository.vendorTotalOrders(vendorId, marketplaceId, w.from, w.to);
        long cancelled = analyticsRepository.vendorCancelledCount(vendorId, marketplaceId, w.from, w.to);
        long returned = analyticsRepository.vendorReturnedCount(vendorId, marketplaceId, w.from, w.to);
        double targetHours = resolveTargetShipHours(marketplaceId);
        VendorShipHoursProjection ship = analyticsRepository.vendorShipHours(vendorId, marketplaceId, w.from, w.to, targetHours);

        double cancellationRate = total > 0 ? (double) cancelled / total : 0.0;
        double refundRate = total > 0 ? (double) returned / total : 0.0;
        long shipped = ship != null && ship.getTotalShipped() != null ? ship.getTotalShipped() : 0;
        long late = ship != null && ship.getTotalLate() != null ? ship.getTotalLate() : 0;
        double lateShipmentRate = shipped > 0 ? (double) late / shipped : 0.0;
        Double avgShipHours = ship != null ? ship.getAvgShipHours() : null;

        VendorAnalyticsSummaryResponse out = new VendorAnalyticsSummaryResponse(
                vendorId, marketplaceId, days, w.from, w.to,
                total,
                nullSafe(rev.getTotalGross()),
                nullSafe(rev.getTotalCommission()),
                nullSafe(rev.getTotalNet()),
                nullSafe(rev.getAvgOrderValue()),
                cancellationRate, refundRate, lateShipmentRate, avgShipHours);
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public VendorRevenueResponse getRevenue(UUID vendorId, UUID marketplaceId, int lookbackDays, UUID actorUserId) {
        assertVendorAccess(vendorId, marketplaceId, actorUserId);
        int days = clamp(lookbackDays);
        Window w = window(days);
        String key = cacheKey(vendorId, "revenue", days);

        VendorRevenueResponse cached = readCache(key, VendorRevenueResponse.class);
        if (cached != null) return cached;

        VendorRevenueSummaryProjection summary = analyticsRepository.vendorRevenueSummary(vendorId, marketplaceId, w.from, w.to);
        List<VendorRevenueDailyProjection> daily = analyticsRepository.vendorRevenueDaily(vendorId, marketplaceId, w.from, w.to);

        List<VendorRevenueResponse.DailyRevenuePoint> points = new ArrayList<>(daily.size());
        for (VendorRevenueDailyProjection d : daily) {
            points.add(new VendorRevenueResponse.DailyRevenuePoint(
                    d.getDay(),
                    nullSafe(d.getGross()),
                    nullSafe(d.getCommission()),
                    nullSafe(d.getNet()),
                    d.getOrderCount() != null ? d.getOrderCount() : 0L));
        }

        VendorRevenueResponse out = new VendorRevenueResponse(
                vendorId, days, w.from, w.to,
                nullSafe(summary.getTotalGross()),
                nullSafe(summary.getTotalCommission()),
                nullSafe(summary.getTotalNet()),
                points);
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public VendorTopProductsResponse getTopProducts(UUID vendorId, UUID marketplaceId, int lookbackDays, int limit, UUID actorUserId) {
        assertVendorAccess(vendorId, marketplaceId, actorUserId);
        int days = clamp(lookbackDays);
        Window w = window(days);
        int cap = Math.min(limit, 50);
        String key = cacheKey(vendorId, "top-products:" + cap, days);

        VendorTopProductsResponse cached = readCache(key, VendorTopProductsResponse.class);
        if (cached != null) return cached;

        List<VendorTopProductProjection> rows = analyticsRepository.vendorTopProducts(vendorId, marketplaceId, w.from, w.to, cap);
        List<VendorTopProductsResponse.ProductEntry> products = new ArrayList<>(rows.size());
        for (VendorTopProductProjection r : rows) {
            products.add(new VendorTopProductsResponse.ProductEntry(
                    r.getProductId(),
                    r.getProductName(),
                    r.getTotalUnitsSold() != null ? r.getTotalUnitsSold() : 0L,
                    nullSafe(r.getTotalRevenue())));
        }

        VendorTopProductsResponse out = new VendorTopProductsResponse(vendorId, days, products);
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public VendorOrdersMetricResponse getOrders(UUID vendorId, UUID marketplaceId, int lookbackDays, UUID actorUserId) {
        assertVendorAccess(vendorId, marketplaceId, actorUserId);
        int days = clamp(lookbackDays);
        Window w = window(days);
        String key = cacheKey(vendorId, "orders", days);

        VendorOrdersMetricResponse cached = readCache(key, VendorOrdersMetricResponse.class);
        if (cached != null) return cached;

        long total = analyticsRepository.vendorTotalOrders(vendorId, marketplaceId, w.from, w.to);
        long cancelled = analyticsRepository.vendorCancelledCount(vendorId, marketplaceId, w.from, w.to);
        double rate = total > 0 ? (double) cancelled / total : 0.0;
        List<DailyCountProjection> daily = analyticsRepository.vendorOrdersDaily(vendorId, marketplaceId, w.from, w.to);

        VendorOrdersMetricResponse out = new VendorOrdersMetricResponse(
                total, cancelled, rate, toDailyPoints(daily));
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public VendorRefundsMetricResponse getRefunds(UUID vendorId, UUID marketplaceId, int lookbackDays, UUID actorUserId) {
        assertVendorAccess(vendorId, marketplaceId, actorUserId);
        int days = clamp(lookbackDays);
        Window w = window(days);
        String key = cacheKey(vendorId, "refunds", days);

        VendorRefundsMetricResponse cached = readCache(key, VendorRefundsMetricResponse.class);
        if (cached != null) return cached;

        long total = analyticsRepository.vendorTotalOrders(vendorId, marketplaceId, w.from, w.to);
        long returned = analyticsRepository.vendorReturnedCount(vendorId, marketplaceId, w.from, w.to);
        double rate = total > 0 ? (double) returned / total : 0.0;
        List<DailyCountProjection> daily = analyticsRepository.vendorRefundsDaily(vendorId, marketplaceId, w.from, w.to);

        VendorRefundsMetricResponse out = new VendorRefundsMetricResponse(returned, total, rate, toDailyPoints(daily));
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public VendorPayoutsMetricResponse getPayouts(UUID vendorId, UUID marketplaceId, int recentCount, UUID actorUserId) {
        assertVendorAccess(vendorId, marketplaceId, actorUserId);
        int cap = Math.min(recentCount, 50);
        var page = payoutRepository.findByVendorIdAndStatus(
                vendorId, PayoutStatus.PAID,
                PageRequest.of(0, cap, Sort.by(Sort.Direction.DESC, "paidAt")));

        BigDecimal totalPaidOut = BigDecimal.ZERO;
        List<VendorPayoutsMetricResponse.PayoutSummary> recent = new ArrayList<>();
        for (var p : page.getContent()) {
            totalPaidOut = totalPaidOut.add(p.getNetAmount());
            recent.add(new VendorPayoutsMetricResponse.PayoutSummary(
                    p.getId(), p.getNetAmount(), p.getCurrency(),
                    p.getStatus().name(), p.getPaidAt()));
        }

        return new VendorPayoutsMetricResponse(vendorId, totalPaidOut, page.getTotalElements(), recent);
    }

    // -------------------------------------------------------------------------
    // Marketplace operator analytics
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public MarketplaceAnalyticsSummaryResponse getMarketplaceSummary(UUID marketplaceId, UUID operatorUserId, int lookbackDays) {
        assertOperator(marketplaceId, operatorUserId);
        int days = clamp(lookbackDays);
        Window w = window(days);
        String key = "marketplace:" + marketplaceId + ":summary:" + days;

        MarketplaceAnalyticsSummaryResponse cached = readCache(key, MarketplaceAnalyticsSummaryResponse.class);
        if (cached != null) return cached;

        MarketplaceSummaryProjection summary = analyticsRepository.marketplaceSummary(marketplaceId, w.from, w.to);
        List<DailyCountProjection> daily = analyticsRepository.marketplaceOrdersDaily(marketplaceId, w.from, w.to);

        BigDecimal gmv = nullSafe(summary.getGmv());
        BigDecimal commission = nullSafe(summary.getTotalCommission());
        double takeRate = gmv.compareTo(BigDecimal.ZERO) > 0
                ? commission.doubleValue() / gmv.doubleValue() : 0.0;

        MarketplaceAnalyticsSummaryResponse out = new MarketplaceAnalyticsSummaryResponse(
                marketplaceId, days, w.from, w.to,
                summary.getTotalOrders() != null ? summary.getTotalOrders() : 0L,
                gmv, commission, takeRate,
                summary.getActiveVendors() != null ? summary.getActiveVendors() : 0L,
                toDailyPoints(daily));
        writeCache(key, out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopVendorResponse> getTopVendors(UUID marketplaceId, UUID operatorUserId, int lookbackDays, int limit) {
        assertOperator(marketplaceId, operatorUserId);
        int days = clamp(lookbackDays);
        Window w = window(days);
        int cap = Math.min(limit, 50);

        List<TopVendorProjection> rows = analyticsRepository.marketplaceTopVendors(marketplaceId, w.from, w.to, cap);
        List<TopVendorResponse> result = new ArrayList<>(rows.size());
        for (TopVendorProjection r : rows) {
            result.add(new TopVendorResponse(
                    r.getVendorId(),
                    r.getVendorName(),
                    r.getTotalSubOrders() != null ? r.getTotalSubOrders() : 0L,
                    nullSafe(r.getTotalGrossRevenue()),
                    nullSafe(r.getTotalCommission()),
                    r.getCancellationRate() != null ? r.getCancellationRate() : 0.0));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private double resolveTargetShipHours(UUID marketplaceId) {
        // Use a fixed default; SLA policy target is consulted by the SLA service
        return DEFAULT_TARGET_SHIP_HOURS;
    }

    private void assertVendorAccess(UUID vendorId, UUID marketplaceId, UUID userId) {
        MarketplaceVendor vendor = marketplaceVendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
        if (!vendor.getMarketplace().getId().equals(marketplaceId)) {
            throw new ResourceNotFoundException("Vendor not found");
        }
        if (vendor.getVendorCompany().getOwner().getId().equals(userId)) {
            return;
        }
        assertOperator(marketplaceId, userId);
    }

    private void assertOperator(UUID marketplaceId, UUID userId) {
        marketplaceProfileRepository.findByCompanyId(marketplaceId)
                .filter(p -> p.getCompany().getOwner().getId() == userId)
                .orElseThrow(() -> new ForbiddenException("You are not an operator of this marketplace"));
    }

    private int clamp(int days) {
        if (days < MIN_LOOKBACK_DAYS) return MIN_LOOKBACK_DAYS;
        if (days > MAX_LOOKBACK_DAYS) return MAX_LOOKBACK_DAYS;
        return days;
    }

    private Window window(int days) {
        Instant now = Instant.now();
        return new Window(now.minus(days, ChronoUnit.DAYS), now);
    }

    private String cacheKey(UUID vendorId, String metric, int days) {
        return CACHE_PREFIX + vendorId + ":" + metric + ":" + days;
    }

    private <T> T readCache(String key, Class<T> type) {
        String raw = cacheService.get(key);
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("[VENDOR ANALYTICS] Cache deserialise error for {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, Object value) {
        try {
            cacheService.set(key, objectMapper.writeValueAsString(value), CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[VENDOR ANALYTICS] Cache serialise error for {}: {}", key, e.getMessage());
        }
    }

    private List<DailyPoint> toDailyPoints(List<DailyCountProjection> rows) {
        List<DailyPoint> out = new ArrayList<>(rows.size());
        for (DailyCountProjection r : rows) {
            out.add(new DailyPoint(r.getDay(), r.getCount() != null ? r.getCount() : 0L, null));
        }
        return out;
    }

    private BigDecimal nullSafe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private record Window(Instant from, Instant to) {}
}
