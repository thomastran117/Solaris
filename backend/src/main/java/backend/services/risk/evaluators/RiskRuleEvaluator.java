package backend.services.risk.evaluators;

import backend.models.enums.RiskSignalType;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;

/**
 * Strategy for one {@link RiskSignalType}. Implementations are stateless and Spring-managed;
 * {@code RiskEngineImpl} collects them via constructor injection and runs them in order.
 *
 * <p>Contract: evaluators are read-only — they query repositories but never mutate state.
 * Exceptions thrown from an evaluator are caught by the engine and converted to a NEUTRAL
 * signal with a warning (fail-open per signal).
 */
public interface RiskRuleEvaluator {

    /** The signal family this evaluator produces. */
    RiskSignalType type();

    /** Returns a single {@link RiskSignal} describing the evaluator's verdict. Never null. */
    RiskSignal evaluate(RiskContext ctx);
}
