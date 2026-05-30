package backend.services.impl.inventory;

import backend.models.core.LocationStock;
import backend.models.enums.AllocationStrategy;
import backend.repositories.LocationStockRepository;
import backend.services.intf.inventory.AllocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AllocationServiceImpl implements AllocationService {

    private static final Logger log = LoggerFactory.getLogger(AllocationServiceImpl.class);
    private static final int MAX_CANDIDATES = 50;

    private final LocationStockRepository locationStockRepository;

    public AllocationServiceImpl(LocationStockRepository locationStockRepository) {
        this.locationStockRepository = locationStockRepository;
    }

    @Override
    public List<AllocationResult> allocate(
            UUID productId, UUID variantId, int qty,
            AllocationStrategy strategy, Double buyerLat, Double buyerLng) {
        List<LocationStock> candidates = fetchCandidates(productId, variantId, strategy, buyerLat, buyerLng);
        if (candidates.isEmpty()) {
            return List.of();
        }
        return attemptAllocation(candidates, qty);
    }

    private List<LocationStock> fetchCandidates(
            UUID productId, UUID variantId,
            AllocationStrategy strategy, Double buyerLat, Double buyerLng) {

        UUID variantRef = variantId;
        var page = PageRequest.of(0, MAX_CANDIDATES);

        return switch (strategy) {
            case HIGHEST_STOCK -> fetchHighestStock(productId, variantId, variantRef, page);

            case NEAREST -> {
                if (buyerLat == null || buyerLng == null) {
                    yield fetchHighestStock(productId, variantId, variantRef, page);
                }
                List<LocationStock> distanceSorted = (variantId != null)
                        ? locationStockRepository.findByVariantOrderedByDistance(
                                productId, variantRef, buyerLat, buyerLng, page)
                        : locationStockRepository.findByProductOrderedByDistance(
                                productId, buyerLat, buyerLng, page);
                // Fall back to HIGHEST_STOCK when no location has coordinates configured.
                yield distanceSorted.isEmpty()
                        ? fetchHighestStock(productId, variantId, variantRef, page)
                        : distanceSorted;
            }

            case CHEAPEST -> {
                List<LocationStock> costSorted = (variantId != null)
                        ? locationStockRepository.findByVariantOrderedByCost(productId, variantRef, page)
                        : locationStockRepository.findByProductOrderedByCost(productId, page);
                // Fall back to HIGHEST_STOCK when no location has fulfillmentCost set.
                yield costSorted.isEmpty()
                        ? fetchHighestStock(productId, variantId, variantRef, page)
                        : costSorted;
            }
        };
    }

    private List<LocationStock> fetchHighestStock(
            UUID productId, UUID variantId, UUID variantRef,
            org.springframework.data.domain.Pageable page) {
        return (variantId != null)
                ? locationStockRepository.findTopByVariantStockDesc(productId, variantRef, page)
                : locationStockRepository.findTopByProductStockDesc(productId, page);
    }

    /**
     * Attempts to fulfill {@code totalQty} units by draining candidates in order.
     * Supports split fulfillment — multiple locations contribute when no single location
     * has enough stock.
     *
     * <p>If the full quantity cannot be satisfied across all candidates (e.g., total stock
     * is insufficient), any partial decrements are restored and an empty list is returned
     * so the caller can throw a ConflictException consistently.
     */
    private List<AllocationResult> attemptAllocation(List<LocationStock> candidates, int totalQty) {
        List<AllocationResult> results = new ArrayList<>();
        int remaining = totalQty;

        for (LocationStock ls : candidates) {
            if (remaining <= 0) break;

            // Optimistic read of available stock; the atomic decrement enforces the true guard.
            int toTake = Math.min(remaining, ls.getStock());
            if (toTake <= 0) continue;

            int decremented = locationStockRepository.decrementStock(ls.getId(), toTake);
            if (decremented == 0) {
                // Race condition — another transaction took the stock; skip to next candidate.
                continue;
            }

            remaining -= toTake;
            results.add(new AllocationResult(ls.getId(), ls.getLocation(), toTake));
        }

        if (remaining > 0) {
            // Could not fulfill the full quantity. Restore any partial decrements; the outer
            // transaction will also roll back, but we restore eagerly for correctness.
            for (AllocationResult r : results) {
                try {
                    locationStockRepository.restoreStock(r.locationStockId(), r.allocatedQty());
                } catch (Exception e) {
                    // Outer transaction rollback is the safety net, but log explicitly so
                    // a rollback failure doesn't leave an invisible stock leak.
                    log.error("[ALLOC] Failed to restore {} units for locationStock {} during partial-fill rollback: {}",
                            r.allocatedQty(), r.locationStockId(), e.getMessage());
                }
            }
            return List.of();
        }

        return results;
    }
}
