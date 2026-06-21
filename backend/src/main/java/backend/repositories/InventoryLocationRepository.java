package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.InventoryLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, java.util.UUID> {

    List<InventoryLocation> findAllByCompanyIdOrderByDisplayOrderAscNameAsc(java.util.UUID companyId);

    /** The company's primary active location (lowest displayOrder, then name). Used as the shipping origin. */
    Optional<InventoryLocation> findFirstByCompanyIdAndActiveTrueOrderByDisplayOrderAscNameAsc(java.util.UUID companyId);

    Optional<InventoryLocation> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    boolean existsByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    boolean existsByCodeAndCompanyId(String code, java.util.UUID companyId);

    boolean existsByCodeAndCompanyIdAndIdNot(String code, java.util.UUID companyId, java.util.UUID excludeId);

    /**
     * Returns STORE and HYBRID locations for a company ordered by Haversine distance from
     * the buyer's coordinates. Only active locations with non-null lat/lng are included.
     * Columns: id, name, code, address, city, country, pickup_ready_hours, type, distance_km.
     */
    @Query(nativeQuery = true, value =
            "SELECT loc.id, loc.name, loc.code, loc.address, loc.city, loc.country, " +
            "       loc.pickup_ready_hours, loc.type, " +
            "       ASIN(SQRT(" +
            "         POW(SIN(RADIANS(loc.latitude - :lat) / 2), 2) + " +
            "         COS(RADIANS(:lat)) * COS(RADIANS(loc.latitude)) * " +
            "         POW(SIN(RADIANS(loc.longitude - :lng) / 2), 2)" +
            "       )) * 12742 AS distance_km " +
            "FROM inventory_locations loc " +
            "WHERE loc.company_id = :companyId " +
            "  AND loc.active = true " +
            "  AND loc.type IN ('STORE', 'HYBRID') " +
            "  AND loc.latitude IS NOT NULL " +
            "  AND loc.longitude IS NOT NULL " +
            "ORDER BY distance_km ASC " +
            "LIMIT :limit")
    List<Object[]> findNearbyPickupLocations(
            @Param("companyId") UUID companyId,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("limit") int limit);
}
