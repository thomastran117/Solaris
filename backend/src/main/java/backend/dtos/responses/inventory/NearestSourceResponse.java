package backend.dtos.responses.inventory;

/**
 * Where the product would ship from for this buyer.
 * {@code distanceKm} is null when the buyer's coordinates are not provided
 * or the location lacks lat/lng.
 */
public record NearestSourceResponse(
        long locationId,
        String name,
        String city,
        String country,
        Double distanceKm
) {}
