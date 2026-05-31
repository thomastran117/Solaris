package backend.services.impl.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.responses.inventory.AvailabilityEstimateResponse;
import backend.dtos.responses.inventory.NearestSourceResponse;
import backend.dtos.responses.inventory.PickupOptionResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.enums.LocationType;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.services.intf.CacheService;
import backend.services.intf.inventory.AvailabilityEstimateService;
import backend.utilities.GeoDistance;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AvailabilityEstimateServiceImpl implements AvailabilityEstimateService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityEstimateServiceImpl.class);

    /** Pickup is offered only if a STORE/HYBRID with stock sits within this radius of the buyer. */
    private static final double PICKUP_RADIUS_KM = 50.0;

    /** Maximum stocked locations we materialise per query — more than enough for ranking + pickup scan. */
    private static final int MAX_LOCATIONS = 25;

    /** Per-product, per-variant, per-coarse-coordinate cache TTL (matches product cache convention). */
    private static final long CACHE_TTL_SECONDS = 300;

    private static final String CACHE_PREFIX = "availability:";

    private final ProductRepository productRepository;
    private final LocationStockRepository locationStockRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AvailabilityEstimateServiceImpl(
            ProductRepository productRepository,
            LocationStockRepository locationStockRepository,
            CacheService cacheService,
            ObjectMapper objectMapper) {
        this(productRepository, locationStockRepository, cacheService, objectMapper, Clock.systemDefaultZone());
    }

    AvailabilityEstimateServiceImpl(
            ProductRepository productRepository,
            LocationStockRepository locationStockRepository,
            CacheService cacheService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.productRepository = productRepository;
        this.locationStockRepository = locationStockRepository;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public AvailabilityEstimateResponse estimateForMarketplace(UUID marketplaceId, UUID productId,
                                                               UUID variantId, Double buyerLat,
                                                               Double buyerLng) {
        Product product = productRepository.findByIdAndMarketplaceId(productId, marketplaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in this marketplace"));

        UUID variantRef = variantId;
        UUID companyId = product.getCompany().getId();

        String cacheKey = buildCacheKey(companyId, productId, variantRef, buyerLat, buyerLng);
        AvailabilityEstimateResponse cached = readCache(cacheKey);
        if (cached != null) return cached;

        List<LocationStock> stocked = loadStockedLocations(productId, variantRef, buyerLat, buyerLng);
        AvailabilityEstimateResponse response = buildResponse(stocked, buyerLat, buyerLng);
        writeCache(cacheKey, response);
        return response;
    }

    private List<LocationStock> loadStockedLocations(UUID productId, UUID variantRef,
                                                     Double buyerLat, Double buyerLng) {
        Pageable page = PageRequest.of(0, MAX_LOCATIONS);

        if (buyerLat != null && buyerLng != null) {
            List<LocationStock> byDistance = (variantRef == null)
                    ? locationStockRepository.findByProductOrderedByDistance(productId, buyerLat, buyerLng, page)
                    : locationStockRepository.findByVariantOrderedByDistance(productId, variantRef, buyerLat, buyerLng, page);
            if (!byDistance.isEmpty()) return byDistance;
        }

        return (variantRef == null)
                ? locationStockRepository.findStockedByProduct(productId)
                : locationStockRepository.findStockedByVariant(productId, variantRef);
    }

    private AvailabilityEstimateResponse buildResponse(List<LocationStock> stocked,
                                                       Double buyerLat, Double buyerLng) {
        if (stocked.isEmpty()) {
            return AvailabilityEstimateResponse.outOfStock();
        }

        InventoryLocation nearest = stocked.get(0).getLocation();
        Double nearestDistance = distanceTo(nearest, buyerLat, buyerLng);

        int[] shipping = (nearestDistance != null)
                ? GeoDistance.shippingDaysForDistance(nearestDistance)
                : GeoDistance.fallbackShippingDays();

        int handling = Math.max(0, nearest.getHandlingDays());
        int etaMin = handling + shipping[0];
        int etaMax = handling + shipping[1];

        LocalDate today = LocalDate.now(clock);

        NearestSourceResponse nearestSource = new NearestSourceResponse(
                nearest.getId(),
                nearest.getName(),
                nearest.getCity(),
                nearest.getCountry(),
                nearestDistance);

        PickupOptionResponse pickup = findPickupOption(stocked, buyerLat, buyerLng);

        return new AvailabilityEstimateResponse(
                true,
                nearestSource,
                etaMin,
                etaMax,
                today.plusDays(etaMin),
                today.plusDays(etaMax),
                pickup);
    }

    private PickupOptionResponse findPickupOption(List<LocationStock> stocked,
                                                  Double buyerLat, Double buyerLng) {
        PickupOptionResponse best = null;
        Double bestDistance = null;

        for (LocationStock ls : stocked) {
            InventoryLocation loc = ls.getLocation();
            if (loc.getType() != LocationType.STORE && loc.getType() != LocationType.HYBRID) continue;

            Double distance = distanceTo(loc, buyerLat, buyerLng);

            if (buyerLat != null && buyerLng != null) {
                if (distance == null || distance > PICKUP_RADIUS_KM) continue;
            }

            if (best == null || (distance != null && bestDistance != null && distance < bestDistance)) {
                best = new PickupOptionResponse(
                        loc.getId(),
                        loc.getName(),
                        loc.getCity(),
                        distance,
                        loc.getPickupReadyHours());
                bestDistance = distance;
            }
        }
        return best;
    }

    private Double distanceTo(InventoryLocation loc, Double buyerLat, Double buyerLng) {
        if (buyerLat == null || buyerLng == null) return null;
        if (loc.getLatitude() == null || loc.getLongitude() == null) return null;
        return GeoDistance.haversineKm(buyerLat, buyerLng, loc.getLatitude(), loc.getLongitude());
    }

    // --- caching ---

    /**
     * Coordinate bucket: 0.5° (~55 km at the equator). Coarser than fine-grained ranking but
     * keeps the cache hit rate high for nearby buyers.
     */
    private String buildCacheKey(UUID companyId, UUID productId, UUID variantRef, Double lat, Double lng) {
        String latBucket = (lat != null) ? String.valueOf((long) Math.floor(lat * 2)) : "x";
        String lngBucket = (lng != null) ? String.valueOf((long) Math.floor(lng * 2)) : "x";
        return CACHE_PREFIX + companyId + ":" + productId + ":" + variantRef + ":" + latBucket + ":" + lngBucket;
    }

    private AvailabilityEstimateResponse readCache(String key) {
        try {
            String raw = cacheService.get(key);
            if (raw == null) return null;
            return objectMapper.readValue(raw, AvailabilityEstimateResponse.class);
        } catch (Exception e) {
            log.debug("Availability cache read failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, AvailabilityEstimateResponse value) {
        try {
            cacheService.set(key, objectMapper.writeValueAsString(value), CACHE_TTL_SECONDS);
        } catch (JsonProcessingException e) {
            log.debug("Availability cache write failed for {}: {}", key, e.getMessage());
        } catch (Exception e) {
            log.debug("Availability cache write failed for {}: {}", key, e.getMessage());
        }
    }
}
