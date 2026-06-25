package backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class MoneyUtilTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static BigDecimal sum(List<BigDecimal> xs) {
        return xs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void allocate_sumsExactlyToTotal_lastLineAbsorbsDrift() {
        List<BigDecimal> shares = MoneyUtil.allocateProportionally(
                bd("10.00"), List.of(bd("1"), bd("1"), bd("1")));
        assertEquals(bd("3.33"), shares.get(0));
        assertEquals(bd("3.33"), shares.get(1));
        assertEquals(bd("3.34"), shares.get(2));
        assertEquals(0, sum(shares).compareTo(bd("10.00")));
    }

    @Test
    void allocate_proportionalToWeights() {
        List<BigDecimal> shares = MoneyUtil.allocateProportionally(
                bd("9.00"), List.of(bd("100"), bd("200")));
        assertEquals(bd("3.00"), shares.get(0));
        assertEquals(bd("6.00"), shares.get(1));
        assertEquals(0, sum(shares).compareTo(bd("9.00")));
    }

    @Test
    void allocate_zeroWeightLinesReceiveZero_lastPositiveAbsorbsDrift() {
        List<BigDecimal> shares = MoneyUtil.allocateProportionally(
                bd("5.00"), List.of(bd("0"), bd("1"), bd("0"), bd("1")));
        assertEquals(0, shares.get(0).compareTo(BigDecimal.ZERO));
        assertEquals(0, shares.get(2).compareTo(BigDecimal.ZERO));
        assertEquals(bd("2.50"), shares.get(1));
        assertEquals(bd("2.50"), shares.get(3));
        assertEquals(0, sum(shares).compareTo(bd("5.00")));
    }

    @Test
    void allocate_zeroTotal_allZero() {
        List<BigDecimal> shares = MoneyUtil.allocateProportionally(
                BigDecimal.ZERO, List.of(bd("1"), bd("2")));
        assertEquals(2, shares.size());
        assertEquals(0, sum(shares).compareTo(BigDecimal.ZERO));
    }

    @Test
    void allocate_allZeroWeights_allZero() {
        List<BigDecimal> shares = MoneyUtil.allocateProportionally(
                bd("10.00"), List.of(bd("0"), bd("0")));
        assertEquals(2, shares.size());
        assertEquals(0, sum(shares).compareTo(BigDecimal.ZERO));
    }

    @Test
    void allocate_emptyWeights_returnsEmpty() {
        assertTrue(MoneyUtil.allocateProportionally(bd("10.00"), List.of()).isEmpty());
    }

    @Test
    void shouldConvertBigDecimalToCentsHalfUp() {
        assertEquals(1299L, MoneyUtil.toCents(new BigDecimal("12.99")));
        assertEquals(1000L, MoneyUtil.toCents(new BigDecimal("10.00")));
        // 0.005 rounds half-up to 0.01 => 1 cent
        assertEquals(1L, MoneyUtil.toCents(new BigDecimal("0.005")));
    }

    @Test
    void shouldTreatNullAmountAsZeroCents() {
        assertEquals(0L, MoneyUtil.toCents(null));
    }

    @Test
    void shouldConvertCentsToScaleTwoBigDecimal() {
        assertEquals(new BigDecimal("12.99"), MoneyUtil.fromCents(1299L));
        assertEquals(new BigDecimal("0.00"), MoneyUtil.fromCents(0L));
        assertEquals(new BigDecimal("100.00"), MoneyUtil.fromCents(10000L));
    }

    @Test
    void shouldRoundTripCentsThroughBigDecimal() {
        for (long cents : new long[] {0, 1, 99, 100, 4999, 123456}) {
            assertEquals(cents, MoneyUtil.toCents(MoneyUtil.fromCents(cents)));
        }
    }
}
