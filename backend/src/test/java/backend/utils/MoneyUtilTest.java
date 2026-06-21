package backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyUtilTest {

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
