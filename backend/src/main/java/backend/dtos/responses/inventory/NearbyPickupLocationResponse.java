package backend.dtos.responses.inventory;

import backend.models.enums.LocationType;

import java.util.UUID;

/** A STORE or HYBRID inventory location returned by the nearby-pickup endpoint, sorted by distance. */
public record NearbyPickupLocationResponse(
        UUID id,
        String name,
        String code,
        String address,
        String city,
        String country,
        double distanceKm,
        Integer pickupReadyHours,
        LocationType type
) {}
