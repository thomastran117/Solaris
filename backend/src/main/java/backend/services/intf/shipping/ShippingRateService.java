package backend.services.intf.shipping;

import java.util.List;

import backend.dtos.shipping.ShippingRate;
import backend.dtos.shipping.ShippingRateRequest;

/**
 * Provider-agnostic shipping-rate lookup (Feature 13). Implementations fetch live carrier
 * rates for a parcel, cache them for a short window, and <strong>never throw</strong> on a
 * provider/cache failure — a degraded checkout still receives at least a flat-rate option.
 */
public interface ShippingRateService {

    /**
     * Returns the available rate options for the given parcel. Results are cache-aside cached
     * for a short TTL keyed on origin/destination/weight/dims. On any provider error, an empty
     * result, or a missing API key, returns a single flat-rate fallback option rather than failing.
     */
    List<ShippingRate> getRates(ShippingRateRequest request);
}
