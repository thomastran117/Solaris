package backend.services.pricing;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line item in the pricing request. Prices are server-authoritative — callers pass
 * only (productId, variantId?, quantity) for products or (bundleId, quantity) for bundles;
 * the engine resolves the current unit price from the repository before constructing a CartLine.
 *
 * <p>Invariant: exactly one of {@code productId} / {@code bundleId} is non-null.
 *
 * @param index          stable 0-based position in the original request, used for breakdowns
 * @param productId      owning product; null for bundle lines
 * @param variantId      optional variant; when present, overrides the product-level price; null for bundle lines
 * @param quantity       &ge; 1
 * @param unitBasePrice  pre-promotion unit price
 * @param companyId      owning company (used to scope rule candidates)
 * @param bundleId       owning bundle; null for product lines
 */
public record CartLine(
        int index,
        UUID productId,
        UUID variantId,
        int quantity,
        BigDecimal unitBasePrice,
        UUID companyId,
        UUID bundleId
) {}
