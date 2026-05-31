package backend.services.intf.pricing;

import backend.services.pricing.CartContext;
import backend.services.pricing.PricingResult;

/**
 * Computes a price quote for a cart. Implementations must be idempotent and side-effect free:
 * the quote API uses this directly; the order flow calls it and then persists the result.
 */
public interface PricingEngine {
    PricingResult quote(CartContext ctx);
}
