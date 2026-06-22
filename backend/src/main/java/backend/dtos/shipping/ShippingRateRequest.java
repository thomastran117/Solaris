package backend.dtos.shipping;

/**
 * Origin/destination/parcel inputs for a live carrier rate lookup (Feature 13).
 *
 * <p>The origin is derived from the vendor's primary {@link backend.models.core.InventoryLocation};
 * note that location addresses may be sparse (no state/postal in older records) — EasyPost rates on
 * whatever is supplied, and the {@link backend.services.intf.shipping.ShippingRateService} degrades to
 * a flat-rate option when the carrier cannot produce a quote.
 *
 * <p>Weight and dimensions are an order-level parcel approximation: {@code weightGrams} is the summed
 * product weight; length/width are the max across items and height is the stacked sum. Dimensional
 * packing is out of scope for v1.
 */
public record ShippingRateRequest(
        String fromStreet,
        String fromCity,
        String fromStateProvince,
        String fromPostalCode,
        String fromCountry,
        String toStreet,
        String toStreet2,
        String toCity,
        String toStateProvince,
        String toPostalCode,
        String toCountry,
        int weightGrams,
        int lengthCm,
        int widthCm,
        int heightCm,
        String currency
) {}
