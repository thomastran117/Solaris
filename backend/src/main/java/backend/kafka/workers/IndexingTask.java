package backend.kafka.workers;

import backend.models.core.Product;
import backend.models.core.ProductBundle;

import java.util.UUID;

/**
 * Sealed work-item type submitted to the ProductIndexingService queue.
 * Each variant represents one indexing or removal operation for a product or bundle.
 */
sealed interface IndexingTask
        permits IndexingTask.IndexProduct, IndexingTask.RemoveProduct,
                IndexingTask.IndexBundle, IndexingTask.RemoveBundle {

    /**
     * companyId is captured from the entity while it is still loaded in the calling
     * JPA session — this prevents LazyInitializationException in worker threads.
     */
    record IndexProduct(Product product, UUID companyId) implements IndexingTask {}

    record RemoveProduct(UUID productId) implements IndexingTask {}

    record IndexBundle(ProductBundle bundle) implements IndexingTask {}

    record RemoveBundle(UUID bundleId) implements IndexingTask {}
}
