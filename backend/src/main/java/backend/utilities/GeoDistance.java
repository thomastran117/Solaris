package backend.utilities;

/**
 * Pure-static geographic distance helpers. No external deps.
 *
 * <p>Used by the availability/delivery estimate service to rank locations and bucket the
 * great-circle distance into a delivery SLA range.</p>
 */
public final class GeoDistance {

    /** Mean Earth radius in km, sufficient for storefront-grade distance estimates. */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoDistance() {}

    /**
     * Great-circle distance between two points in kilometres.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Distance-bucketed shipping SLA. Returns a [minDays, maxDays] range exclusive of
     * handling time — callers add {@code handlingDays} to both bounds.
     *
     * <p>Buckets:
     * <ul>
     *   <li>&lt; 50 km    → 1–2 days</li>
     *   <li>&lt; 250 km   → 2–3 days</li>
     *   <li>&lt; 1000 km  → 3–5 days</li>
     *   <li>&lt; 4000 km  → 5–8 days</li>
     *   <li>≥ 4000 km    → 8–14 days</li>
     * </ul>
     * </p>
     */
    public static int[] shippingDaysForDistance(double distanceKm) {
        if (distanceKm < 50)   return new int[]{1, 2};
        if (distanceKm < 250)  return new int[]{2, 3};
        if (distanceKm < 1000) return new int[]{3, 5};
        if (distanceKm < 4000) return new int[]{5, 8};
        return new int[]{8, 14};
    }

    /**
     * Fallback SLA when distance is unknown (no buyer coords or no location coords).
     * Conservative middle-of-the-road bucket.
     */
    public static int[] fallbackShippingDays() {
        return new int[]{3, 7};
    }
}
