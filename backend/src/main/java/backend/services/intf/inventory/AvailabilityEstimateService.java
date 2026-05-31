package backend.services.intf.inventory;

import backend.dtos.responses.inventory.AvailabilityEstimateResponse;
import java.util.UUID;

public interface AvailabilityEstimateService {

    /**
     * Compute the availability + delivery estimate for a product listed in the given
     * marketplace, for a buyer at the given coordinates.
     *
     * @param marketplaceId the marketplace the buyer is shopping in
     * @param productId     the product to look up
     * @param variantId     optional variant id; null means product-level stock
     * @param buyerLat      optional buyer latitude; null falls back to displayOrder ranking
     * @param buyerLng      optional buyer longitude; null falls back to displayOrder ranking
     */
    AvailabilityEstimateResponse estimateForMarketplace(
            UUID marketplaceId,
            UUID productId,
            UUID variantId,
            Double buyerLat,
            Double buyerLng);
}
