package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.PromotionRedemption;
import backend.repositories.projections.PayoutAttributionProjection;
import backend.repositories.projections.PromotionRuleAnalyticsProjection;

import java.time.Instant;
import java.util.List;

@Repository
public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, java.util.UUID> {

    List<PromotionRedemption> findAllByOrderId(java.util.UUID orderId);

    List<PromotionRedemption> findAllByRuleId(java.util.UUID ruleId);

    /**
     * Aggregates settlement amounts per funding company across all rule redemptions in the
     * given window. Null bounds are treated as open-ended. Ordered by total savings DESC.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                c.id                                   AS fundedByCompanyId,
                c.name                                 AS companyName,
                COALESCE(SUM(pr.discount_amount), 0)   AS totalSavings,
                COUNT(pr.id)                           AS redemptionCount,
                COUNT(DISTINCT pr.order_id)            AS uniqueOrderCount
            FROM promotion_redemptions pr
            JOIN companies c ON pr.funded_by_company_id = c.id
            WHERE (:from IS NULL OR pr.redeemed_at >= :from)
              AND (:to   IS NULL OR pr.redeemed_at <= :to)
            GROUP BY c.id, c.name
            ORDER BY totalSavings DESC
            """)
    List<PayoutAttributionProjection> aggregatePayoutAttribution(
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Aggregates redemption count, total savings, and unique user/order counts for one rule
     * in the given window. Returns a single row (all zeros if no redemptions match).
     */
    @Query(nativeQuery = true, value = """
            SELECT
                COUNT(pr.id)                         AS redemptionCount,
                COALESCE(SUM(pr.discount_amount), 0) AS totalSavings,
                COUNT(DISTINCT pr.order_id)          AS uniqueOrderCount,
                COUNT(DISTINCT pr.user_id)           AS uniqueUserCount
            FROM promotion_redemptions pr
            WHERE pr.rule_id = :ruleId
              AND (:from IS NULL OR pr.redeemed_at >= :from)
              AND (:to   IS NULL OR pr.redeemed_at <= :to)
            """)
    PromotionRuleAnalyticsProjection aggregateRuleAnalytics(
            @Param("ruleId") java.util.UUID ruleId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
