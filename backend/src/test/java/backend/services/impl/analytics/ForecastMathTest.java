package backend.services.impl.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ForecastMathTest {

    // Monday 2024-01-01
    private static final LocalDate MONDAY = LocalDate.of(2024, 1, 1);

    // -------------------------------------------------------------------------
    // mean / stddev
    // -------------------------------------------------------------------------

    @Test
    void mean_emptyArray_returnsZero() {
        assertThat(ForecastMath.mean(new long[]{})).isEqualTo(0.0);
    }

    @Test
    void mean_uniformSeries_returnsCorrectMean() {
        assertThat(ForecastMath.mean(new long[]{4, 4, 4, 4})).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void stddev_shortSeries_returnsZero() {
        assertThat(ForecastMath.stddev(new long[]{10})).isEqualTo(0.0);
    }

    @Test
    void stddev_uniformSeries_returnsZero() {
        assertThat(ForecastMath.stddev(new long[]{5, 5, 5, 5})).isCloseTo(0.0, within(1e-9));
    }

    // -------------------------------------------------------------------------
    // computeSeasonality — sparse data (< 14 days)
    // -------------------------------------------------------------------------

    @Test
    void computeSeasonality_fewerThan14Days_returnsAllOnes() {
        long[] series = new long[13];
        double[] factors = ForecastMath.computeSeasonality(series, MONDAY);
        for (double f : factors) {
            assertThat(f).isCloseTo(1.0, within(1e-9));
        }
    }

    // -------------------------------------------------------------------------
    // computeSeasonality — normal operation
    // -------------------------------------------------------------------------

    @Test
    void computeSeasonality_allZeroDemand_returnsAllOnes() {
        long[] series = new long[14];
        double[] factors = ForecastMath.computeSeasonality(series, MONDAY);
        for (double f : factors) {
            assertThat(f).isCloseTo(1.0, within(1e-9));
        }
    }

    @Test
    void computeSeasonality_allFactorsWithinBounds() {
        long[] series = new long[28];
        for (int i = 0; i < 28; i++) series[i] = (i % 7 == 4) ? 10 : 1;
        double[] factors = ForecastMath.computeSeasonality(series, MONDAY);

        for (double f : factors) {
            assertThat(f).isGreaterThanOrEqualTo(0.3 - 1e-9);
            assertThat(f).isLessThanOrEqualTo(3.0 + 1e-9);
        }
    }

    @Test
    void computeSeasonality_fridaySpike_clampedToMax() {
        long[] series = new long[28];
        for (int i = 0; i < 28; i++) series[i] = (i % 7 == 4) ? 100 : 1;
        double[] factors = ForecastMath.computeSeasonality(series, MONDAY);
        for (double f : factors) {
            assertThat(f).isLessThanOrEqualTo(3.0 + 1e-6);
        }
    }

    @Test
    void computeSeasonality_flatDemand_returnsAllOnes() {
        long[] series = new long[28];
        java.util.Arrays.fill(series, 5L);
        double[] factors = ForecastMath.computeSeasonality(series, MONDAY);
        for (double f : factors) {
            assertThat(f).isCloseTo(1.0, within(1e-6));
        }
    }

    // -------------------------------------------------------------------------
    // projectStockout
    // -------------------------------------------------------------------------

    @Test
    void projectStockout_zeroMu_returnsNull() {
        double[] flat = {1, 1, 1, 1, 1, 1, 1};
        assertThat(ForecastMath.projectStockout(10, 0.0, flat, MONDAY, 28)).isNull();
    }

    @Test
    void projectStockout_zeroStock_returnsNull() {
        double[] flat = {1, 1, 1, 1, 1, 1, 1};
        assertThat(ForecastMath.projectStockout(0, 1.0, flat, MONDAY, 28)).isNull();
    }

    @Test
    void projectStockout_stockExceedsHorizon_returnsNull() {
        double[] flat = {1, 1, 1, 1, 1, 1, 1};
        assertThat(ForecastMath.projectStockout(100, 1.0, flat, MONDAY, 28)).isNull();
    }

    @Test
    void projectStockout_exactFiveDays_returnsCorrectDate() {
        double[] flat = {1, 1, 1, 1, 1, 1, 1};
        LocalDate result = ForecastMath.projectStockout(5, 1.0, flat, MONDAY, 28);
        assertThat(result).isEqualTo(MONDAY.plusDays(4));
    }

    // -------------------------------------------------------------------------
    // computeReorderQty
    // -------------------------------------------------------------------------

    @Test
    void computeReorderQty_zeroMu_returnsZero() {
        assertThat(ForecastMath.computeReorderQty(0.0, 50, 7, 3, 7, null)).isEqualTo(0);
    }

    @Test
    void computeReorderQty_sufficientStock_returnsZero() {
        assertThat(ForecastMath.computeReorderQty(1.0, 20, 7, 3, 7, null)).isEqualTo(0);
    }

    @Test
    void computeReorderQty_insufficientStock_returnsPositive() {
        assertThat(ForecastMath.computeReorderQty(1.0, 5, 7, 3, 7, null)).isEqualTo(12);
    }

    @Test
    void computeReorderQty_autoRestockQtyLarger_snapsToAutoRestock() {
        assertThat(ForecastMath.computeReorderQty(1.0, 5, 7, 3, 7, 50)).isEqualTo(50);
    }

    @Test
    void computeReorderQty_autoRestockQtySmaller_returnsRaw() {
        assertThat(ForecastMath.computeReorderQty(1.0, 5, 7, 3, 7, 5)).isEqualTo(12);
    }

    // -------------------------------------------------------------------------
    // predictedWeekly
    // -------------------------------------------------------------------------

    @Test
    void predictedWeekly_flatSeasonality_equalsSevenTimesMu() {
        double[] flat = {1, 1, 1, 1, 1, 1, 1};
        double[] result = ForecastMath.predictedWeekly(3.0, 0.0, flat, MONDAY);
        assertThat(result[0]).isCloseTo(21.0, within(1e-6));
        assertThat(result[1]).isCloseTo(21.0, within(1e-6));
        assertThat(result[2]).isCloseTo(21.0, within(1e-6));
    }

    @Test
    void predictedWeekly_lowFloorAtZero() {
        double[] flat = {1, 1, 1, 1, 1, 1, 1};
        double[] result = ForecastMath.predictedWeekly(0.1, 100.0, flat, MONDAY);
        assertThat(result[1]).isGreaterThanOrEqualTo(0.0);
    }
}
