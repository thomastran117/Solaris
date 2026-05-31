package backend.models.enums;

/**
 * Mirror of Stripe's recurring Price interval. Combined with an intervalCount
 * (e.g. 2 + WEEK = "every two weeks") to describe a billing cadence.
 */
public enum BillingInterval {
    DAY,
    WEEK,
    MONTH,
    YEAR;

    /** Stripe API string form (lowercase). */
    public String toStripe() {
        return name().toLowerCase();
    }
}
