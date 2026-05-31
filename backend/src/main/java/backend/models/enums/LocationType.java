package backend.models.enums;

public enum LocationType {
    /** Ships only — no in-person pickup. */
    WAREHOUSE,
    /** Pickup only — does not ship. */
    STORE,
    /** Ships and supports in-person pickup. */
    HYBRID
}
