package backend.services.pricing.evaluators;

import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.services.pricing.WorkingLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strategy for one PromotionRuleType. Implementations are stateless and Spring-managed,
 * resolved from PricingEngineImpl via a map keyed by {@link #type()}.
 *
 * <p>Implementations should mutate {@code eligibleLines} in place via
 * {@link WorkingLine#applySavings(long, BigDecimal)} and return the total saving taken.
 * {@code parsedConfig} is the already-validated record produced by
 * {@link backend.services.pricing.config.PromotionConfigValidator#parseStored}.
 */
public interface RuleEvaluator {

    /** The rule type this evaluator handles. */
    PromotionRuleType type();

    /**
     * Applies the rule to the eligible lines (already filtered for {@code rule.targetProducts}).
     *
     * @param rule           rule being evaluated (for id, name, config-less metadata)
     * @param parsedConfig   type-specific config record (from PromotionConfigValidator)
     * @param eligibleLines  lines passing the product-target gate (may be empty)
     * @return total saving taken by this rule across the provided lines
     */
    BigDecimal apply(PromotionRule rule, Object parsedConfig, List<WorkingLine> eligibleLines);
}
