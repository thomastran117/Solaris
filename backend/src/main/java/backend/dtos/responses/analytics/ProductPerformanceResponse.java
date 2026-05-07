package backend.dtos.responses.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductPerformanceResponse(
        long companyId,
        int lookbackDays,
        Instant computedAt,
        List<ProductPerfEntry> products
) {
    public record ProductPerfEntry(
            long productId,
            String productName,
            String sku,
            BigDecimal currentRevenue,
            long currentUnits,
            BigDecimal priorRevenue,
            long priorUnits,
            double revenueGrowthPercent,
            double unitsGrowthPercent,
            int revenueRank,
            int unitsRank
    ) {}
}
