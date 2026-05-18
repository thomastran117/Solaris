package backend.dtos.responses.inventory;

import java.util.UUID;

/**
 * Where the product would ship from for this buyer.
 * {@code distanceKm} is null when the buyer's coordinates are not provided
 * or the location lacks lat/lng.
 */
public record NearestSourceResponse(
        UUID locationId,
        String name,
        String city,
        String country,
        Double distanceKm
) {}
