package backend.dtos.responses.inventory;

import java.time.LocalDate;

/**
 * Customer-facing availability + delivery estimate for a product (or a specific variant).
 *
 * <p>All fields except {@code inStock} may be null when stock is exhausted across all
 * locations, or when the company has no active inventory locations configured.</p>
 */
public record AvailabilityEstimateResponse(
        boolean inStock,
        NearestSourceResponse nearestSource,
        Integer etaDaysMin,
        Integer etaDaysMax,
        LocalDate etaDateMin,
        LocalDate etaDateMax,
        PickupOptionResponse pickup
) {
    public static AvailabilityEstimateResponse outOfStock() {
        return new AvailabilityEstimateResponse(false, null, null, null, null, null, null);
    }
}
