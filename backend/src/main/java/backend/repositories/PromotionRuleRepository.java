package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.PromotionRule;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRuleRepository extends JpaRepository<PromotionRule, java.util.UUID> {

    Optional<PromotionRule> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    Page<PromotionRule> findAllByCompanyId(java.util.UUID companyId, Pageable pageable);

    Optional<PromotionRule> findByLegacyDiscountId(long legacyDiscountId);

    boolean existsByLegacyDiscountIdIsNotNull();

    /**
     * Candidate rules for PricingEngine: ACTIVE, within time window, and either owned by one
     * of the cart's companies or marketplace-funded for that company.
     * Product-set and segment-set filtering is applied in the engine (cheaper in-memory with
     * pre-fetched associations than a complex SQL join).
     */
    @Query("""
            SELECT DISTINCT r FROM PromotionRule r
            WHERE r.company.id IN :companyIds
              AND r.status = backend.models.enums.DiscountStatus.ACTIVE
              AND (:now >= r.startDate OR r.startDate IS NULL)
              AND (:now <  r.endDate   OR r.endDate   IS NULL)
              AND (r.maxUses IS NULL OR r.usedCount < r.maxUses)
            """)
    List<PromotionRule> findActiveCandidates(
            @Param("companyIds") Collection<java.util.UUID> companyIds,
            @Param("now") Instant now);

    /**
     * Atomic increment. Returns 1 on success, 0 if the total-uses cap has been reached.
     * Callers must fail-and-rollback on a 0 response.
     */
    @Modifying
    @Query("UPDATE PromotionRule r SET r.usedCount = r.usedCount + 1 WHERE r.id = :id AND (r.maxUses IS NULL OR r.usedCount < r.maxUses)")
    int tryIncrementUsedCount(@Param("id") java.util.UUID id);

    /**
     * Reverses a previous {@link #tryIncrementUsedCount} when a promotion-bearing order is
     * cancelled or fails before a completed sale. The {@code usedCount > 0} guard keeps the
     * global usage counter from going negative under concurrent reversals.
     */
    @Modifying
    @Query("UPDATE PromotionRule r SET r.usedCount = r.usedCount - 1 WHERE r.id = :id AND r.usedCount > 0")
    void decrementUsedCount(@Param("id") java.util.UUID id);

    /**
     * Active, in-window rules for a product: either explicitly targeting the product or
     * applying to the whole company catalogue (empty targetProducts and targetBundles).
     * Bundle-scoped rules are excluded so they don't surface as product-level discounts.
     * Used by search indexing to populate {@code hasActiveDiscount} / {@code discountedPrice}.
     */
    @Query("""
            SELECT DISTINCT r FROM PromotionRule r LEFT JOIN r.targetProducts p
            WHERE r.company.id = :companyId
              AND r.status = backend.models.enums.DiscountStatus.ACTIVE
              AND (:now >= r.startDate OR r.startDate IS NULL)
              AND (:now <  r.endDate   OR r.endDate   IS NULL)
              AND (r.targetProducts IS EMPTY OR p.id = :productId)
              AND r.targetBundles IS EMPTY
            """)
    List<PromotionRule> findActiveRulesForProduct(
            @Param("companyId") java.util.UUID companyId,
            @Param("productId") java.util.UUID productId,
            @Param("now") Instant now);

    /**
     * Active, in-window rules for a bundle: either explicitly targeting the bundle or
     * applying to the whole company catalogue (empty targetProducts and targetBundles).
     * Product-scoped rules are excluded so they don't surface as bundle-level discounts.
     * Used by bundle indexing to populate {@code hasActiveDiscount} / {@code discountedPrice}.
     */
    @Query("""
            SELECT DISTINCT r FROM PromotionRule r LEFT JOIN r.targetBundles tb
            WHERE r.company.id = :companyId
              AND r.status = backend.models.enums.DiscountStatus.ACTIVE
              AND (:now >= r.startDate OR r.startDate IS NULL)
              AND (:now <  r.endDate   OR r.endDate   IS NULL)
              AND (r.targetBundles IS EMPTY OR tb.id = :bundleId)
              AND r.targetProducts IS EMPTY
            """)
    List<PromotionRule> findActiveRulesForBundle(
            @Param("companyId") java.util.UUID companyId,
            @Param("bundleId") java.util.UUID bundleId,
            @Param("now") Instant now);

    /** Bulk-deletes expired rules. Called by an expiry scheduler analogous to DiscountExpiryScheduler. */
    @Modifying
    @Query("DELETE FROM PromotionRule r WHERE r.endDate IS NOT NULL AND r.endDate < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);

    /** Cleanup when a product is deleted. */
    @Modifying
    @Query(value = "DELETE FROM promotion_rule_products WHERE product_id = :productId", nativeQuery = true)
    void removeProductFromAllRules(@Param("productId") java.util.UUID productId);

    @Modifying
    @Query(value = "DELETE FROM promotion_rule_products WHERE product_id IN :productIds", nativeQuery = true)
    void removeProductsFromAllRules(@Param("productIds") List<java.util.UUID> productIds);
}
