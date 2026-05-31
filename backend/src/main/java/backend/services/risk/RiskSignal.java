package backend.services.risk;

import backend.models.enums.RiskDecision;
import backend.models.enums.RiskSignalType;

/**
 * One row of a {@link RiskAssessmentResult}. Emitted by a {@code RiskRuleEvaluator}
 * after looking at a {@link RiskContext}.
 *
 * @param type              which signal family this row represents
 * @param decision          per-signal classification; {@link RiskDecision#NEUTRAL} never contributes score
 * @param scoreContribution 0 for NEUTRAL/LOW, positive for MEDIUM/HIGH; summed by the engine
 * @param reason            short merchant-facing explanation (safe to surface in the review queue)
 */
public record RiskSignal(
        RiskSignalType type,
        RiskDecision decision,
        int scoreContribution,
        String reason
) {
    public static RiskSignal neutral(RiskSignalType type, String reason) {
        return new RiskSignal(type, RiskDecision.NEUTRAL, 0, reason);
    }

    public static RiskSignal low(RiskSignalType type, String reason) {
        return new RiskSignal(type, RiskDecision.LOW, 0, reason);
    }

    public static RiskSignal medium(RiskSignalType type, int score, String reason) {
        return new RiskSignal(type, RiskDecision.MEDIUM, score, reason);
    }

    public static RiskSignal high(RiskSignalType type, int score, String reason) {
        return new RiskSignal(type, RiskDecision.HIGH, score, reason);
    }
}
