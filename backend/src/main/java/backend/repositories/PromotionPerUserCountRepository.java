package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.PromotionPerUserCount;

import java.util.UUID;

@Repository
public interface PromotionPerUserCountRepository extends JpaRepository<PromotionPerUserCount, java.util.UUID> {

    /**
     * Atomic per-user increment. Returns 1 on success, 0 when the per-user cap has been reached.
     * Mirrors CouponPerUserCountRepository.tryIncrementUserCount.
     */
    @Modifying
    @Query(value = """
            INSERT INTO promotion_per_user_counts (rule_id, user_id, count)
            VALUES (:ruleId, :userId, 1)
            ON DUPLICATE KEY UPDATE count = IF(count < :maxUses, count + 1, count)
            """, nativeQuery = true)
    int tryIncrementUserCount(
            @Param("ruleId") java.util.UUID ruleId,
            @Param("userId") UUID userId,
            @Param("maxUses") int maxUses);

    @Modifying
    @Query(value = """
            UPDATE promotion_per_user_counts
            SET count = GREATEST(count - 1, 0)
            WHERE rule_id = :ruleId AND user_id = :userId
            """, nativeQuery = true)
    void decrementUserCount(
            @Param("ruleId") java.util.UUID ruleId,
            @Param("userId") UUID userId);

    @Query(value = "SELECT COALESCE(SUM(count), 0) FROM promotion_per_user_counts " +
                   "WHERE rule_id = :ruleId AND user_id = :userId",
           nativeQuery = true)
    int countByRuleIdAndUserId(@Param("ruleId") java.util.UUID ruleId, @Param("userId") UUID userId);
}
