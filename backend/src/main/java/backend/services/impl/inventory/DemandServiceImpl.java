package backend.services.impl.inventory;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.responses.analytics.DemandEntry;
import backend.dtos.responses.analytics.HotProductsResponse;
import backend.exceptions.http.BadRequestException;
import backend.models.enums.CompanyCapability;
import backend.repositories.ProductRepository;
import backend.repositories.projections.ProductDemandProjection;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.inventory.DemandService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DemandServiceImpl implements DemandService {

    private static final Logger log = LoggerFactory.getLogger(DemandServiceImpl.class);

    /** Maximum entries stored per cache key. API requests are sliced to the requested limit. */
    private static final int CACHE_SIZE = 50;

    private static final String CACHE_KEY_PREFIX = "demand:hot:"; // + window + ":" + companyId
    private static final Set<String> VALID_WINDOWS = Set.of("1h", "24h");

    private final CompanyAccessService companyAccessService;
    private final ProductRepository productRepository;
    private final CacheService      cacheService;
    private final ObjectMapper      objectMapper;

    @Value("${app.demand.cache-ttl-1h-seconds:300}")
    private int cacheTtl1h;

    @Value("${app.demand.cache-ttl-24h-seconds:900}")
    private int cacheTtl24h;

    public DemandServiceImpl(
            CompanyAccessService companyAccessService,
            ProductRepository productRepository,
            CacheService cacheService,
            ObjectMapper objectMapper) {
        this.companyAccessService = companyAccessService;
        this.productRepository = productRepository;
        this.cacheService      = cacheService;
        this.objectMapper      = objectMapper;
    }

    @Override
    public HotProductsResponse getHotProducts(UUID companyId, UUID ownerId, String window, int limit) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_PRODUCTS);

        if (!VALID_WINDOWS.contains(window)) {
            throw new BadRequestException("window must be '1h' or '24h'");
        }

        String cacheKey = CACHE_KEY_PREFIX + window + ":" + companyId;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            try {
                HotProductsResponse full = objectMapper.readValue(cached, HotProductsResponse.class);
                // Cache holds up to CACHE_SIZE entries — slice to the requested limit
                return new HotProductsResponse(
                        full.window(), full.computedAt(), full.windowStart(),
                        full.products().stream().limit(limit).toList());
            } catch (Exception e) {
                log.warn("[DEMAND] Cache deserialise error for key {}: {}", cacheKey, e.getMessage());
            }
        }

        return computeAndCache(companyId, window, limit, cacheKey);
    }

    @Override
    @Transactional(readOnly = true)
    public void refreshCache(UUID companyId, String window) {
        String cacheKey = CACHE_KEY_PREFIX + window + ":" + companyId;
        computeAndCache(companyId, window, CACHE_SIZE, cacheKey);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    private HotProductsResponse computeAndCache(UUID companyId, String window, int limit, String cacheKey) {
        Instant now         = Instant.now();
        boolean is1h        = "1h".equals(window);
        Instant windowStart = now.minus(is1h ? 1 : 24, ChronoUnit.HOURS);
        double  windowHours = is1h ? 1.0 : 24.0;

        List<ProductDemandProjection> raw =
                productRepository.findTopByDemandSince(companyId, windowStart, CACHE_SIZE);

        // For the 1h window, also fetch the 24h data so we can compute acceleration ratio.
        // Products not in the 24h result get a baseline of 0 (brand new sellers).
        Map<UUID, Long> baseline24h = Collections.emptyMap();
        if (is1h) {
            baseline24h = productRepository
                    .findTopByDemandSince(companyId, now.minus(24, ChronoUnit.HOURS), CACHE_SIZE)
                    .stream()
                    .collect(Collectors.toMap(
                            ProductDemandProjection::getProductId,
                            p -> p.getTotalUnitsSold() != null ? p.getTotalUnitsSold() : 0L));
        }

        Map<UUID, Long> base = baseline24h;
        List<DemandEntry> entries = new ArrayList<>(raw.size());
        int rank = 1;

        for (ProductDemandProjection p : raw) {
            long   units    = p.getTotalUnitsSold() != null ? p.getTotalUnitsSold() : 0L;
            double velocity = units / windowHours;

            double acceleration = 1.0;
            if (is1h) {
                // units24h / 24 = average hourly rate over last 24h
                double velocity24hAvg = base.getOrDefault(p.getProductId(), 0L) / 24.0;
                // epsilon prevents division by zero for products with no prior 24h history
                acceleration = velocity / Math.max(velocity24hAvg, 0.001);
            }

            entries.add(new DemandEntry(
                    p.getProductId(),
                    p.getProductName(),
                    p.getSku(),
                    p.getPrice(),
                    p.getCurrency(),
                    units,
                    p.getTotalRevenue(),
                    velocity,
                    acceleration,
                    rank++));
        }

        HotProductsResponse response = new HotProductsResponse(window, now, windowStart, entries);

        try {
            int ttl = is1h ? cacheTtl1h : cacheTtl24h;
            cacheService.set(cacheKey, objectMapper.writeValueAsString(response), ttl);
        } catch (Exception e) {
            log.warn("[DEMAND] Cache write error for key {}: {}", cacheKey, e.getMessage());
        }

        // Return sliced to the requested limit — the cached value always holds CACHE_SIZE
        return new HotProductsResponse(
                window, now, windowStart,
                entries.stream().limit(limit).toList());
    }
}
