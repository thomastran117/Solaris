package backend.services.pricing;

import backend.models.enums.TaxSource;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outcome of resolving a {@link TaxDestination} to a concrete rate.
 *
 * @param rate            fractional rate, e.g. {@code 0.08875}; never null (zero when no tax applies)
 * @param shippingTaxable whether shipping cost is taxable in this jurisdiction
 * @param source          why this rate was chosen
 * @param taxRateId       id of the matched {@code TaxRate} row, or null for fallback/none
 */
public record ResolvedTaxRate(BigDecimal rate, boolean shippingTaxable, TaxSource source, UUID taxRateId) {

    /** No tax (e.g. no destination supplied). */
    public static ResolvedTaxRate none() {
        return new ResolvedTaxRate(BigDecimal.ZERO, false, TaxSource.NONE, null);
    }
}
