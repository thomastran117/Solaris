package backend.dtos.responses.forecasting;

import java.time.LocalDate;
import java.util.UUID;

public record ProductForecastResponse(
        UUID productId,
        String productName,
        String sku,
        Integer currentStock,
        double avgDailyDemand,
        double predictedWeeklyDemand,
        double predictedWeeklyLow,
        double predictedWeeklyHigh,
        Double daysOfCoverage,
        LocalDate likelyStockoutDate,
        int reorderSuggestedQty,
        boolean reorderUrgent,
        double[] seasonalityFactors
) {}
