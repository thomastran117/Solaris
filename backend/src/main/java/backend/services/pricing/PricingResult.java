package backend.services.pricing;

import backend.models.enums.TaxSource;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable pricing quote. All amounts scale 2, HALF_UP.
 * {@code finalTotal = max(0, subtotal − promotionSavings − couponSavings) + shippingAmount + taxAmount}
 * where {@code shippingAmount} is already net of any FREE_SHIPPING reduction ({@link #shippingSavings}),
 * and {@code taxAmount} is computed on the post-discount taxable base (plus taxable shipping).
 *
 * @param lines              per-line breakdown, same order as input
 * @param appliedPromotions  rules that fired, in application order (useful for UI chips)
 * @param subtotal           sum of quantity * unitBasePrice across lines, pre-discount
 * @param promotionSavings   sum of line-level savings from PromotionRule-driven discounts
 * @param couponSavings      additional saving from the redeemed coupon (0 if none)
 * @param appliedCouponCode  echo of the coupon code when one was successfully applied, else null
 * @param shippingAmount     shipping cost the customer pays, after FREE_SHIPPING reductions
 * @param shippingSavings    total reduction applied to the shipping line by FREE_SHIPPING rules
 * @param taxableAmount      base tax was applied to (post-discount subtotal + taxable shipping)
 * @param taxRate            fractional tax rate applied (0 when no tax)
 * @param taxAmount          sales tax added to the total (0 when no tax)
 * @param taxSource          why the rate was chosen ({@code NONE} when no destination/tax)
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
        BigDecimal taxableAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        TaxSource taxSource,
        BigDecimal finalTotal,
        List<String> warnings
) {}
