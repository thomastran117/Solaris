package backend.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central money conversion between a {@link BigDecimal} major-unit amount (e.g. dollars,
 * scale 2) and a {@code long} minor-unit amount (cents). One consistent scale/rounding rule
 * keeps order totals, shipping costs, and provider (Stripe) amounts in lockstep.
 */
public final class MoneyUtil {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyUtil() {
    }

    /**
     * Converts a major-unit amount to cents, rounding half-up. {@code null} is treated as zero.
     */
    public static long toCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Converts a cents amount to a major-unit {@link BigDecimal} with scale 2.
     */
    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents).divide(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }
}
