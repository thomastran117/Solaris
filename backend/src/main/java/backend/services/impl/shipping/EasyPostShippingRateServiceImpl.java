package backend.services.impl.shipping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Rate;
import com.easypost.model.Shipment;
import com.easypost.service.EasyPostClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.shipping.ShippingRate;
import backend.dtos.shipping.ShippingRateRequest;
import backend.services.intf.CacheService;
import backend.services.intf.shipping.ShippingRateService;
import backend.utils.MoneyUtil;

/**
 * EasyPost-backed {@link ShippingRateService} (Feature 13). Wraps the EasyPost SDK behind a
 * provider-agnostic interface, caches successful rate lookups in Redis for a short TTL, and
 * degrades gracefully: any provider error, empty result, or missing API key yields a single
 * flat-rate fallback so checkout always offers at least one option (never throws).
 */
@Service
public class EasyPostShippingRateServiceImpl implements ShippingRateService {

    private static final Logger log = LoggerFactory.getLogger(EasyPostShippingRateServiceImpl.class);

    /** Stable id so a customer's flat-rate selection can be matched on the confirm path. */
    static final String FLAT_RATE_ID = "flat-rate";
    private static final String FLAT_RATE_CARRIER = "Flat Rate";
    private static final String FLAT_RATE_SERVICE_CODE = "FLAT";

    private static final double GRAMS_PER_OUNCE = 28.3495;
    private static final double CM_PER_INCH = 2.54;

    private final EnvironmentSetting environmentSetting;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;

    /** Null when no API key is configured — service then runs in flat-rate-only mode. */
    private EasyPostClient client;

    public EasyPostShippingRateServiceImpl(
            EnvironmentSetting environmentSetting,
            CacheService cacheService,
            ObjectMapper objectMapper,
            @Qualifier("easyPostRetryTemplate") RetryTemplate retryTemplate) {
        this.environmentSetting = environmentSetting;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
    }

