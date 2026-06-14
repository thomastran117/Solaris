package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.ProductVariant;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, java.util.UUID> {
    Optional<ProductVariant> findByIdAndProductId(java.util.UUID id, java.util.UUID productId);
    Optional<ProductVariant> findByIdAndProductCompanyId(java.util.UUID id, java.util.UUID companyId);
    List<ProductVariant> findAllByProductIdOrderByDisplayOrderAsc(java.util.UUID productId);
    boolean existsBySkuAndProductCompanyId(String sku, java.util.UUID companyId);
    boolean existsByProductId(java.util.UUID productId);

    /**
     * Atomically decrements variant stock. Returns 1 on success, 0 if stock is tracked but < qty.
     * NULL stock (untracked inventory) always succeeds — the decrement is a no-op (NULL - qty = NULL).
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.stock = v.stock - :qty WHERE v.id = :id AND (v.stock IS NULL OR v.stock >= :qty)")
    int decrementStock(@Param("id") java.util.UUID id, @Param("qty") int qty);

    /**
     * Restores variant stock (used in compensation/cancel flows). Caps the result at maxStock when
     * a ceiling is configured, mirroring {@code ProductRepository.restoreStock}, so compensation
     * cannot over-restore a variant above its configured maximum. Untracked (null stock) variants
     * are skipped.
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.stock = CASE WHEN v.maxStock IS NOT NULL AND (v.stock + :qty) > v.maxStock THEN v.maxStock ELSE v.stock + :qty END WHERE v.id = :id AND v.stock IS NOT NULL")
    int restoreStock(@Param("id") java.util.UUID id, @Param("qty") int qty);

    /**
     * Adjusts variant stock by delta. Returns 1 on success, 0 if result would be negative.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductVariant v SET v.stock = v.stock + :delta WHERE v.id = :id AND v.stock IS NOT NULL AND (v.stock + :delta) >= 0")
    int adjustStock(@Param("id") java.util.UUID id, @Param("delta") int delta);
}
