package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.Coupon;
import backend.models.enums.DiscountStatus;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, java.util.UUID> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Used when updating a coupon — checks uniqueness against other coupons. */
    boolean existsByCodeIgnoreCaseAndIdNot(String code, java.util.UUID id);

    Optional<Coupon> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    Page<Coupon> findAllByCompanyId(java.util.UUID companyId, Pageable pageable);

    /**
     * Atomically increments usedCount only when the coupon is still active, not expired,
     * and under its usage limit. Returns 1 on success, 0 if any guard fails.
     * A return value of 0 should cause the calling transaction to throw ConflictException.
     */
    @Modifying
    @Query("""
            UPDATE Coupon c SET c.usedCount = c.usedCount + 1
            WHERE c.id = :id
              AND c.status = :activeStatus
              AND (c.startDate IS NULL OR c.startDate <= :now)
              AND (c.endDate IS NULL OR c.endDate > :now)
              AND (c.maxUses IS NULL OR c.usedCount < c.maxUses)
            """)
    int tryIncrementUsedCount(@Param("id") java.util.UUID id, @Param("now") Instant now,
                              @Param("activeStatus") DiscountStatus activeStatus);

    /**
     * Bulk-deletes all coupons whose endDate has passed.
     * Called by CouponExpiryScheduler on a fixed interval.
     */
    @Modifying
    @Query("DELETE FROM Coupon c WHERE c.endDate IS NOT NULL AND c.endDate < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);
}
