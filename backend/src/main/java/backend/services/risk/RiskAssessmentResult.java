package backend.services.risk;

import backend.models.enums.RiskAction;

import java.util.List;

/**
 * Output of {@code RiskEngine.assess}. Named {@code ...Result} to avoid collision with the
 * persisted {@code backend.models.core.RiskAssessment} entity.
 *
 * <p>The engine is side-effect free: it does not write the assessment row nor flip any order.
 * Callers persist and act on this result.
 *
 * @param action       LOW→ALLOW, MEDIUM→VERIFY, HIGH→BLOCK (after threshold mapping)
 * @param totalScore   sum of {@link RiskSignal#scoreContribution} across non-NEUTRAL signals (0–100+)
 * @param signals      every signal the evaluators produced (including LOW and NEUTRAL — for audit)
 * @param warnings     non-blocking notes (e.g. "GeoIP stub", "evaluator X threw, fail-open")
 */
public record RiskAssessmentResult(
        RiskAction action,
        int totalScore,
        List<RiskSignal> signals,
        List<String> warnings
) {
    public static RiskAssessmentResult allow(int score, List<RiskSignal> signals, List<String> warnings) {
        return new RiskAssessmentResult(RiskAction.ALLOW, score, signals, warnings);
    }

    public static RiskAssessmentResult verify(int score, List<RiskSignal> signals, List<String> warnings) {
        return new RiskAssessmentResult(RiskAction.VERIFY, score, signals, warnings);
    }

    public static RiskAssessmentResult block(int score, List<RiskSignal> signals, List<String> warnings) {
        return new RiskAssessmentResult(RiskAction.BLOCK, score, signals, warnings);
    }
}
