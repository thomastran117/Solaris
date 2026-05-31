package backend.services.risk.evaluators;

import backend.configurations.environment.RiskProperties;
import backend.models.enums.RiskSignalType;
import backend.repositories.UserDeviceRepository;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Detects one device fingerprint being used by many accounts — the tell-tale pattern
 * for promo-abuse rings and account-farming. Combines three heuristics:
 *
 * <ul>
 *   <li>Distinct users ever seen on this fingerprint → the headline signal.</li>
 *   <li>Account burst on this fingerprint in the last {@code burstWindowMinutes} → +15.</li>
 *   <li>Account was created less than {@code accountAge.newMinutes} ago → +10.</li>
 * </ul>
 *
 * Missing fingerprint returns NEUTRAL (we can't evaluate the signal, but we don't want
 * anonymous traffic to pile on score for no reason).
 */
@Component
public class MultiAccountDeviceVelocityEvaluator implements RiskRuleEvaluator {

    private final UserDeviceRepository userDeviceRepository;
    private final RiskProperties properties;

    public MultiAccountDeviceVelocityEvaluator(UserDeviceRepository userDeviceRepository,
                                               RiskProperties properties) {
        this.userDeviceRepository = userDeviceRepository;
        this.properties = properties;
    }

    @Override
    public RiskSignalType type() {
        return RiskSignalType.MULTI_ACCOUNT_DEVICE_VELOCITY;
    }

    @Override
    public RiskSignal evaluate(RiskContext ctx) {
        String fp = ctx.deviceFingerprint();
        if (fp == null || fp.isBlank()) {
            return RiskSignal.neutral(type(), "No device fingerprint captured");
        }

        RiskProperties.Device dev = properties.getDevice();
        RiskProperties.AccountAge age = properties.getAccountAge();

        long distinctUsers = userDeviceRepository.countDistinctUserIdByFingerprint(fp);
        Instant burstSince = ctx.now().minus(Duration.ofMinutes(dev.getBurstWindowMinutes()));
        long burstCount = userDeviceRepository.countByFingerprintAndCreatedAtAfter(fp, burstSince);

        boolean isNewAccount = ctx.userCreatedAt() != null
                && ctx.userCreatedAt().isAfter(ctx.now().minus(Duration.ofMinutes(age.getNewMinutes())));
        boolean isBursting = burstCount >= 2;

        int bonus = (isBursting ? 15 : 0) + (isNewAccount ? 10 : 0);
        String bonusReason = (isBursting ? " + burst=" + burstCount : "")
                + (isNewAccount ? " + new account" : "");

        if (distinctUsers >= dev.getDistinctUsersHigh()) {
            return RiskSignal.high(type(), 50 + bonus,
                    "Fingerprint shared by " + distinctUsers + " users" + bonusReason);
        }
        if (distinctUsers >= dev.getDistinctUsersMedium()) {
            return RiskSignal.medium(type(), 20 + bonus,
                    "Fingerprint shared by " + distinctUsers + " users" + bonusReason);
        }
        if (bonus > 0) {
            // Not cross-account, but new + bursting is still worth a small nudge.
            return RiskSignal.medium(type(), bonus, "Device" + bonusReason);
        }
        return RiskSignal.low(type(), "Device history within normal range");
    }
}
