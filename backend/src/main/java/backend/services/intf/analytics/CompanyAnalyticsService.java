package backend.services.intf.analytics;

import backend.dtos.responses.analytics.CategorySalesResponse;
import backend.dtos.responses.analytics.CompanyRevenueSummaryResponse;
import backend.dtos.responses.analytics.ProductPerformanceResponse;
import backend.dtos.responses.analytics.SlowMoversResponse;

public interface CompanyAnalyticsService {

    CompanyRevenueSummaryResponse getRevenueSummary(long companyId, long ownerId, int lookbackDays);

    CategorySalesResponse getCategorySales(long companyId, long ownerId, int lookbackDays);

    SlowMoversResponse getSlowMovers(long companyId, long ownerId, int days);

    ProductPerformanceResponse getProductPerformance(long companyId, long ownerId, int lookbackDays);

    /** Called by {@link backend.services.impl.analytics.ProductAnalyticsWorker} — no ownership check. */
    void precomputeAll(long companyId);
}
