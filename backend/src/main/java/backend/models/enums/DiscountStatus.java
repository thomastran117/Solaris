package backend.models.enums;

public enum DiscountStatus {
    ACTIVE,
    DISABLED,
    /** Computed at response time when endDate is non-null and in the past. Never stored in the database. */
    EXPIRED
}
