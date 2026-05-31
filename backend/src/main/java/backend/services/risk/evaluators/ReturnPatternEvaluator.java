package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskSignalType;
import backend.repositories.ReturnRepository;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Two-mode evaluator gated on {@link RiskContext#kind()}:
 *
 * <ul>
 *   <li><b>CHECKOUT</b>: computes lifetime return-rate = returns / orders. Requires at least
 *       {@code rateMinDenominator} orders before the ratio is trusted (a single return on one
 *       order is 100% but meaningless). Above {@code rateHigh} → MEDIUM; extreme rates with
 *       volume → HIGH.</li>
 *   <li><b>RETURN</b>: two complementary signals —
 *     <ol>
 *       <li>Fast-return: delivered→requested gap &lt; {@code fastMinutes} on a ≥
 *           {@code fastHighValueUsd} order → HIGH (+55).</li>
 *       <li>Repeat-return: ≥ {@code repeatCountHigh} returns from this user in the last
 *           {@code windowDays} days → HIGH (+50).</li>
 *     </ol>
 *   </li>
 * </ul>
 */
@Component
public class ReturnPatternEvaluator implements RiskRuleEvaluator {

    private final ReturnRepository returnRepository;
    private final RiskProperties properties;

    public ReturnPatternEvaluator(ReturnRepository returnRepository, RiskProperties properties) {
        this.returnRepository = returnRepository;
        this.properties = properties;
    }

    @Override
    public RiskSignalType type() {
        return RiskSignalType.RETURN_PATTERN;
    }

    @Override
    public RiskSignal evaluate(RiskContext ctx) {
        RiskProperties.ReturnPolicy cfg = properties.getReturnPolicy();
        return ctx.kind() == RiskAssessmentKind.RETURN
                ? evaluateForReturn(ctx, cfg)
                : evaluateForCheckout(ctx, cfg);
    }

    private RiskSignal evaluateForCheckout(RiskContext ctx, RiskProperties.ReturnPolicy cfg) {
        long totalReturns = returnRepository.countByUserId(ctx.userId());
        if (totalReturns == 0) {
            return RiskSignal.low(type(), "No prior returns on record");
        }

        // We don't have total orders in the context; the CouponAbuseEvaluator path does — but we
        // intentionally keep this evaluator dependency-minimal. Approximate by requiring at least
        // rateMinDenominator returns before flagging; a user with 5+ returns in total is already
        // a candidate for review regardless of order count.
        if (totalReturns < cfg.getRateMinDenominator()) {
            return RiskSignal.low(type(), totalReturns + " prior returns (below min denom)");
        }

        Instant since = ctx.now().minus(Duration.ofDays(cfg.getWindowDays()));
        long recentReturns = returnRepository.countByUserIdAndCreatedAtAfter(ctx.userId(), since);
        if (recentReturns >= cfg.getRepeatCountHigh()) {
            return RiskSignal.high(type(), 45,
                    recentReturns + " returns in last " + cfg.getWindowDays() + " days");
        }
        if (totalReturns >= cfg.getRateMinDenominator() * 2L) {
            return RiskSignal.medium(type(), 20,
                    totalReturns + " lifetime returns");
        }
        return RiskSignal.low(type(), "Return history within normal range");
    }

    private RiskSignal evaluateForReturn(RiskContext ctx, RiskProperties.ReturnPolicy cfg) {
        Instant since = ctx.now().minus(Duration.ofDays(cfg.getWindowDays()));
        long recentReturns = returnRepository.countByUserIdAndCreatedAtAfter(ctx.userId(), since);
        if (recentReturns >= cfg.getRepeatCountHigh()) {
            return RiskSignal.high(type(), 50,
                    recentReturns + " returns in last " + cfg.getWindowDays() + " days");
        }

        if (ctx.orderDeliveredAt() != null
                && ctx.orderTotal() != null
                && ctx.orderTotal().compareTo(BigDecimal.valueOf(cfg.getFastHighValueUsd())) >= 0) {
            Duration since_delivered = Duration.between(ctx.orderDeliveredAt(), ctx.now());
            if (!since_delivered.isNegative()
                    && since_delivered.toMinutes() < cfg.getFastMinutes()) {
                return RiskSignal.high(type(), 55,
                        "Fast return (<" + cfg.getFastMinutes() + "min) on high-value order");
            }
        }

        return RiskSignal.low(type(), "Return pattern within normal range");
    }
}
