package backend.dtos.responses.forecasting;

import java.util.List;

public record SeasonalPrepSummaryResponse(
        List<SeasonalPrepResponse> items,
        String reason
) {
    public static SeasonalPrepSummaryResponse of(List<SeasonalPrepResponse> items) {
        return new SeasonalPrepSummaryResponse(items, null);
    }

    public static SeasonalPrepSummaryResponse insufficientHistory() {
        return new SeasonalPrepSummaryResponse(List.of(), "INSUFFICIENT_HISTORY");
    }
}
