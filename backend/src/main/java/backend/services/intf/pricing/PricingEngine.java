package backend.services.intf.pricing;

import backend.services.pricing.CartContext;
import backend.services.pricing.PricingResult;
import backend.services.pricing.ResolvedTaxRate;

/**
 * Computes a price quote for a cart. Implementations must be idempotent and side-effect free:
 * the quote API uses this directly; the order flow calls it and then persists the result.
 */
public interface PricingEngine {

    /** Resolves the tax rate from {@code ctx.destination()} (or no tax when absent), then prices the cart. */
    PricingResult quote(CartContext ctx);

    /**
     * Prices the cart using an already-resolved tax rate, so a caller that needs the resolved
     * jurisdiction snapshot (order creation) resolves once rather than resolving here a second time.
     */
    PricingResult quote(CartContext ctx, ResolvedTaxRate resolvedTax);
}
