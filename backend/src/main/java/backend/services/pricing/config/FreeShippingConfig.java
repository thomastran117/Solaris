package backend.services.pricing.config;

import java.math.BigDecimal;

/**
 * Config for PromotionRuleType.FREE_SHIPPING. Evaluator is a stub until shipping subsystem lands.
 *
 * @param maxShippingDiscount        optional cap on shipping reimbursement (null = full shipping)
 * @param requiresAllTargetProducts  when true, every target product must be present in the cart
 */
public record FreeShippingConfig(
        BigDecimal maxShippingDiscount,
        boolean requiresAllTargetProducts
) {}
