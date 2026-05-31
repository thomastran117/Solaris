package backend.services.pricing.evaluators;

import org.springframework.stereotype.Component;

import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.services.pricing.WorkingLine;
import backend.services.pricing.config.TieredPriceConfig;
import backend.services.pricing.config.TieredPriceConfig.Breakpoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Applies per-line tiered pricing. For each line, selects the highest breakpoint whose
 * {@code minQty} ≤ line quantity. If the tier's unit price is lower than the line's base
 * price, the engine credits the difference × quantity as savings (capped at remaining).
 */
@Component
public class TieredPriceEvaluator implements RuleEvaluator {

    @Override
    public PromotionRuleType type() {
        return PromotionRuleType.TIERED_PRICE;
    }

    @Override
    public BigDecimal apply(PromotionRule rule, Object parsedConfig, List<WorkingLine> eligibleLines) {
        TieredPriceConfig cfg = (TieredPriceConfig) parsedConfig;
        List<Breakpoint> sorted = cfg.breakpoints().stream()
                .sorted((a, b) -> Integer.compare(a.minQty(), b.minQty()))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        for (WorkingLine line : eligibleLines) {
            if (line.remaining().signum() <= 0) continue;
            Breakpoint chosen = null;
            for (Breakpoint bp : sorted) {
                if (bp.minQty() <= line.quantity()) chosen = bp; else break;
            }
            if (chosen == null) continue;
            if (chosen.unitPrice().compareTo(line.unitBasePrice()) >= 0) continue;

            BigDecimal perUnitDelta = line.unitBasePrice().subtract(chosen.unitPrice());
            BigDecimal saving = perUnitDelta.multiply(BigDecimal.valueOf(line.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            total = total.add(line.applySavings(rule.getId(), saving));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
