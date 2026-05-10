package backend.dtos.responses.inventory;

/**
 * Pickup availability at a STORE / HYBRID location near the buyer.
 * Null at the response level when no eligible pickup location is in range.
 */
public record PickupOptionResponse(
        long locationId,
        String name,
        String city,
        Double distanceKm,
        Integer readyHours
) {}
