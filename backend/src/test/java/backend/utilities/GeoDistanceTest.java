package backend.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoDistanceTest {

    private static final double DELTA = 0.5; // km tolerance

    // ── haversineKm ───────────────────────────────────────────────────────────

    @Test
    void samePoint_returnsZero() {
        assertEquals(0.0, GeoDistance.haversineKm(51.5074, -0.1278, 51.5074, -0.1278), DELTA);
    }

    @Test
    void londonToNewYork_approximately5570km() {
        double dist = GeoDistance.haversineKm(51.5074, -0.1278, 40.7128, -74.0060);
        assertEquals(5570.0, dist, 20.0);
    }

    @Test
    void londonToParis_approximately340km() {
        double dist = GeoDistance.haversineKm(51.5074, -0.1278, 48.8566, 2.3522);
        assertEquals(340.0, dist, 10.0);
    }

    @Test
    void sydneyToMelbourne_approximately713km() {
        double dist = GeoDistance.haversineKm(-33.8688, 151.2093, -37.8136, 144.9631);
        assertEquals(713.0, dist, 15.0);
    }

    @Test
    void antipodal_approximatelyHalfCircumference() {
        double dist = GeoDistance.haversineKm(0, 0, 0, 180);
        // half earth circumference ≈ 20015 km
        assertEquals(20015.0, dist, 50.0);
    }

    @Test
    void northToSouth_correctDistance() {
        // equator to north pole ≈ 10007 km
        double dist = GeoDistance.haversineKm(0, 0, 90, 0);
        assertEquals(10007.0, dist, 20.0);
    }

    // ── shippingDaysForDistance ───────────────────────────────────────────────

    @Test
    void under50km_returns1To2Days() {
        assertArrayEquals(new int[]{1, 2}, GeoDistance.shippingDaysForDistance(0));
        assertArrayEquals(new int[]{1, 2}, GeoDistance.shippingDaysForDistance(49));
        assertArrayEquals(new int[]{1, 2}, GeoDistance.shippingDaysForDistance(1.5));
    }

    @Test
    void boundary50km_returns2To3Days() {
        assertArrayEquals(new int[]{2, 3}, GeoDistance.shippingDaysForDistance(50));
    }

    @Test
    void under250km_returns2To3Days() {
        assertArrayEquals(new int[]{2, 3}, GeoDistance.shippingDaysForDistance(100));
        assertArrayEquals(new int[]{2, 3}, GeoDistance.shippingDaysForDistance(249));
    }

    @Test
    void boundary250km_returns3To5Days() {
        assertArrayEquals(new int[]{3, 5}, GeoDistance.shippingDaysForDistance(250));
    }

    @Test
    void under1000km_returns3To5Days() {
        assertArrayEquals(new int[]{3, 5}, GeoDistance.shippingDaysForDistance(500));
        assertArrayEquals(new int[]{3, 5}, GeoDistance.shippingDaysForDistance(999));
    }

    @Test
    void boundary1000km_returns5To8Days() {
        assertArrayEquals(new int[]{5, 8}, GeoDistance.shippingDaysForDistance(1000));
    }

    @Test
    void under4000km_returns5To8Days() {
        assertArrayEquals(new int[]{5, 8}, GeoDistance.shippingDaysForDistance(2000));
        assertArrayEquals(new int[]{5, 8}, GeoDistance.shippingDaysForDistance(3999));
    }

    @Test
    void boundary4000km_returns8To14Days() {
        assertArrayEquals(new int[]{8, 14}, GeoDistance.shippingDaysForDistance(4000));
    }

    @Test
    void over4000km_returns8To14Days() {
        assertArrayEquals(new int[]{8, 14}, GeoDistance.shippingDaysForDistance(10000));
        assertArrayEquals(new int[]{8, 14}, GeoDistance.shippingDaysForDistance(20000));
    }

    // ── fallbackShippingDays ──────────────────────────────────────────────────

    @Test
    void fallback_returns3To7Days() {
        assertArrayEquals(new int[]{3, 7}, GeoDistance.fallbackShippingDays());
    }
}
