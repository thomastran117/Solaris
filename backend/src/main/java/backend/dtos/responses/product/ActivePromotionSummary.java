package backend.dtos.responses.product;

import backend.models.enums.PromotionRuleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight, presentation-only snapshot of an in-window {@code PromotionRule} that targets a product.
 * Surfaced on {@code ProductResponse} so listing/detail UIs can render strikethrough prices and
 * "Sale" / "-X%" badges without having to run the pricing engine for every catalog read.
 *
 * <p>The pricing engine remains the source of truth at order/cart time — this DTO never feeds checkout.
 * For non-discount rule types ({@code BOGO}, {@code TIERED_PRICE}, {@code FREE_SHIPPING}), {@link #percentOff}
 * and {@link #amountOff} are null and the UI falls back to a generic "Sale" label.
 *
 * @param id         PromotionRule id
 * @param ruleType   underlying rule type — UI may key off this for icons/labels
 * @param percentOff non-null only for {@link PromotionRuleType#PERCENTAGE_OFF}
 * @param amountOff  non-null only for {@link PromotionRuleType#FIXED_OFF}
 * @param endDate    when the active window closes; null means the rule has no scheduled end
 */
public record ActivePromotionSummary(
        UUID id,
        PromotionRuleType ruleType,
        BigDecimal percentOff,
        BigDecimal amountOff,
        Instant endDate
) {}
