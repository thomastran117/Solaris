package backend.services.impl.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import backend.repositories.ProductRepository;
import backend.services.intf.inventory.DemandService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Pre-warms the Redis hot-product caches for all companies that have had
 * at least one PAID product order in the last 24 hours. Runs on a fixed
 * delay (default 5 minutes) so that API calls always hit a warm cache.
 *
 * The refresh interval is configurable via DEMAND_REFRESH_INTERVAL_MS.
 */
@Component
public class DemandTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemandTrackingScheduler.class);

    private final ProductRepository productRepository;
    private final DemandService     demandService;

    public DemandTrackingScheduler(ProductRepository productRepository, DemandService demandService) {
        this.productRepository = productRepository;
        this.demandService     = demandService;
    }

    @Scheduled(fixedDelayString = "${app.demand.refresh-interval-ms:300000}")
    public void refreshHotProductCaches() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<UUID> activeCompanies = productRepository.findDistinctCompanyIdsWithPaidOrdersSince(since);

        for (UUID companyId : activeCompanies) {
            try {
                demandService.refreshCache(companyId, "1h");
            } catch (Exception e) {
                log.warn("[DEMAND] 1h refresh failed for company {}: {}", companyId, e.getMessage());
            }
            try {
                demandService.refreshCache(companyId, "24h");
            } catch (Exception e) {
                log.warn("[DEMAND] 24h refresh failed for company {}: {}", companyId, e.getMessage());
            }
        }

        if (!activeCompanies.isEmpty()) {
            log.info("[DEMAND] Pre-warmed hot-product caches for {} active company/companies",
                    activeCompanies.size());
        }
    }
}
