package backend.services.pricing.config;

import java.math.BigDecimal;

/**
 * Config for PromotionRuleType.PERCENTAGE_OFF.
 *
 * @param percent     0 &lt; percent ≤ 100
 * @param maxDiscount optional absolute cap on the saving (null = no cap)
 * @param appliesTo   scope — LINE applies per matching line, ORDER applies once to the subtotal
 */
public record PercentageOffConfig(
        BigDecimal percent,
        BigDecimal maxDiscount,
        PromotionScope appliesTo
) {}
