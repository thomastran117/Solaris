package backend.services.intf.analytics;

import java.util.UUID;
import backend.dtos.responses.forecasting.ForecastSummaryResponse;
import backend.dtos.responses.forecasting.ProductForecastResponse;
import backend.dtos.responses.forecasting.ReorderSuggestionResponse;
import backend.dtos.responses.forecasting.SeasonalPrepSummaryResponse;

import java.util.List;

public interface ForecastingService {

    /**
     * Returns a company-wide demand forecast for all tracked products.
     *
     * @param lookbackDays number of past days of PAID order history to use (14–365)
     * @param limit        maximum number of products to include in the response
     */
    ForecastSummaryResponse getCompanyForecast(UUID companyId, UUID ownerId, int lookbackDays, int limit);

    /**
     * Returns a single-product demand forecast scoped to the given company.
     *
     * @param lookbackDays number of past days of PAID order history to use (14–365)
     */
    ProductForecastResponse getProductForecast(UUID companyId, UUID productId, UUID ownerId, int lookbackDays);

    /**
     * Returns products that need restocking, ranked by urgency.
     *
     * @param lookbackDays number of past days used to estimate daily velocity
     * @param limit        maximum number of suggestions to return
     */
    List<ReorderSuggestionResponse> getReorderSuggestions(UUID companyId, UUID ownerId, int lookbackDays, int limit);

    /**
     * Compares current 28-day demand to the same calendar window one year ago.
     * Returns {@code SeasonalPrepSummaryResponse.insufficientHistory()} when less
     * than one year of data is available.
     *
     * @param limit maximum number of products to include
     */
    SeasonalPrepSummaryResponse getSeasonalPrep(UUID companyId, UUID ownerId, int limit);
}
