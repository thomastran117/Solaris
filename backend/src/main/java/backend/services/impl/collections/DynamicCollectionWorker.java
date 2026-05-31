package backend.services.impl.collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import backend.models.core.Collection;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import backend.repositories.CollectionRepository;

import java.util.List;

/**
 * Periodically re-materialises {@code DYNAMIC} collections so their AUTO members reflect the
 * current product catalog.
 *
 * <p>Pattern matches {@link backend.services.impl.products.ProductSchedulingWorker} —
 * scheduled poll, paged work, defensive try/catch per item so one bad rule can't poison the
 * whole pass. Heavy lifting (rule eval, ES reindex events, cache eviction) lives on
 * {@link CollectionServiceImpl#materialiseDynamicMembers(Collection)} so the admin "Refresh"
 * button and the worker share one code path.
 */
@Component
public class DynamicCollectionWorker {

    private static final Logger log = LoggerFactory.getLogger(DynamicCollectionWorker.class);

    private final CollectionRepository collectionRepository;
    private final CollectionServiceImpl collectionService;
    private final TransactionTemplate transactionTemplate;

    public DynamicCollectionWorker(
            CollectionRepository collectionRepository,
            CollectionServiceImpl collectionService,
            TransactionTemplate transactionTemplate) {
        this.collectionRepository = collectionRepository;
        this.collectionService = collectionService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Fixed-delay schedule defaults to 5 minutes. Tune via
     * {@code app.collection.materialization.interval-ms}; lower values increase load on the
     * product table without much shopper benefit.
     */
    @Scheduled(fixedDelayString = "${app.collection.materialization.interval-ms:300000}")
    public void refreshDynamicCollections() {
        List<Collection> dueCollections = collectionRepository
                .findAllByTypeAndStatus(CollectionType.DYNAMIC, CollectionStatus.ACTIVE);
        if (dueCollections.isEmpty()) return;

        int refreshed = 0;
        for (Collection c : dueCollections) {
            try {
                // Each collection runs in its own transaction so a slow or failing one doesn't
                // hold up the others or rollback the entire batch.
                transactionTemplate.executeWithoutResult(status -> {
                    Collection reloaded = collectionRepository.findById(c.getId()).orElse(null);
                    if (reloaded == null
                            || reloaded.getType() != CollectionType.DYNAMIC
                            || reloaded.getStatus() != CollectionStatus.ACTIVE) {
                        return;
                    }
                    collectionService.materialiseDynamicMembers(reloaded);
                    collectionRepository.save(reloaded);
                });
                refreshed++;
            } catch (Exception e) {
                log.warn("[COLLECTION WORKER] Failed to refresh collection {}: {}",
                        c.getId(), e.getMessage());
            }
        }
        if (refreshed > 0) {
            log.info("[COLLECTION WORKER] Refreshed {} dynamic collection(s)", refreshed);
        }
    }
}
