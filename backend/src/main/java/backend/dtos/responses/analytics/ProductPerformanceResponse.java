package backend.dtos.responses.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductPerformanceResponse(
        UUID companyId,
        int lookbackDays,
        Instant computedAt,
        List<ProductPerfEntry> products
) {
    public record ProductPerfEntry(
            UUID productId,
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
