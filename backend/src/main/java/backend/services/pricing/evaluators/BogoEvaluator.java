package backend.services.pricing.evaluators;

import org.springframework.stereotype.Component;

import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.BogoConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Buy-X-Get-Y evaluator. Counts trigger units across the cart, computes how many times
 * the pattern fires (capped by {@code maxApplicationsPerOrder}), and awards reward units
 * from the cheapest eligible lines first (deterministic).
 *
 * <p>When trigger and reward sets overlap (e.g. "buy 2 get 1 free" same SKU), triggers
 * are counted first via {@code bogoTriggerConsumed} so reward units don't double-book.
 */
@Component
public class BogoEvaluator implements RuleEvaluator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public PromotionRuleType type() {
        return PromotionRuleType.BOGO;
    }

    @Override
    public BigDecimal apply(PromotionRule rule, Object parsedConfig, List<WorkingLine> eligibleLines) {
        BogoConfig cfg = (BogoConfig) parsedConfig;
        Set<UUID> triggerSet = cfg.triggerProductIds() == null
                ? new HashSet<>() : new HashSet<>(cfg.triggerProductIds());
        Set<UUID> rewardSet = cfg.rewardProductIds() == null || cfg.rewardProductIds().isEmpty()
                ? triggerSet : new HashSet<>(cfg.rewardProductIds());

        List<WorkingLine> triggerLines = eligibleLines.stream()
                .filter(l -> triggerSet.isEmpty() || triggerSet.contains(l.productId()))
                .toList();
        List<WorkingLine> rewardLines = new ArrayList<>(eligibleLines.stream()
                .filter(l -> rewardSet.contains(l.productId()) || rewardSet.isEmpty())
                .toList());

        int totalTrigger = triggerLines.stream().mapToInt(WorkingLine::bogoTriggerAvailable).sum();
        int applications = Math.min(cfg.maxApplicationsPerOrder(), totalTrigger / cfg.triggerQty());
        if (applications <= 0) return BigDecimal.ZERO;

        int triggerUnitsToConsume = applications * cfg.triggerQty();
        int rewardUnitsToGive     = applications * cfg.rewardQty();

        // Consume triggers first (cheapest first is fine — prevents double-booking overlap sets)
        triggerLines = triggerLines.stream()
                .sorted(Comparator.comparing(WorkingLine::unitBasePrice))
                .toList();
        for (WorkingLine line : triggerLines) {
            if (triggerUnitsToConsume <= 0) break;
            int take = Math.min(triggerUnitsToConsume, line.bogoTriggerAvailable());
            if (take <= 0) continue;
            line.consumeBogoTrigger(take);
            triggerUnitsToConsume -= take;
        }

        // Award reward units from cheapest reward-eligible lines first
        rewardLines.sort(Comparator.comparing(WorkingLine::unitBasePrice));
        BigDecimal totalSavings = BigDecimal.ZERO;
        for (WorkingLine line : rewardLines) {
            if (rewardUnitsToGive <= 0) break;
            int available = line.bogoRewardAvailable();
            if (available <= 0) continue;
            int take = Math.min(rewardUnitsToGive, available);

            BigDecimal perUnitSaving = line.unitBasePrice()
                    .multiply(cfg.rewardPercentOff())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal desired = perUnitSaving.multiply(BigDecimal.valueOf(take))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal taken = line.applySavings(rule.getId(), desired);
            if (taken.signum() > 0) {
                line.consumeBogoReward(take);
                totalSavings = totalSavings.add(taken);
                rewardUnitsToGive -= take;
            } else {
                // No headroom on this line (fully discounted by earlier rule) — skip it.
                line.consumeBogoReward(take);
                rewardUnitsToGive -= take;
            }
        }
        return totalSavings.setScale(2, RoundingMode.HALF_UP);
    }
}
