package backend.services.pricing.evaluators;

import org.springframework.stereotype.Component;

import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.PercentageOffConfig;
import backend.services.pricing.config.PromotionScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Applies a percentage discount. LINE scope discounts each eligible line independently;
 * ORDER scope discounts the summed remaining across eligible lines and distributes the
 * saving proportionally (last line absorbs rounding).
 */
@Component
public class PercentageOffEvaluator implements RuleEvaluator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public PromotionRuleType type() {
        return PromotionRuleType.PERCENTAGE_OFF;
    }

    @Override
    public BigDecimal apply(PromotionRule rule, Object parsedConfig, List<WorkingLine> eligibleLines) {
        PercentageOffConfig cfg = (PercentageOffConfig) parsedConfig;
        BigDecimal percent = cfg.percent();
        BigDecimal cap = cfg.maxDiscount();
        BigDecimal total = BigDecimal.ZERO;

        if (cfg.appliesTo() == PromotionScope.LINE) {
            for (WorkingLine line : eligibleLines) {
                if (line.remaining().signum() <= 0) continue;
                BigDecimal gross = line.remaining().multiply(percent)
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                if (cap != null && gross.compareTo(cap) > 0) gross = cap;
                total = total.add(line.applySavings(rule.getId(), gross));
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        }

        // ORDER scope: proportional distribution across eligible lines
        BigDecimal pool = eligibleLines.stream()
                .map(WorkingLine::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pool.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal target = pool.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        if (cap != null && target.compareTo(cap) > 0) target = cap;
        if (target.compareTo(pool) > 0) target = pool;

        return distribute(rule.getId(), eligibleLines, pool, target);
    }

    /** Distribute {@code target} across lines proportionally to their current remaining. */
    static BigDecimal distribute(UUID ruleId, List<WorkingLine> lines, BigDecimal pool, BigDecimal target) {
        BigDecimal taken = BigDecimal.ZERO;
        int lastIdx = lines.size() - 1;
        for (int i = 0; i <= lastIdx; i++) {
            WorkingLine line = lines.get(i);
            BigDecimal share;
            if (i == lastIdx) {
                share = target.subtract(taken);
            } else {
                share = target.multiply(line.remaining())
                        .divide(pool, 2, RoundingMode.HALF_UP);
            }
            if (share.signum() > 0) {
                taken = taken.add(line.applySavings(ruleId, share));
            }
        }
        return taken.setScale(2, RoundingMode.HALF_UP);
    }
}
