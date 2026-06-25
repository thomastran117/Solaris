package backend.services.pricing;

import backend.models.enums.TaxSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Computed tax for a cart: the base it was applied to, the rate used, and the resulting amount.
 * All monetary amounts are scale 2, HALF_UP; {@code taxRate} keeps its native scale.
 *
 * @param taxableAmount the base tax was applied to (post-discount subtotal + taxable shipping)
 * @param taxRate       the fractional rate applied
 * @param taxAmount     {@code taxableAmount * taxRate}, rounded
 * @param source        why the rate was chosen
 * @param taxRateId     id of the matched rate row, or null
 */
public record TaxAmounts(BigDecimal taxableAmount, BigDecimal taxRate, BigDecimal taxAmount,
                         TaxSource source, UUID taxRateId) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /** Zero tax with the given source (NONE for no destination, CONFIG_FALLBACK with a zero rate, etc.). */
    public static TaxAmounts zero(TaxSource source) {
        return new TaxAmounts(ZERO, BigDecimal.ZERO, ZERO, source, null);
    }
}
