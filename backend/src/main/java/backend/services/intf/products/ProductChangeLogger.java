package backend.services.intf.products;

import java.util.UUID;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.enums.ChangeSource;

/**
 * Records per-field audit rows for product and variant edits into {@code product_change_log}.
 *
 * <p>Implementations are expected to:
 * <ul>
 *   <li>Compare the supplied before/after snapshots for a fixed allowlist of tracked fields.</li>
 *   <li>Skip {@code stock} (already recorded in {@code inventory_adjustments}).</li>
 *   <li>Persist one row per actually-changed field within the caller's transaction.</li>
 * </ul>
 */
public interface ProductChangeLogger {

    void logCreate(Product saved, ChangeSource source);

    void logUpdate(Product before, Product after, ChangeSource source, UUID revertedFromLogId);

    void logDelete(Product before);

    void logVariantCreate(ProductVariant saved, ChangeSource source);

    void logVariantUpdate(ProductVariant before, ProductVariant after, ChangeSource source, UUID revertedFromLogId);

    void logVariantDelete(ProductVariant before);

    /**
     * Returns a transient {@link Product} whose tracked fields equal the source's at call time.
     * Callers capture this before mutating an attached entity, then pass it to
     * {@link #logUpdate} after {@code save()}.
     */
    Product snapshot(Product source);

    /** See {@link #snapshot(Product)}. */
    ProductVariant snapshot(ProductVariant source);
}
