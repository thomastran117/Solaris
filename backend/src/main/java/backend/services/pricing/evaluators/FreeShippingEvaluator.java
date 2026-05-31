package backend.services.pricing.evaluators;

import org.springframework.stereotype.Component;

import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.services.pricing.WorkingLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 2 stub. Shipping reductions are handled in {@code PricingEngineImpl} once the
 * shipping subsystem lands in Phase 4; for now, declaring this evaluator keeps the
 * registry complete so FREE_SHIPPING rules can be authored without a "no evaluator
 * registered" error. Records zero line-level savings.
 */
@Component
public class FreeShippingEvaluator implements RuleEvaluator {

    @Override
    public PromotionRuleType type() {
        return PromotionRuleType.FREE_SHIPPING;
    }

    @Override
    public BigDecimal apply(PromotionRule rule, Object parsedConfig, List<WorkingLine> eligibleLines) {
        return BigDecimal.ZERO;
    }
}
