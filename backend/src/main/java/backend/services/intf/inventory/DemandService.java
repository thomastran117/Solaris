package backend.services.intf.inventory;

import java.util.UUID;
import backend.dtos.responses.analytics.HotProductsResponse;

public interface DemandService {

    /**
     * Returns the hot-products ranking for the given company and time window.
     * Validates company ownership, reads from Redis cache when available,
     * falls back to a live DB query.
     *
     * @param window "1h" or "24h"
     * @param limit  number of entries to return (1–50)
     */
    HotProductsResponse getHotProducts(UUID companyId, UUID ownerId, String window, int limit);

    /**
     * Bypasses auth and directly recomputes the cache for the given company
     * and window. Called by the DemandTrackingScheduler to pre-warm caches
     * for active companies.
     *
     * @param window "1h" or "24h"
     */
    void refreshCache(UUID companyId, String window);
}
