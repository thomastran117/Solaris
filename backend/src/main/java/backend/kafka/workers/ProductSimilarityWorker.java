package backend.kafka.workers;

import backend.repositories.ProductRepository;
import backend.repositories.ProductSimilarityRepository;
import backend.services.impl.products.ProductSimilarityService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Nightly catch-up job that recomputes similarity rows for ACTIVE products
 * that either have no rows or have rows older than 7 days. Runs at 3 am.
 * Skipped in the test profile via {@code @Profile("!test")}.
 */
@Component
@Profile("!test")
public class ProductSimilarityWorker {

    private static final Logger log = LoggerFactory.getLogger(ProductSimilarityWorker.class);
    private static final int BATCH_SIZE = 100;
    private static final long BATCH_SLEEP_MS = 200L;
    private static final int STALENESS_DAYS = 7;

    private final ProductSimilarityService productSimilarityService;
    private final ProductRepository productRepository;
    private final ProductSimilarityRepository productSimilarityRepository;
    private final MeterRegistry meterRegistry;

    public ProductSimilarityWorker(
            ProductSimilarityService productSimilarityService,
            ProductRepository productRepository,
            ProductSimilarityRepository productSimilarityRepository,
            MeterRegistry meterRegistry) {
        this.productSimilarityService = productSimilarityService;
        this.productRepository = productRepository;
        this.productSimilarityRepository = productSimilarityRepository;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void recomputeAll() {
        List<UUID> companyIds = productRepository.findDistinctCompanyIdsWithActiveProducts();
        if (companyIds.isEmpty()) return;

        log.info("[SimilarityWorker] Starting nightly catch-up for {} companies", companyIds.size());
        Instant staleThreshold = Instant.now().minus(STALENESS_DAYS, ChronoUnit.DAYS);
        long missing = 0;

        for (UUID companyId : companyIds) {
            try {
                missing += processCompany(companyId, staleThreshold);
            } catch (Exception e) {
                log.warn("[SimilarityWorker] Failed processing company {}: {}", companyId, e.getMessage());
            }
        }

        meterRegistry.counter("similarity_products_missing_rows_total").increment(missing);
        log.info("[SimilarityWorker] Catch-up complete — recomputed {} product(s)", missing);
    }

    private long processCompany(UUID companyId, Instant staleThreshold) throws InterruptedException {
        long recomputed = 0;
        int page = 0;

        while (true) {
            Page<backend.models.core.Product> batch = productRepository
                    .findActiveByCompanyId(companyId, PageRequest.of(page, BATCH_SIZE));

            if (batch.isEmpty()) break;

            List<UUID> batchIds = batch.map(backend.models.core.Product::getId).toList();
            Set<UUID> freshIds = new HashSet<>(
                    productSimilarityRepository.findSourceIdsWithFreshRows(batchIds, staleThreshold));

            for (UUID productId : batchIds) {
                if (!freshIds.contains(productId)) {
                    try {
                        productSimilarityService.recompute(productId);
                        recomputed++;
                    } catch (Exception e) {
                        log.warn("[SimilarityWorker] recompute failed for product {}: {}", productId, e.getMessage());
                    }
                }
            }

            if (!batch.hasNext()) break;
            page++;
            Thread.sleep(BATCH_SLEEP_MS);
        }

        return recomputed;
    }
}
