package backend.models.enums;

/** Per-signal classification returned by a {@code RiskRuleEvaluator}. Aggregated into {@link RiskAction}. */
public enum RiskDecision {
    LOW,
    MEDIUM,
    HIGH,
    /** Signal is temporarily unavailable (e.g. stubbed/disabled) — contributes 0 score and surfaces a warning. */
    NEUTRAL
}
