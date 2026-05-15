package backend.services.impl.pricing;

import backend.configurations.environment.RiskProperties;
import backend.models.core.RiskBlocklist;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskBlocklistType;
import backend.models.enums.RiskDecision;
import backend.models.enums.RiskSignalType;
import backend.repositories.RiskBlocklistRepository;
import backend.services.intf.auth.EmailVerificationService;
import backend.services.intf.pricing.RiskEngine;
import backend.services.risk.RiskAssessmentResult;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import backend.services.risk.evaluators.RiskRuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orders the per-signal evaluators behind a few short-circuits:
 *
 * <ol>
 *   <li>Kill switch — {@code app.risk.enabled=false} returns ALLOW (and a warning).</li>
 *   <li>VIP allowlist — membership in {@code app.risk.vip-segment-id} returns ALLOW.</li>
 *   <li>Blocklist — hit on email / IP / device fingerprint returns BLOCK before any
 *       evaluator runs (saves DB load and stops engineering-free manual blocks).</li>
 *   <li>Evaluators — each runs inside a try/catch so one misbehaving signal can't fail
 *       the whole engine. A thrown evaluator becomes a NEUTRAL signal + warning.</li>
 *   <li>Threshold mapping — summed score maps to ALLOW / VERIFY / BLOCK.</li>
 *   <li>VERIFY-on-checkout triggers a step-up email dispatch as a side-effect
 *       (the token never leaks into the API response; the user retrieves it from email).</li>
 * </ol>
 *
 * <p>The result is side-effect free for the caller's DB state: persisting the assessment
 * and flipping order status is the caller's responsibility.
 */
