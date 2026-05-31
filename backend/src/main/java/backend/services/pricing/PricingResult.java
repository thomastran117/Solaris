package backend.services.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable pricing quote. All amounts scale 2, HALF_UP.
 * {@code finalTotal = max(0, subtotal − promotionSavings − couponSavings) + shippingAmount}
 * where {@code shippingAmount} is already net of any FREE_SHIPPING reduction ({@link #shippingSavings}).
 *
 * @param lines              per-line breakdown, same order as input
 * @param appliedPromotions  rules that fired, in application order (useful for UI chips)
 * @param subtotal           sum of quantity * unitBasePrice across lines, pre-discount
 * @param promotionSavings   sum of line-level savings from PromotionRule-driven discounts
 * @param couponSavings      additional saving from the redeemed coupon (0 if none)
 * @param appliedCouponCode  echo of the coupon code when one was successfully applied, else null
 * @param shippingAmount     shipping cost the customer pays, after FREE_SHIPPING reductions
 * @param shippingSavings    total reduction applied to the shipping line by FREE_SHIPPING rules
 * @param finalTotal         what the customer pays
 * @param warnings           advisory issues (per-user caps, soft skips) — not hard failures
 */
public record PricingResult(
        List<LineBreakdown> lines,
        List<AppliedPromotion> appliedPromotions,
        BigDecimal subtotal,
        BigDecimal promotionSavings,
        BigDecimal couponSavings,
        String appliedCouponCode,
        BigDecimal shippingAmount,
        BigDecimal shippingSavings,
        BigDecimal finalTotal,
        List<String> warnings
) {}
