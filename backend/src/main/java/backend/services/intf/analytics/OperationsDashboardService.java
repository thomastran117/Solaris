package backend.services.intf.analytics;

import java.util.UUID;
import backend.dtos.responses.operations.CancellationMetricResponse;
import backend.dtos.responses.operations.DurationMetricResponse;
import backend.dtos.responses.operations.OperationsSummaryResponse;
import backend.dtos.responses.operations.StockoutMetricResponse;
import backend.dtos.responses.operations.SupplierLatenessMetricResponse;

/**
 * Aggregated SLA / operations metrics for a single merchant company. All
 * responses are read-only snapshots over a {@code lookbackDays} window. Caching
 * is internal to the implementation.
 */
public interface OperationsDashboardService {

    OperationsSummaryResponse        getSummary(UUID companyId, UUID ownerId, int lookbackDays);

    DurationMetricResponse           getFulfillmentMetric(UUID companyId, UUID ownerId, int lookbackDays);

    DurationMetricResponse           getRefundMetric(UUID companyId, UUID ownerId, int lookbackDays);

    DurationMetricResponse           getPickDelayMetric(UUID companyId, UUID ownerId, int lookbackDays);

    StockoutMetricResponse           getStockoutMetric(UUID companyId, UUID ownerId, int lookbackDays);

    SupplierLatenessMetricResponse   getSupplierLatenessMetric(UUID companyId, UUID ownerId, int lookbackDays);

    CancellationMetricResponse       getCancellationMetric(UUID companyId, UUID ownerId, int lookbackDays);
}
