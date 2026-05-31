package backend.services.pricing.evaluators;

import org.springframework.stereotype.Component;

import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.FixedOffConfig;
import backend.services.pricing.config.PromotionScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Applies a fixed currency discount. LINE scope takes up to {@code amount} off each
 * eligible line (capped at each line's remaining). ORDER scope deducts {@code amount}
 * once from the subtotal, distributed proportionally across eligible lines.
 */
@Component
public class FixedOffEvaluator implements RuleEvaluator {

    @Override
    public PromotionRuleType type() {
        return PromotionRuleType.FIXED_OFF;
    }

    @Override
    public BigDecimal apply(PromotionRule rule, Object parsedConfig, List<WorkingLine> eligibleLines) {
        FixedOffConfig cfg = (FixedOffConfig) parsedConfig;
        BigDecimal amount = cfg.amount();

        if (cfg.appliesTo() == PromotionScope.LINE) {
            BigDecimal total = BigDecimal.ZERO;
            for (WorkingLine line : eligibleLines) {
                total = total.add(line.applySavings(rule.getId(), amount));
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal pool = eligibleLines.stream()
                .map(WorkingLine::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pool.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal target = amount.min(pool);
        return PercentageOffEvaluator.distribute(rule.getId(), eligibleLines, pool, target);
    }
}
