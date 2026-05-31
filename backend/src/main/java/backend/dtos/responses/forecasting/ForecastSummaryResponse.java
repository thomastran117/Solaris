package backend.dtos.responses.forecasting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForecastSummaryResponse(
        UUID companyId,
        int windowDays,
        Instant computedAt,
        int productCount,
        int productsNeedingReorder,
        int productsWithImminentStockout,
        List<ProductForecastResponse> items
) {}
