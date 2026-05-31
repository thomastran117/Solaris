package backend.dtos.responses.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-rule redemption analytics over a time window. Powers
 * {@code GET /api/companies/{companyId}/promotion-rules/{ruleId}/analytics}.
 *
 * @param ruleId              the rule analytics apply to
 * @param from                window start (null = unbounded)
 * @param to                  window end (null = unbounded)
 * @param redemptionCount     total rows in promotion_redemptions for this rule in window
 * @param totalSavings        sum of discountAmount contributed by this rule in window
 * @param uniqueOrderCount    distinct orders that triggered the rule
 * @param uniqueUserCount     distinct users that redeemed the rule
 */
public record PromotionRuleAnalyticsResponse(
        UUID ruleId,
        Instant from,
        Instant to,
        long redemptionCount,
        BigDecimal totalSavings,
        long uniqueOrderCount,
        long uniqueUserCount
) {}
