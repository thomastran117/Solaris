package backend.dtos.responses.analytics;

import java.math.BigDecimal;
import java.util.List;

public record SlowMoversResponse(
        long companyId,
        int days,
        List<SlowMoverEntry> items
) {
    public record SlowMoverEntry(
            long productId,
            String productName,
            String sku,
            Integer currentStock,
            BigDecimal price,
            String currency,
            long unitsSold,
            BigDecimal revenue,
            double dailyVelocity,
            boolean neverSold
    ) {}
}
