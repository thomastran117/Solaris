package backend.models.enums;

/**
 * Discriminator for PromotionRule.configJson. Each type has a corresponding evaluator
 * in backend.services.pricing.evaluators and a config record in backend.services.pricing.config.
 */
public enum PromotionRuleType {
    PERCENTAGE_OFF,
    FIXED_OFF,
    BOGO,
    TIERED_PRICE,
    FREE_SHIPPING
}
