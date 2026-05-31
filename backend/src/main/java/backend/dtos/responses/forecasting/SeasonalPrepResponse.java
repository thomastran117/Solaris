package backend.dtos.responses.forecasting;

import java.util.UUID;

public record SeasonalPrepResponse(
        UUID productId,
        String productName,
        String sku,
        Integer currentStock,
        double avgDailyLast28,
        double avgDailyYoY,
        double yoyRatio,
        Trend trend
) {
    public enum Trend {
        RAMPING_UP,
        COOLING_DOWN,
        STABLE
    }
}
