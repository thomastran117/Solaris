package backend.dtos.responses.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Settlement report for the admin payout-attribution endpoint. One {@link Row} per
 * funding company across the requested window, plus period totals.
 *
 * @param from                window start (null = unbounded)
 * @param to                  window end (null = unbounded)
 * @param rows                aggregates per funding company, ordered by totalSavings DESC
 * @param grandTotalSavings   sum of {@code totalSavings} across all rows
 * @param totalRedemptions    sum of {@code redemptionCount} across all rows
 */
public record PayoutAttributionResponse(
        Instant from,
        Instant to,
        List<Row> rows,
        BigDecimal grandTotalSavings,
        long totalRedemptions
) {
    public record Row(
            UUID fundedByCompanyId,
            String companyName,
            BigDecimal totalSavings,
            long redemptionCount,
            long uniqueOrderCount
    ) {}
}
