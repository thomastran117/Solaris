package backend.services.pricing;

/**
 * Single source of truth for normalising tax jurisdiction fields. Save-time (admin create),
 * lookup-time (resolve) and snapshot-time (order creation) must all agree exactly or the
 * most-specific-match lookup silently breaks, so every call site uses these helpers.
 *
 * <p>Country and state are ISO codes: trimmed, uppercased, and reduced to {@code ""} (the wildcard)
 * when they are not exactly two letters — so a full name like "Texas" resolves to the country-level
 * default rather than being truncated to a meaningless "TE".
 */
public final class TaxJurisdiction {

    private TaxJurisdiction() {
    }

    /** ISO-2 country/state code, or {@code ""} when not a clean 2-letter code. */
    public static String iso2(String value) {
        if (value == null) return "";
        String v = value.trim().toUpperCase();
        return v.length() == 2 ? v : "";
    }

    /** Trimmed postal code; {@code ""} (wildcard) when null/blank. */
    public static String postal(String value) {
        return value == null ? "" : value.trim();
    }
}
