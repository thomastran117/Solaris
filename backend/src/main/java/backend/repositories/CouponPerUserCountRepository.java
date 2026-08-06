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
     * <p>
     * The cap lives in the {@code DO UPDATE ... WHERE} clause rather than in the assigned
     * value, because that is what makes PostgreSQL report 0 affected rows when the cap is
     * hit. {@code ON CONFLICT DO UPDATE} always reports 1 when its WHERE passes, so writing
     * this as {@code SET count = LEAST(count + 1, :maxUses)} would report 1 unconditionally
     * and silently disable the cap that callers detect via {@code claimed == 0}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO coupon_per_user_counts (id, coupon_id, user_id, count)
            VALUES (gen_random_uuid(), :couponId, :userId, 1)
            ON CONFLICT (coupon_id, user_id) DO UPDATE
               SET count = coupon_per_user_counts.count + 1
             WHERE coupon_per_user_counts.count < :maxUses
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
