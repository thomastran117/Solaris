package backend.services.impl.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import backend.repositories.ProductRepository;
import backend.services.intf.analytics.CompanyAnalyticsService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Dedicated worker that precomputes common product analytics queries into Redis
 * for all companies with recent order activity. Runs on a fixed delay (default
 * 30 minutes), separate from {@link backend.services.impl.inventory.DemandTrackingScheduler}
 * which handles real-time hot-product demand tracking.
 *
 * Each company's data is precomputed independently so a failure for one company
 * does not block the rest.
 */
@Component
public class ProductAnalyticsWorker {

    private static final Logger log = LoggerFactory.getLogger(ProductAnalyticsWorker.class);

    private final CompanyAnalyticsService companyAnalyticsService;
    private final ProductRepository       productRepository;

    public ProductAnalyticsWorker(
            CompanyAnalyticsService companyAnalyticsService,
            ProductRepository productRepository) {
        this.companyAnalyticsService = companyAnalyticsService;
        this.productRepository       = productRepository;
    }

    @Scheduled(fixedDelayString = "${app.analytics.product.refresh-interval-ms:1800000}")
    public void refreshProductAnalytics() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Long> activeCompanyIds =
                productRepository.findDistinctCompanyIdsWithPaidOrdersSince(since);

        if (activeCompanyIds.isEmpty()) {
            return;
        }

        log.info("[ANALYTICS] Precomputing product analytics for {} active company/companies",
                activeCompanyIds.size());

        int success = 0;
        for (Long companyId : activeCompanyIds) {
            try {
                companyAnalyticsService.precomputeAll(companyId);
                success++;
            } catch (Exception e) {
                log.warn("[ANALYTICS] Precompute failed for company {}: {}", companyId, e.getMessage());
            }
        }

        log.info("[ANALYTICS] Precompute complete: {}/{} companies refreshed",
                success, activeCompanyIds.size());
    }
}
