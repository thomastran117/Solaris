package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.CouponPerUserCount;

import java.util.UUID;

@Repository
public interface CouponPerUserCountRepository extends JpaRepository<CouponPerUserCount, java.util.UUID> {

    /**
     * Atomically increments the per-user redemption count, inserting a new row if none exists.
     * Only increments when the current count is below {@code maxUses}.
     * Returns the number of affected rows: 1 means the increment succeeded, 0 means the
     * per-user limit has already been reached.
     */
    @Modifying
    @Query(value = """
            INSERT INTO coupon_per_user_counts (coupon_id, user_id, count)
            VALUES (:couponId, :userId, 1)
            ON DUPLICATE KEY UPDATE count = IF(count < :maxUses, count + 1, count)
            """, nativeQuery = true)
    int tryIncrementUserCount(
            @Param("couponId") java.util.UUID couponId,
            @Param("userId") UUID userId,
            @Param("maxUses") int maxUses);

    /**
     * Decrements the per-user count by 1 (floor 0). Called on order cancellation or payment
     * failure so the user can redeem the coupon again.
     */
    @Modifying
    @Query(value = """
            UPDATE coupon_per_user_counts
            SET count = GREATEST(count - 1, 0)
            WHERE coupon_id = :couponId AND user_id = :userId
            """, nativeQuery = true)
    void decrementUserCount(
            @Param("couponId") java.util.UUID couponId,
            @Param("userId") UUID userId);
}
