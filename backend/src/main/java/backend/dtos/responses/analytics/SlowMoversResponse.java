package backend.dtos.responses.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SlowMoversResponse(
        UUID companyId,
        int days,
        List<SlowMoverEntry> items
) {
    public record SlowMoverEntry(
            UUID productId,
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
