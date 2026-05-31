package backend.services.pricing.config;

import java.math.BigDecimal;

/**
 * Config for PromotionRuleType.FIXED_OFF.
 *
 * @param amount    positive absolute currency amount
 * @param appliesTo LINE or ORDER
 */
public record FixedOffConfig(
        BigDecimal amount,
        PromotionScope appliesTo
) {}
