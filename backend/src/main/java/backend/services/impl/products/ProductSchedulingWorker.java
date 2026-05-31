package backend.services.impl.products;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import backend.events.ProductIndexEvent;
import backend.models.core.Product;
import backend.models.enums.ProductStatus;
import backend.repositories.ProductRepository;
import backend.services.impl.SingleFlightCache;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Promotes products from {@link ProductStatus#SCHEDULED} to {@link ProductStatus#ACTIVE} as
 * their {@code scheduledPublishAt} window opens.
 *
 * <p>The catalog filter at {@code ProductSpecification} and the Elasticsearch query in
 * {@code ProductServiceImpl#searchMarketplaceCatalog} both pin {@code status=ACTIVE}, so a
 * scheduled product is invisible to customers until this worker flips it. After the flip we
 * publish a {@link ProductIndexEvent} so the customer ES index is refreshed in the same
 * pipeline used by manual updates, and we evict the relevant single-flight cache keys.
 *
 * <p>Interval is configurable via {@code app.product.scheduling.interval-ms} (default 60s).
 * The query is index-backed by {@code idx_products_status_scheduled_publish_at}; we page
 * through results in batches so a backlog of due products doesn't lock the table.
 */
@Component
public class ProductSchedulingWorker {

    private static final Logger log = LoggerFactory.getLogger(ProductSchedulingWorker.class);
    private static final int BATCH_SIZE = 100;

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SingleFlightCache singleFlightCache;

    public ProductSchedulingWorker(
            ProductRepository productRepository,
            ApplicationEventPublisher eventPublisher,
            SingleFlightCache singleFlightCache) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.singleFlightCache = singleFlightCache;
    }

    @Scheduled(fixedDelayString = "${app.product.scheduling.interval-ms:60000}")
    public void publishDueProducts() {
        Instant now = Instant.now();
        int total = 0;
        while (true) {
            List<Product> flipped = flipNextBatch(now);
            if (flipped.isEmpty()) {
                break;
            }
            total += flipped.size();
            for (Product p : flipped) {
                UUID companyId = p.getCompany().getId();
                eventPublisher.publishEvent(new ProductIndexEvent(p, companyId));
                evictCachesSafely(companyId, p.getId(), p.getMarketplaceId());
                log.info("[PRODUCT SCHEDULE] Published product id={} companyId={} (was scheduled for {})",
                        p.getId(), companyId, p.getScheduledPublishAt());
            }
            if (flipped.size() < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("[PRODUCT SCHEDULE] Activated {} scheduled product(s) at {}", total, now);
        }
    }

    /**
     * Fetches the next batch of due products and flips them inside a single transaction.
     * The {@code scheduledPublishAt} field is intentionally cleared by
     * {@code ProductServiceImpl#applyStatusTransition}, mirrored here so the worker stays
     * the source of truth for the SCHEDULED→ACTIVE step.
     */
    @Transactional
    protected List<Product> flipNextBatch(Instant now) {
        Page<Product> page = productRepository.findDueForPublishing(
                ProductStatus.SCHEDULED, now, PageRequest.of(0, BATCH_SIZE));
        List<Product> products = page.getContent();
        for (Product p : products) {
            p.setStatus(ProductStatus.ACTIVE);
            p.setScheduledPublishAt(null);
            if (p.getPublishedAt() == null) {
                p.setPublishedAt(now);
            }
        }
        return products;
    }

    /**
     * Mirrors the cache eviction pattern used by {@code ProductServiceImpl#updateProduct}.
     * Redis failures must never propagate per the project Redis convention — degrade silently.
     */
    private void evictCachesSafely(UUID companyId, UUID productId, UUID marketplaceId) {
        try {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            if (marketplaceId != null) {
                singleFlightCache.evict("marketplace:product:" + marketplaceId + ":" + productId);
                singleFlightCache.evictByPattern("marketplace:search:" + marketplaceId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + marketplaceId + ":*");
            }
        } catch (Exception e) {
            log.warn("[PRODUCT SCHEDULE] Cache eviction failed for productId={}: {}", productId, e.getMessage());
        }
    }
}
