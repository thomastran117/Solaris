package backend.dtos.responses.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CompanyRevenueSummaryResponse(
        UUID companyId,
        int lookbackDays,
        Instant from,
        Instant to,
        BigDecimal totalRevenue,
        long totalOrders,
        BigDecimal avgOrderValue,
        List<DailyRevPoint> daily
) {
    public record DailyRevPoint(
            LocalDate day,
            BigDecimal totalRevenue,
            long totalUnits,
            long orderCount
    ) {}
}
