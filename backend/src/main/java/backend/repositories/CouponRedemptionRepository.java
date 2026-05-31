package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.CouponRedemption;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, java.util.UUID> {

    /** Used to enforce per-user usage caps. */
    long countByCouponIdAndUserId(java.util.UUID couponId, UUID userId);

    /** Coupon redemptions for a given user since {@code since} — feeds CouponAbuseEvaluator. */
    long countByUser_IdAndRedeemedAtAfter(UUID userId, Instant since);

    /** Coupon redemptions from a given client IP since {@code since} — feeds cross-account abuse detection. */
    long countByIpAndRedeemedAtAfter(String ip, Instant since);
}
