package backend.models.enums;

/** Final verdict returned by the risk engine. The caller maps each action to a concrete flow decision. */
public enum RiskAction {
    ALLOW,
    VERIFY,
    BLOCK
}
