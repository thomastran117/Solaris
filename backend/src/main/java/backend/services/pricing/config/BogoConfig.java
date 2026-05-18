package backend.services.pricing.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Config for PromotionRuleType.BOGO (Buy-X-Get-Y).
 *
 * @param triggerProductIds        products that count toward the "buy" clause. Empty = any product in
 *                                 the rule's targetProducts set (or whole catalogue if that is empty).
 * @param triggerQty               number of trigger units required per application (&ge; 1)
 * @param rewardProductIds         products eligible for the reward. Empty = same as trigger set.
 * @param rewardQty                reward units per application (&ge; 1)
 * @param rewardPercentOff         0-100. 100 = free.
 * @param maxApplicationsPerOrder  cap on how many times the pattern fires in one cart (&ge; 1)
 */
public record BogoConfig(
        List<UUID> triggerProductIds,
        int triggerQty,
        List<UUID> rewardProductIds,
        int rewardQty,
        BigDecimal rewardPercentOff,
        int maxApplicationsPerOrder
) {}
