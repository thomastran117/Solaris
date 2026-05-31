package backend.services.impl.analytics;

import java.time.LocalDate;

/**
 * Pure, stateless math helpers for the forecasting engine.
 * No Spring dependencies — fully unit-testable without a context.
 */
class ForecastMath {

    private static final int    DOW_COUNT       = 7;
    private static final double MIN_FACTOR      = 0.3;
    private static final double MAX_FACTOR      = 3.0;
    private static final int    MIN_DAYS_FOR_DOW = 14;

    private ForecastMath() {}

    /**
     * Computes day-of-week seasonality factors (Mon=0 … Sun=6) from a dense daily-demand
     * series where index 0 = the oldest day and the series starts on {@code seriesStart}.
     *
     * <p>If the series has fewer than {@code MIN_DAYS_FOR_DOW} days, all factors default to 1.0
     * to avoid misleading signals on sparse data. Each factor is clamped to [{@code MIN_FACTOR},
     * {@code MAX_FACTOR}] and then re-normalised so the weekly sum equals 7 × μ.</p>
     *
     * @param series      daily units, index 0 = oldest (0-filled for days with no sales)
     * @param seriesStart the calendar date of series[0]
     * @return double[7] where [0] = Monday factor … [6] = Sunday factor
     */
    static double[] computeSeasonality(long[] series, LocalDate seriesStart) {
        if (series.length < MIN_DAYS_FOR_DOW) {
            double[] flat = new double[DOW_COUNT];
            java.util.Arrays.fill(flat, 1.0);
            return flat;
        }

        double mu = mean(series);
        if (mu <= 0) {
            double[] flat = new double[DOW_COUNT];
            java.util.Arrays.fill(flat, 1.0);
            return flat;
        }

        // Accumulate sum and count per DOW (Mon=0)
        double[] dowSum   = new double[DOW_COUNT];
        int[]    dowCount = new int[DOW_COUNT];
        for (int i = 0; i < series.length; i++) {
            // getDayOfWeek().getValue() returns 1=Mon … 7=Sun
            int dow = seriesStart.plusDays(i).getDayOfWeek().getValue() - 1;
            dowSum[dow]   += series[i];
            dowCount[dow] += 1;
        }

        double[] factors = new double[DOW_COUNT];
        for (int d = 0; d < DOW_COUNT; d++) {
            double dowMean = dowCount[d] > 0 ? dowSum[d] / dowCount[d] : mu;
            factors[d] = Math.min(MAX_FACTOR, Math.max(MIN_FACTOR, dowMean / mu));
        }

        return factors;
    }

    /**
     * Projects the first date on which cumulative demand exceeds {@code currentStock}, starting
     * from {@code startDay} and looking forward up to {@code horizonDays} days.
     *
     * @return the stockout date, or {@code null} if stock lasts beyond the horizon or μ == 0
     */
    static LocalDate projectStockout(
            int currentStock, double mu, double[] seasonality,
            LocalDate startDay, int horizonDays) {
        if (mu <= 0 || currentStock <= 0) return null;

        double remaining = currentStock;
        for (int i = 0; i < horizonDays; i++) {
            int dow = startDay.plusDays(i).getDayOfWeek().getValue() - 1;
            remaining -= mu * seasonality[dow];
            if (remaining <= 0) return startDay.plusDays(i);
        }
        return null;
    }

    /**
     * Computes how many units to reorder.
     *
     * <p>The target on-hand quantity is {@code μ × (lead + safety + review)} days.
     * If current stock already covers that target, returns 0.
     * If {@code autoRestockQty} is set and larger than the raw suggestion, it is used instead.</p>
     */
    static int computeReorderQty(
            double mu, int currentStock,
            int leadDays, int safetyDays, int reviewDays,
            Integer autoRestockQty) {
        if (mu <= 0) return 0;
        int target = (int) Math.ceil(mu * (leadDays + safetyDays + reviewDays));
        int raw    = Math.max(0, target - currentStock);
        if (autoRestockQty != null && autoRestockQty > raw) return autoRestockQty;
        return raw;
    }

    /**
     * Returns predicted weekly demand and its ±1-σ√7 confidence band.
     * Result is [predicted, low, high].
     */
    static double[] predictedWeekly(double mu, double stddev, double[] seasonality, LocalDate startDay) {
        double weekly = 0;
        for (int i = 0; i < DOW_COUNT; i++) {
            int dow = startDay.plusDays(i).getDayOfWeek().getValue() - 1;
            weekly += mu * seasonality[dow];
        }
        double margin = stddev * Math.sqrt(DOW_COUNT);
        return new double[]{weekly, Math.max(0, weekly - margin), weekly + margin};
    }

    /** Mean of a long array; returns 0 for empty arrays. */
    static double mean(long[] series) {
        if (series.length == 0) return 0;
        long sum = 0;
        for (long v : series) sum += v;
        return (double) sum / series.length;
    }

    /** Population standard deviation; returns 0 for series shorter than 2. */
    static double stddev(long[] series) {
        if (series.length < 2) return 0;
        double m = mean(series);
        double var = 0;
        for (long v : series) var += (v - m) * (v - m);
        return Math.sqrt(var / series.length);
    }
}
