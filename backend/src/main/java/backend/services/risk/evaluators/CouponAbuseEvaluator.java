package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.models.enums.RiskSignalType;
import backend.repositories.CouponRedemptionRepository;
import backend.repositories.OrderRepository;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Catches coupon-stacking, promo-farming, and throwaway-account discount grinding.
 *
 * <ul>
 *   <li>Per-user redemptions in the last 24h ≥ {@code coupon.perUser24hHigh} → HIGH (+45).</li>
 *   <li>Per-IP redemptions in the last 24h ≥ {@code coupon.perIp24hHigh} → HIGH (+35) —
 *       catches ring patterns where one IP rotates across freshly-made accounts.</li>
 *   <li>First-ever order for this user + coupon discount ≥ {@code firstOrderPctThreshold}%
 *       of the order total → MEDIUM (+25). A single modest discount is fine; it's the
 *       combination that's signal.</li>
 * </ul>
 *
 * No coupon applied → NEUTRAL (evaluator is a no-op, not a reason to reduce score).
 */
@Component
public class CouponAbuseEvaluator implements RiskRuleEvaluator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CouponRedemptionRepository redemptionRepository;
    private final OrderRepository orderRepository;
    private final RiskProperties properties;

    public CouponAbuseEvaluator(CouponRedemptionRepository redemptionRepository,
                                OrderRepository orderRepository,
                                RiskProperties properties) {
        this.redemptionRepository = redemptionRepository;
        this.orderRepository = orderRepository;
        this.properties = properties;
    }

    @Override
    public RiskSignalType type() {
        return RiskSignalType.COUPON_ABUSE;
    }

    @Override
    public RiskSignal evaluate(RiskContext ctx) {
        if (ctx.couponCode() == null || ctx.couponCode().isBlank()) {
            return RiskSignal.neutral(type(), "No coupon applied");
        }

        RiskProperties.Coupon cfg = properties.getCoupon();
        Instant since24h = ctx.now().minus(Duration.ofHours(24));

        long userRedemptions = redemptionRepository.countByUser_IdAndRedeemedAtAfter(ctx.userId(), since24h);
        if (userRedemptions >= cfg.getPerUser24hHigh()) {
            return RiskSignal.high(type(), 45,
                    "User redeemed " + userRedemptions + " coupons in last 24h");
        }

        if (ctx.clientIp() != null && !ctx.clientIp().isBlank()) {
            long ipRedemptions = redemptionRepository.countByIpAndRedeemedAtAfter(ctx.clientIp(), since24h);
            if (ipRedemptions >= cfg.getPerIp24hHigh()) {
                return RiskSignal.high(type(), 35,
                        "IP redeemed " + ipRedemptions + " coupons in last 24h");
            }
        }

        // R2-H8: count only orders that have moved past RESERVED. Two concurrent first
        // orders both bump countByUserId(), so the previous {@code priorOrders <= 1}
        // heuristic let abusers slip through by submitting parallel checkouts. Counting
        // only non-RESERVED orders means a brand-new buyer with N parallel in-flight
        // orders is still treated as "first order" for every one of them — the signal
        // fires (correctly) on each.
        long priorOrders = orderRepository.countByUserIdExcludingStatus(
                ctx.userId(), backend.models.enums.OrderStatus.RESERVED);
        boolean firstOrder = priorOrders == 0;
        if (firstOrder && isDiscountPercentHigh(ctx, cfg.getFirstOrderPctThreshold())) {
            return RiskSignal.medium(type(), 25,
                    "First order with coupon ≥ " + cfg.getFirstOrderPctThreshold() + "% off");
        }

        return RiskSignal.low(type(), "Coupon usage within normal range");
    }

    private boolean isDiscountPercentHigh(RiskContext ctx, int thresholdPct) {
        BigDecimal discount = ctx.couponDiscountAmount();
        BigDecimal total = ctx.orderTotal();
        if (discount == null || total == null || total.signum() <= 0) {
            return false;
        }
        // discount / (total + discount) * 100 — total already excludes the discount.
        BigDecimal gross = total.add(discount);
        if (gross.signum() <= 0) {
            return false;
        }
        BigDecimal pct = discount.multiply(ONE_HUNDRED).divide(gross, 2, RoundingMode.HALF_UP);
        return pct.compareTo(BigDecimal.valueOf(thresholdPct)) >= 0;
    }
}
