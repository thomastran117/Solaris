package backend.services.intf.pricing;

import backend.services.risk.RiskAssessmentResult;
import backend.services.risk.RiskContext;

/**
 * Scores a checkout or return attempt against the configured risk evaluators and returns
 * a 3-way action (ALLOW / VERIFY / BLOCK) plus the per-signal breakdown for audit.
 *
 * <p>The engine is side-effect free: it does <em>not</em> persist assessment rows, flip
 * order statuses, or send verification emails on its own — callers (OrderServiceImpl,
 * ReturnServiceImpl) are responsible for those steps. This keeps the engine trivial to
 * unit-test and lets the same result shape be reused across flows.
 */
public interface RiskEngine {

    /** Evaluates {@code ctx} and returns the aggregate verdict. Never returns null. */
    RiskAssessmentResult assess(RiskContext ctx);
}
