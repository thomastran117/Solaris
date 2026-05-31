package backend.services.intf.inventory;

import backend.models.enums.AllocationStrategy;

import java.util.UUID;
import java.util.List;

public interface AllocationService {

    /**
     * Allocates stock across one or more inventory locations for a single order item.
     *
     * <p>Must be called inside an existing {@code @Transactional} context — it participates
     * in the caller's transaction rather than opening its own.
     *
     * <p>Returns an empty list when no location stock records exist for the item (silent skip,
     * matching current behavior). Returns one or more {@link AllocationResult} entries on
     * success; multiple entries indicate a split fulfillment across locations.
     *
     * @param productId  the product being allocated
     * @param variantId  null for product-level stock, non-null for variant-level
     * @param qty        total units required
     * @param strategy   allocation preference
     * @param buyerLat   buyer latitude (required only for NEAREST; null triggers fallback)
     * @param buyerLng   buyer longitude (required only for NEAREST; null triggers fallback)
     * @return list of allocation results; empty means no location stock is available
     */
    List<AllocationResult> allocate(
            UUID productId,
            UUID variantId,
            int qty,
            AllocationStrategy strategy,
            Double buyerLat,
            Double buyerLng);

    /**
     * Immutable result for one location's share of a fulfillment allocation.
     *
     * @param locationStockId the {@code LocationStock.id} that was atomically decremented
     * @param location        the hydrated {@code InventoryLocation} entity
     * @param allocatedQty    units decremented from this location
     */
    record AllocationResult(
            UUID locationStockId,
            backend.models.core.InventoryLocation location,
            int allocatedQty) {}
}
