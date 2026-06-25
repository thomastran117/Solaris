package backend.models.enums;

/**
 * Why a particular tax rate was applied — surfaced on quotes and persisted on orders so it is
 * always clear whether the rate came from a real jurisdiction match, a default, or the config
 * fallback. {@code NONE} means no tax was computed (e.g. a quote preview with no destination)
 * and doubles as the "not estimated" signal for the UI.
 */
public enum TaxSource {
    /** Exact {@code (country, state, postalCode)} row matched. */
    DESTINATION_MATCH,
    /** State-level row matched ({@code postalCode} wildcard). */
    STATE_DEFAULT,
    /** Country-level default row matched ({@code state} + {@code postalCode} wildcards). */
    COUNTRY_DEFAULT,
    /** No row matched; the configured fallback rate was used. */
    CONFIG_FALLBACK,
    /** No tax computed (no destination supplied). */
    NONE
}
