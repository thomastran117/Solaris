package backend.services.impl.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import backend.repositories.ProductRepository;
import backend.services.intf.CacheService;
import backend.services.intf.analytics.CompanyAnalyticsService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
    private static final String LOCK_KEY    = "analytics:product:refresh:lock";
    private static final long   LOCK_TTL_S  = 25 * 60L; // 25 min — just under the 30-min schedule

    private final CompanyAnalyticsService companyAnalyticsService;
    private final ProductRepository       productRepository;
    private final CacheService            cacheService;

    // Stable per-instance identity used as the lock owner token.
    private final String instanceId = UUID.randomUUID().toString();

    public ProductAnalyticsWorker(
            CompanyAnalyticsService companyAnalyticsService,
            ProductRepository productRepository,
            CacheService cacheService) {
        this.companyAnalyticsService = companyAnalyticsService;
        this.productRepository       = productRepository;
        this.cacheService            = cacheService;
    }

    @Scheduled(fixedDelayString = "${app.analytics.product.refresh-interval-ms:1800000}")
    public void refreshProductAnalytics() {
        // Distributed mutex: only one pod runs the precomputation per cycle.
        // tryLock is a Redis SET NX EX, so concurrent instances both attempt to
        // acquire but only the winner proceeds; the loser skips silently.
        try {
            if (!cacheService.tryLock(LOCK_KEY, instanceId, LOCK_TTL_S)) {
                log.debug("[ANALYTICS] Skipping precompute — another instance holds the lock");
                return;
            }
        } catch (Exception e) {
            log.warn("[ANALYTICS] Lock acquisition failed, proceeding without distributed lock: {}", e.getMessage());
        }

        try {
            Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
            List<UUID> activeCompanyIds =
                    productRepository.findDistinctCompanyIdsWithPaidOrdersSince(since);

            if (activeCompanyIds.isEmpty()) {
                return;
            }

            log.info("[ANALYTICS] Precomputing product analytics for {} active company/companies",
                    activeCompanyIds.size());

            int success = 0;
            for (UUID companyId : activeCompanyIds) {
                try {
                    companyAnalyticsService.precomputeAll(companyId);
                    success++;
                } catch (Exception e) {
                    log.warn("[ANALYTICS] Precompute failed for company {}: {}", companyId, e.getMessage());
                }
            }

            log.info("[ANALYTICS] Precompute complete: {}/{} companies refreshed",
                    success, activeCompanyIds.size());
        } finally {
            try {
                cacheService.unlock(LOCK_KEY, instanceId);
            } catch (Exception e) {
                log.warn("[ANALYTICS] Failed to release precompute lock: {}", e.getMessage());
            }
        }
    }
}
