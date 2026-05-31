package backend.repositories.projections;

import java.math.BigDecimal;

/**
 * Aggregated redemption stats for a single PromotionRule, used by the per-rule
 * analytics endpoint exposed under the promotion-rules controller.
 */
public interface PromotionRuleAnalyticsProjection {
    Long getRedemptionCount();
    BigDecimal getTotalSavings();
    Long getUniqueOrderCount();
    Long getUniqueUserCount();
}
