package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.models.enums.RiskSignalType;
import backend.repositories.FailedPaymentAttemptRepository;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Flags users whose payments keep failing — a classic card-tester / stolen-card signal.
 *
 * <p>Two windows are checked independently and the worst result wins:
 * <ul>
 *   <li>Per-user within {@code failedPayment.windowMinutes}: medium at {@code mediumCount},
 *       high at {@code highCount}.</li>
 *   <li>Per-IP within {@code failedPayment.ipWindowHours}: high at {@code ipHighCount}
 *       (IP-level fan-out is almost always bot traffic).</li>
 * </ul>
 */
@Component
public class FailedPaymentVelocityEvaluator implements RiskRuleEvaluator {

    private final FailedPaymentAttemptRepository failedPaymentRepository;
    private final RiskProperties properties;

    public FailedPaymentVelocityEvaluator(FailedPaymentAttemptRepository failedPaymentRepository,
                                          RiskProperties properties) {
        this.failedPaymentRepository = failedPaymentRepository;
        this.properties = properties;
    }

    @Override
    public RiskSignalType type() {
        return RiskSignalType.FAILED_PAYMENT_VELOCITY;
    }

    @Override
    public RiskSignal evaluate(RiskContext ctx) {
        RiskProperties.FailedPayment cfg = properties.getFailedPayment();
        Instant now = ctx.now();

        Instant userSince = now.minus(Duration.ofMinutes(cfg.getWindowMinutes()));
        long userFails = failedPaymentRepository.countByUserIdAndCreatedAtAfter(ctx.userId(), userSince);

        long ipFails = 0;
        if (ctx.clientIp() != null && !ctx.clientIp().isBlank()) {
            Instant ipSince = now.minus(Duration.ofHours(cfg.getIpWindowHours()));
            ipFails = failedPaymentRepository.countByIpAndCreatedAtAfter(ctx.clientIp(), ipSince);
        }

        if (ipFails >= cfg.getIpHighCount()) {
            return RiskSignal.high(type(), 60,
                    "IP has " + ipFails + " failed payments in last " + cfg.getIpWindowHours() + "h");
        }
        if (userFails >= cfg.getHighCount()) {
            return RiskSignal.high(type(), 55,
                    "User has " + userFails + " failed payments in last " + cfg.getWindowMinutes() + "min");
        }
        if (userFails >= cfg.getMediumCount()) {
            return RiskSignal.medium(type(), 30,
                    "User has " + userFails + " failed payments in last " + cfg.getWindowMinutes() + "min");
        }
        return RiskSignal.low(type(), "Failed-payment velocity within normal range");
    }
}
