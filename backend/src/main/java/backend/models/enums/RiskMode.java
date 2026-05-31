package backend.models.enums;

/**
 * Runtime behavior switch for the risk engine.
 * <p>SHADOW: engine runs and writes assessments but the call site never blocks the order.
 * <p>ENFORCE: non-ALLOW actions are honored (VERIFY throws step-up, BLOCK holds the order).
 */
public enum RiskMode {
    SHADOW,
    ENFORCE
}