    @PostConstruct
    public void init() {
        String apiKey = environmentSetting.getEasyPost().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("EasyPost API key not configured — shipping rates run in flat-rate-only mode.");
            this.client = null;
            return;
        }
        try {
            EnvironmentSetting.EasyPost cfg = environmentSetting.getEasyPost();
            this.client = new EasyPostClient(apiKey, cfg.getConnectTimeoutMs(), cfg.getReadTimeoutMs());
        } catch (Exception e) {
            log.error("Failed to initialise EasyPost client — falling back to flat-rate-only mode.", e);
            this.client = null;
        }
    }

    @Override
    public List<ShippingRate> getRates(ShippingRateRequest request) {
        String cacheKey = cacheKey(request);

        List<ShippingRate> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (client == null) {
            // No external provider available — fall back without caching the placeholder.
            return List.of(flatRate(request));
        }

        try {
            List<ShippingRate> rates = retryTemplate.execute(ctx -> fetchFromEasyPost(request));
            if (rates == null || rates.isEmpty()) {
                // Degraded: never cache the fallback so a transient outage isn't pinned for the TTL.
                return List.of(flatRate(request));
            }
            writeCache(cacheKey, rates);
            return rates;
        } catch (Exception e) {
            log.warn("EasyPost rate lookup failed; serving flat-rate fallback. cacheKey={}", cacheKey, e);
            return List.of(flatRate(request));
        }
    }

    /**
     * Raw EasyPost SDK call, isolated behind a seam so unit tests can stub the network hop.
     * Builds a single-parcel shipment and maps the returned carrier rates.
     */
    protected List<ShippingRate> fetchFromEasyPost(ShippingRateRequest req) throws EasyPostException {
        Map<String, Object> fromAddress = new HashMap<>();
        fromAddress.put("street1", req.fromStreet());
        fromAddress.put("city", req.fromCity());
        fromAddress.put("state", req.fromStateProvince());
        fromAddress.put("zip", req.fromPostalCode());
        fromAddress.put("country", req.fromCountry());

        Map<String, Object> toAddress = new HashMap<>();
        toAddress.put("street1", req.toStreet());
        toAddress.put("street2", req.toStreet2());
        toAddress.put("city", req.toCity());
        toAddress.put("state", req.toStateProvince());
        toAddress.put("zip", req.toPostalCode());
        toAddress.put("country", req.toCountry());

        Map<String, Object> parcel = new HashMap<>();
        parcel.put("weight", gramsToOunces(req.weightGrams()));
        parcel.put("length", cmToInches(req.lengthCm()));
        parcel.put("width", cmToInches(req.widthCm()));
        parcel.put("height", cmToInches(req.heightCm()));

        Map<String, Object> shipmentParams = new HashMap<>();
        shipmentParams.put("to_address", toAddress);
        shipmentParams.put("from_address", fromAddress);
        shipmentParams.put("parcel", parcel);

        Shipment shipment = client.shipment.create(shipmentParams);
        List<Rate> rates = shipment.getRates();
        if (rates == null) {
            return List.of();
        }

        List<ShippingRate> mapped = new ArrayList<>(rates.size());
        for (Rate rate : rates) {
            if (rate.getRate() == null) {
                continue;
            }
            mapped.add(new ShippingRate(
                    rate.getId(),
                    rate.getCarrier(),
                    // EasyPost exposes a single `service` token with no separate display name, so we
                    // intentionally use it for both the human-readable name and the machine code.
                    rate.getService(),
                    rate.getService(),
                    estimatedDays(rate),
                    MoneyUtil.toCents(BigDecimal.valueOf(rate.getRate())),
                    rate.getCurrency() != null ? rate.getCurrency().toUpperCase(Locale.ROOT) : "USD"
            ));
        }
        return mapped;
    }

    private static Integer estimatedDays(Rate rate) {
        Number days = rate.getDeliveryDays() != null ? rate.getDeliveryDays() : rate.getEstDeliveryDays();
        return days != null ? days.intValue() : null;
    }

    private ShippingRate flatRate(ShippingRateRequest req) {
        EnvironmentSetting.EasyPost cfg = environmentSetting.getEasyPost();
        // Quote the fallback in the order's currency so a non-USD order isn't charged USD shipping.
        String currency = req.currency() != null && !req.currency().isBlank()
                ? req.currency().toUpperCase(Locale.ROOT) : "USD";
        return new ShippingRate(
                FLAT_RATE_ID,
                FLAT_RATE_CARRIER,
                cfg.getFlatRateServiceName(),
                FLAT_RATE_SERVICE_CODE,
                null,
                cfg.getFlatRateCents(),
                currency
        );
    }

    // ---- caching -----------------------------------------------------------

    private List<ShippingRate> readCache(String cacheKey) {
        try {
            String json = cacheService.get(cacheKey);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<ShippingRate>>() {});
        } catch (Exception e) {
            // A cache read failure must never fail the lookup — treat as a miss.
            log.warn("Shipping-rate cache read failed; treating as miss. cacheKey={}", cacheKey, e);
            return null;
        }
    }

    private void writeCache(String cacheKey, List<ShippingRate> rates) {
        try {
            String json = objectMapper.writeValueAsString(rates);
            cacheService.set(cacheKey, json, environmentSetting.getEasyPost().getRateCacheTtlSeconds());
        } catch (Exception e) {
            log.warn("Shipping-rate cache write failed (non-fatal). cacheKey={}", cacheKey, e);
        }
    }

    /**
     * Cache key includes parcel dimensions (not just weight) so two parcels of equal weight
     * but different box sizes don't collide and serve each other's volumetric rates.
     */
    private static String cacheKey(ShippingRateRequest req) {
        String origin = norm(req.fromPostalCode()) + "_" + norm(req.fromCountry());
        String dest = norm(req.toPostalCode()) + "_" + norm(req.toCountry());
        String dims = req.lengthCm() + "x" + req.widthCm() + "x" + req.heightCm();
        return "shipping-rates:" + origin + ":" + dest + ":" + req.weightGrams() + ":" + dims;
    }

    private static String norm(String s) {
        if (s == null || s.isBlank()) {
            return "na";
        }
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    // ---- unit conversion ---------------------------------------------------

    private static double gramsToOunces(int grams) {
        return grams / GRAMS_PER_OUNCE;
    }

    private static double cmToInches(int cm) {
        return cm / CM_PER_INCH;
    }
}
