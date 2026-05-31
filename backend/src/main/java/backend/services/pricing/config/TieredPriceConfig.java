package backend.services.pricing.config;

import java.math.BigDecimal;
import java.util.List;

/**
 * Config for PromotionRuleType.TIERED_PRICE.
 * The engine sorts breakpoints by minQty ascending and selects the highest whose
 * minQty ≤ line quantity.
 */
public record TieredPriceConfig(
        List<Breakpoint> breakpoints
) {
    public record Breakpoint(int minQty, BigDecimal unitPrice) {}
}
