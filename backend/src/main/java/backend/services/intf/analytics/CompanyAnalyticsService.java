package backend.services.intf.analytics;

import java.util.UUID;
import backend.dtos.responses.analytics.CategorySalesResponse;
import backend.dtos.responses.analytics.CompanyRevenueSummaryResponse;
import backend.dtos.responses.analytics.ProductPerformanceResponse;
import backend.dtos.responses.analytics.SlowMoversResponse;

public interface CompanyAnalyticsService {

    CompanyRevenueSummaryResponse getRevenueSummary(UUID companyId, UUID ownerId, int lookbackDays);

    CategorySalesResponse getCategorySales(UUID companyId, UUID ownerId, int lookbackDays);

    SlowMoversResponse getSlowMovers(UUID companyId, UUID ownerId, int days);

    ProductPerformanceResponse getProductPerformance(UUID companyId, UUID ownerId, int lookbackDays);

    /** Called by {@link backend.services.impl.analytics.ProductAnalyticsWorker} — no ownership check. */
    void precomputeAll(UUID companyId);
}
