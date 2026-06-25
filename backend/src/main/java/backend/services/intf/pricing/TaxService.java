package backend.services.intf.pricing;

import backend.services.pricing.ResolvedTaxRate;
import backend.services.pricing.TaxAmounts;
import backend.services.pricing.TaxDestination;

import java.math.BigDecimal;

/**
 * Resolves destination-based sales-tax rates and computes tax amounts.
 *
 * <p>{@link #resolve} reads the jurisdiction table (with a configurable fallback) and is the only
 * DB-touching method. {@link #compute} is pure arithmetic so the pricing engine can apply tax inside
 * its side-effect-free quote computation, and other callers (refunds, B2B) can reuse identical math.
 */
public interface TaxService {

    /**
     * Resolves a rate for the given destination. A {@code null} destination yields
     * {@link ResolvedTaxRate#none()} (no tax). When a destination is supplied but no active row
     * matches, the configured fallback rate is returned ({@code CONFIG_FALLBACK}).
     */
    ResolvedTaxRate resolve(TaxDestination dest);

    /**
     * Pure: tax = {@code (taxableSubtotal + (shippingTaxable ? shippingAmount : 0)) * rate}, scale 2
     * HALF_UP. {@code null} amounts are treated as zero.
     */
    TaxAmounts compute(BigDecimal taxableSubtotal, BigDecimal shippingAmount, ResolvedTaxRate rate);
}
