package backend.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.LocationStock;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationStockRepository extends JpaRepository<LocationStock, java.util.UUID> {

    List<LocationStock> findAllByLocationId(java.util.UUID locationId);

    List<LocationStock> findAllByProductId(java.util.UUID productId);

    Optional<LocationStock> findByLocationIdAndProductIdAndVariantRef(
            java.util.UUID locationId, java.util.UUID productId, java.util.UUID variantRef);

    @Query("SELECT ls FROM LocationStock ls WHERE ls.product.id = :productId " +
           "AND ls.location.company.id = :companyId ORDER BY ls.location.displayOrder ASC, ls.location.name ASC")
    List<LocationStock> findAllByProductIdAndCompanyId(
            @Param("productId") java.util.UUID productId, @Param("companyId") java.util.UUID companyId);

    /**
     * Picks the best-stocked active location for a product (product-level stock).
     * JOIN FETCH avoids a lazy-load inside the order transaction.
     */
    @Query("SELECT ls FROM LocationStock ls JOIN FETCH ls.location loc " +
           "WHERE ls.product.id = :productId AND ls.variantRef IS NULL " +
           "AND loc.active = true AND ls.stock > 0 ORDER BY ls.stock DESC")
    List<LocationStock> findTopByProductStockDesc(@Param("productId") java.util.UUID productId, Pageable pageable);

    /**
     * Picks the best-stocked active location for a specific variant.
     * JOIN FETCH avoids a lazy-load inside the order transaction.
     */
    @Query("SELECT ls FROM LocationStock ls JOIN FETCH ls.location loc " +
           "WHERE ls.product.id = :productId AND ls.variantRef = :variantRef " +
           "AND loc.active = true AND ls.stock > 0 ORDER BY ls.stock DESC")
    List<LocationStock> findTopByVariantStockDesc(
            @Param("productId") java.util.UUID productId,
            @Param("variantRef") java.util.UUID variantRef,
            Pageable pageable);

    /**
     * All active, stocked locations for a product (product-level stock), sorted by the
     * admin-curated displayOrder. Used when the buyer has no coordinates so we can't sort
     * by distance. JOIN FETCH avoids lazy-loads when mapping to the response.
     */
    @Query("SELECT ls FROM LocationStock ls JOIN FETCH ls.location loc " +
           "WHERE ls.product.id = :productId AND ls.variantRef IS NULL " +
           "AND loc.active = true AND ls.stock > 0 " +
           "ORDER BY loc.displayOrder ASC, loc.name ASC")
    List<LocationStock> findStockedByProduct(@Param("productId") java.util.UUID productId);

    /**
     * All active, stocked locations for a specific variant, sorted by displayOrder.
     * No-coords companion to {@link #findByVariantOrderedByDistance}.
     */
    @Query("SELECT ls FROM LocationStock ls JOIN FETCH ls.location loc " +
           "WHERE ls.product.id = :productId AND ls.variantRef = :variantRef " +
           "AND loc.active = true AND ls.stock > 0 " +
           "ORDER BY loc.displayOrder ASC, loc.name ASC")
    List<LocationStock> findStockedByVariant(
            @Param("productId") java.util.UUID productId,
            @Param("variantRef") java.util.UUID variantRef);

    /** Picks active locations for a product ordered by Haversine distance to buyer (product-level stock). */
    @Query("SELECT ls FROM LocationStock ls JOIN FETCH ls.location loc " +
           "WHERE ls.product.id = :productId AND ls.variantRef IS NULL " +
           "AND loc.active = true AND ls.stock > 0 " +
           "AND loc.latitude IS NOT NULL AND loc.longitude IS NOT NULL " +
           "ORDER BY FUNCTION('ASIN', FUNCTION('SQRT', " +
           "  FUNCTION('POWER', FUNCTION('SIN', FUNCTION('RADIANS', loc.latitude - :buyerLat) / 2), 2) + " +
           "  FUNCTION('COS', FUNCTION('RADIANS', :buyerLat)) * FUNCTION('COS', FUNCTION('RADIANS', loc.latitude)) * " +
           "  FUNCTION('POWER', FUNCTION('SIN', FUNCTION('RADIANS', loc.longitude - :buyerLng) / 2), 2) " +
           ")) ASC")
    List<LocationStock> findByProductOrderedByDistance(
            @Param("productId") java.util.UUID productId,
            @Param("buyerLat") double buyerLat,
            @Param("buyerLng") double buyerLng,
            Pageable pageable);

    /** Picks active locations for a variant ordered by Haversine distance to buyer (variant-level stock). */
    @Query("SELECT ls FROM LocationStock ls JOIN FETCH ls.location loc " +
           "WHERE ls.product.id = :productId AND ls.variantRef = :variantRef " +
           "AND loc.active = true AND ls.stock > 0 " +
           "AND loc.latitude IS NOT NULL AND loc.longitude IS NOT NULL " +
           "ORDER BY FUNCTION('ASIN', FUNCTION('SQRT', " +
           "  FUNCTION('POWER', FUNCTION('SIN', FUNCTION('RADIANS', loc.latitude - :buyerLat) / 2), 2) + " +
           "  FUNCTION('COS', FUNCTION('RADIANS', :buyerLat)) * FUNCTION('COS', FUNCTION('RADIANS', loc.latitude)) * " +
           "  FUNCTION('POWER', FUNCTION('SIN', FUNCTION('RADIANS', loc.longitude - :buyerLng) / 2), 2) " +
           ")) ASC")
    List<LocationStock> findByVariantOrderedByDistance(
            @Param("productId") java.util.UUID productId,
            @Param("variantRef") java.util.UUID variantRef,
            @Param("buyerLat") double buyerLat,
            @Param("buyerLng") double buyerLng,
            Pageable pageable);

    /** Picks active locations for a product ordered by fulfillmentCost ASC (product-level stock). */
    @Query("SELECT ls FROM LocationStock ls JOIN FETCH ls.location loc " +
           "WHERE ls.product.id = :productId AND ls.variantRef IS NULL " +
           "AND loc.active = true AND ls.stock > 0 " +
           "AND loc.fulfillmentCost IS NOT NULL " +
           "ORDER BY loc.fulfillmentCost ASC, ls.stock DESC")
    List<LocationStock> findByProductOrderedByCost(
            @Param("productId") java.util.UUID productId,
            Pageable pageable);

    /** Picks active locations for a variant ordered by fulfillmentCost ASC (variant-level stock). */
    @Query("SELECT ls FROM LocationStock ls JOIN FETCH ls.location loc " +
           "WHERE ls.product.id = :productId AND ls.variantRef = :variantRef " +
           "AND loc.active = true AND ls.stock > 0 " +
           "AND loc.fulfillmentCost IS NOT NULL " +
           "ORDER BY loc.fulfillmentCost ASC, ls.stock DESC")
    List<LocationStock> findByVariantOrderedByCost(
            @Param("productId") java.util.UUID productId,
            @Param("variantRef") java.util.UUID variantRef,
            Pageable pageable);

    /** Guards deletion: true only when at least one record still has stock above zero. */
    boolean existsByLocationIdAndStockGreaterThan(java.util.UUID locationId, int stock);

    /**
     * Atomically decrements stock. Returns 1 on success (stock >= qty), 0 on failure.
     */
    @Modifying
    @Query("UPDATE LocationStock ls SET ls.stock = ls.stock - :qty " +
           "WHERE ls.id = :id AND ls.stock >= :qty")
    int decrementStock(@Param("id") java.util.UUID id, @Param("qty") int qty);

    /**
     * Unconditional restore — used in cancel/compensation flows.
     */
    @Modifying
    @Query("UPDATE LocationStock ls SET ls.stock = ls.stock + :qty WHERE ls.id = :id")
    int restoreStock(@Param("id") java.util.UUID id, @Param("qty") int qty);

    /**
     * Signed delta adjustment with negative-stock guard.
     * Returns 1 on success, 0 if result would be negative.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE LocationStock ls SET ls.stock = ls.stock + :delta " +
           "WHERE ls.id = :id AND (ls.stock + :delta) >= 0")
    int adjustStock(@Param("id") java.util.UUID id, @Param("delta") int delta);

    /**
     * Direct set — used by setLocationStock to replace stock and threshold entirely.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE LocationStock ls SET ls.stock = :stock, ls.lowStockThreshold = :threshold " +
           "WHERE ls.id = :id")
    int setStock(@Param("id") java.util.UUID id, @Param("stock") int stock,
                 @Param("threshold") Integer threshold);
}
