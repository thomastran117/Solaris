package backend.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central money conversion between a {@link BigDecimal} major-unit amount (e.g. dollars,
 * scale 2) and a {@code long} minor-unit amount (cents). One consistent scale/rounding rule
 * keeps order totals, shipping costs, and provider (Stripe) amounts in lockstep.
 */
public final class MoneyUtil {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO2 = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private MoneyUtil() {
    }

    /**
     * Splits {@code total} across line {@code weights} proportionally, scale 2 HALF_UP, so the returned
     * shares sum exactly to {@code total} — the last line with a positive weight absorbs any rounding
     * drift. Lines with a non-positive weight receive zero. When {@code total} or the weight pool is
     * zero, every share is zero.
     *
     * <p>Single source of truth for per-line money distribution (coupon savings, tax) so two callers
     * can't drift into slightly different rounding.
     */
    public static List<BigDecimal> allocateProportionally(BigDecimal total, List<BigDecimal> weights) {
        if (weights == null || weights.isEmpty()) {
            return new ArrayList<>();
        }
        int n = weights.size();
        List<BigDecimal> out = new ArrayList<>(Collections.nCopies(n, ZERO2));
        if (total == null || total.signum() == 0) {
            return out;
        }
        BigDecimal pool = BigDecimal.ZERO;
        int lastIdx = -1;
        for (int i = 0; i < n; i++) {
            BigDecimal w = weights.get(i);
            if (w != null && w.signum() > 0) {
                pool = pool.add(w);
                lastIdx = i;
            }
        }
        if (pool.signum() <= 0) {
            return out;
        }
        BigDecimal scaledTotal = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal w = weights.get(i);
            if (w == null || w.signum() <= 0) {
                continue;
            }
            BigDecimal share = (i == lastIdx)
                    ? scaledTotal.subtract(allocated)
                    : scaledTotal.multiply(w).divide(pool, 2, RoundingMode.HALF_UP);
            out.set(i, share);
            allocated = allocated.add(share);
        }
        return out;
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
