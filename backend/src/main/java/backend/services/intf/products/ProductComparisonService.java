package backend.services.intf.products;

import java.util.List;
import java.util.UUID;

import backend.dtos.responses.product.ProductComparisonResponse;

/**
 * Read-only, customer-facing product comparison for a marketplace storefront. Produces a
 * unified attribute matrix for 2–4 products that all belong to the same marketplace and are
 * publicly listed (ACTIVE + marketplaceListed).
 */
public interface ProductComparisonService {

    /**
     * Builds a comparison matrix for the given product ids within a marketplace.
     *
     * @throws backend.exceptions.http.BadRequestException     if fewer than 2 or more than 4 ids are supplied
     * @throws backend.exceptions.http.ResourceNotFoundException if any id is not a publicly-listed product in this marketplace
     */
    ProductComparisonResponse compare(UUID marketplaceId, List<UUID> productIds);
}