@Service
public class RiskEngineImpl implements RiskEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskEngineImpl.class);

    private final RiskBlocklistRepository blocklistRepository;
    private final EmailVerificationService emailVerificationService;
    private final RiskProperties properties;
    private final Map<RiskSignalType, RiskRuleEvaluator> evaluators;

    public RiskEngineImpl(RiskBlocklistRepository blocklistRepository,
                          EmailVerificationService emailVerificationService,
                          RiskProperties properties,
                          List<RiskRuleEvaluator> evaluatorList) {
        this.blocklistRepository = blocklistRepository;
        this.emailVerificationService = emailVerificationService;
        this.properties = properties;

        Map<RiskSignalType, RiskRuleEvaluator> map = new EnumMap<>(RiskSignalType.class);
        for (RiskRuleEvaluator ev : evaluatorList) {
            map.put(ev.type(), ev);
        }
        this.evaluators = map;
    }

    @Override
    @Transactional(readOnly = true)
    public RiskAssessmentResult assess(RiskContext ctx) {
        List<RiskSignal> signals = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!properties.isEnabled()) {
            warnings.add("Risk engine disabled (kill switch)");
            return RiskAssessmentResult.allow(0, signals, warnings);
        }

        if (isVip(ctx)) {
            warnings.add("VIP allowlist hit (segment=" + properties.getVipSegmentId() + ")");
            return RiskAssessmentResult.allow(0, signals, warnings);
        }

        Optional<RiskBlocklist> blockHit = findBlocklistHit(ctx);
        if (blockHit.isPresent()) {
            RiskBlocklist b = blockHit.get();
            signals.add(RiskSignal.high(
                    RiskSignalType.BLOCKLIST,
                    1000,
                    "Blocklist hit on " + b.getType() + ": " + b.getReason()));
            warnings.add("Blocklist hit (" + b.getType() + ")");
            return RiskAssessmentResult.block(1000, signals, warnings);
        }

        int totalScore = 0;
        for (RiskRuleEvaluator evaluator : evaluators.values()) {
            RiskSignal signal = safeEvaluate(evaluator, ctx, warnings);
            signals.add(signal);
            if (signal.decision() != RiskDecision.NEUTRAL) {
                totalScore += Math.max(0, signal.scoreContribution());
            }
        }

        RiskAction action = resolveAction(totalScore);

        if (action == RiskAction.VERIFY && ctx.userEmail() != null && !ctx.userEmail().isBlank()) {
            try {
                emailVerificationService.initiateVerification(ctx.userId(), ctx.userEmail());
            } catch (RuntimeException ex) {
                log.warn("Failed to dispatch risk step-up email to userId={}", ctx.userId(), ex);
                warnings.add("Step-up email dispatch failed: " + ex.getClass().getSimpleName());
            }
        }

        return switch (action) {
            case ALLOW -> RiskAssessmentResult.allow(totalScore, signals, warnings);
            case VERIFY -> RiskAssessmentResult.verify(totalScore, signals, warnings);
            case BLOCK -> RiskAssessmentResult.block(totalScore, signals, warnings);
        };
    }

    /**
     * VIP carve-out is intentionally fail-closed: when {@code app.risk.vip-segment-id}
     * is unset (default 0) we return false so the engine runs every signal against
     * every user. The inverse would be dangerous — silently exempting segment 0 (or
     * any random unconfigured segment) from fraud checks. Operators who want a
     * carve-out must set the property explicitly to a positive segment id.
     */
    private boolean isVip(RiskContext ctx) {
        long vipId = properties.getVipSegmentId();
        if (vipId <= 0) return false;
        Set<Long> segments = ctx.userSegmentIds();
        return segments != null && segments.contains(vipId);
    }

    private Optional<RiskBlocklist> findBlocklistHit(RiskContext ctx) {
        // Email → IP → fingerprint. First hit wins; we don't need to collect them all.
        if (ctx.userEmail() != null && !ctx.userEmail().isBlank()) {
            Optional<RiskBlocklist> hit = blocklistRepository.findActive(
                    RiskBlocklistType.EMAIL, ctx.userEmail().toLowerCase(), ctx.now());
            if (hit.isPresent()) return hit;
        }
        if (ctx.clientIp() != null && !ctx.clientIp().isBlank()) {
            Optional<RiskBlocklist> hit = blocklistRepository.findActive(
                    RiskBlocklistType.IP, ctx.clientIp(), ctx.now());
            if (hit.isPresent()) return hit;
        }
        if (ctx.deviceFingerprint() != null && !ctx.deviceFingerprint().isBlank()) {
            Optional<RiskBlocklist> hit = blocklistRepository.findActive(
                    RiskBlocklistType.DEVICE_FINGERPRINT, ctx.deviceFingerprint(), ctx.now());
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }

    private RiskSignal safeEvaluate(RiskRuleEvaluator evaluator, RiskContext ctx, List<String> warnings) {
        try {
            RiskSignal signal = evaluator.evaluate(ctx);
            if (signal == null) {
                warnings.add(evaluator.type() + " returned null; treated as NEUTRAL");
                return RiskSignal.neutral(evaluator.type(), "Evaluator returned null");
            }
            return signal;
        } catch (RuntimeException ex) {
            // R2-M5: in SHADOW mode fail-open is fine (we're just observing). In ENFORCE
            // a broken evaluator silently dropping its score contribution is exactly the
            // risk the fraud engine exists to prevent — bias toward verification rather
            // than letting the order glide through. A MEDIUM-weight signal nudges marginal
            // orders into VERIFY without auto-blocking benign traffic during an outage.
            boolean enforce = properties.getMode() == backend.models.enums.RiskMode.ENFORCE;
            log.warn("Risk evaluator {} threw (mode={}); failing {}", evaluator.type(),
                    properties.getMode(), enforce ? "to MEDIUM" : "open", ex);
            warnings.add(evaluator.type() + " threw " + ex.getClass().getSimpleName()
                    + (enforce ? "; failing to MEDIUM" : "; fail-open"));
            if (enforce) {
                return RiskSignal.medium(evaluator.type(), 15,
                        "Evaluator " + evaluator.type() + " unavailable: " + ex.getClass().getSimpleName());
            }
            return RiskSignal.neutral(evaluator.type(), "Evaluator error: " + ex.getClass().getSimpleName());
        }
    }

    private RiskAction resolveAction(int score) {
        if (score >= properties.getBlockThreshold()) return RiskAction.BLOCK;
        if (score >= properties.getVerifyThreshold()) return RiskAction.VERIFY;
        return RiskAction.ALLOW;
    }
}
