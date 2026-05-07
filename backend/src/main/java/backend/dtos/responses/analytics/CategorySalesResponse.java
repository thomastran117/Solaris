package backend.dtos.responses.analytics;

import java.math.BigDecimal;
import java.util.List;

public record CategorySalesResponse(
        long companyId,
        int lookbackDays,
        List<CategoryEntry> categories
) {
    public record CategoryEntry(
            String category,
            BigDecimal totalRevenue,
            long totalUnits,
            long orderCount,
            double revenueSharePercent
    ) {}
}
