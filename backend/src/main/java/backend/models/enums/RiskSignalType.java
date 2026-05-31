package backend.models.enums;

/** Signal family emitted by a single {@code RiskRuleEvaluator}, plus engine-level signals. */
public enum RiskSignalType {
    FAILED_PAYMENT_VELOCITY,
    SHIPPING_IP_COUNTRY_MISMATCH,
    COUPON_ABUSE,
    MULTI_ACCOUNT_DEVICE_VELOCITY,
    RETURN_PATTERN,
    /** Synthesized by the engine when a RiskBlocklist entry short-circuits the assessment. */
    BLOCKLIST
}
