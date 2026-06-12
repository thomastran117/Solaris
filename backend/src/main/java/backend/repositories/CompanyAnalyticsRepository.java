package backend.repositories;

import backend.models.core.Order;
import backend.repositories.projections.CategorySalesProjection;
import backend.repositories.projections.DailyRevProjection;
import backend.repositories.projections.SlowMoverProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregation queries for company-level product analytics.
 * Bound to {@link Order} so Spring Data has an entity to attach to — there is
 * no entity-level use of these methods. All queries are company-scoped and
 * time-windowed. Native SQL is used for DATE() grouping which has no JPQL equivalent.
 */
@Repository
public interface CompanyAnalyticsRepository extends JpaRepository<Order, UUID> {

    // -------------------------------------------------------------------------
    // Daily revenue
    // -------------------------------------------------------------------------

    @Query(nativeQuery = true, value = """
            SELECT
                DATE(o.created_at)                          AS day,
                SUM(oi.quantity * oi.unit_price)            AS totalRevenue,
                SUM(oi.quantity)                            AS totalUnits,
                COUNT(DISTINCT o.id)                        AS orderCount
            FROM order_items oi
            JOIN orders o  ON oi.order_id  = o.id
            JOIN products p ON oi.product_id = p.id
            WHERE p.company_id = :companyId
              AND o.status = 'PAID'
              AND o.created_at BETWEEN :from AND :to
            GROUP BY DATE(o.created_at)
            ORDER BY DATE(o.created_at)
            """)
    List<DailyRevProjection> getDailyRevenue(
            @Param("companyId") UUID companyId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // Category sales
    // -------------------------------------------------------------------------

    @Query(nativeQuery = true, value = """
            SELECT
                COALESCE(p.category, 'Uncategorised')       AS category,
                SUM(oi.quantity * oi.unit_price)            AS totalRevenue,
                SUM(oi.quantity)                            AS totalUnits,
                COUNT(DISTINCT o.id)                        AS orderCount
            FROM order_items oi
            JOIN orders o  ON oi.order_id  = o.id
            JOIN products p ON oi.product_id = p.id
            WHERE p.company_id = :companyId
              AND o.status = 'PAID'
              AND o.created_at BETWEEN :from AND :to
            GROUP BY COALESCE(p.category, 'Uncategorised')
            ORDER BY totalRevenue DESC
            """)
    List<CategorySalesProjection> getCategorySales(
            @Param("companyId") UUID companyId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // Slow movers — active products with stock, low sales velocity
    // -------------------------------------------------------------------------

    @Query(nativeQuery = true, value = """
            SELECT
                p.id                                        AS productId,
                p.name                                      AS productName,
                p.sku                                       AS sku,
                p.stock                                     AS currentStock,
                p.price                                     AS price,
                p.currency                                  AS currency,
                COALESCE(SUM(CASE WHEN o.id IS NOT NULL THEN oi.quantity ELSE 0 END), 0)               AS totalUnitsSold,
                COALESCE(SUM(CASE WHEN o.id IS NOT NULL THEN oi.quantity * oi.unit_price ELSE 0 END), 0.00) AS totalRevenue
            FROM products p
            LEFT JOIN order_items oi ON oi.product_id = p.id
            LEFT JOIN orders o ON oi.order_id = o.id
                AND o.status = 'PAID'
                AND o.created_at >= :since
            WHERE p.company_id = :companyId
              AND p.stock > 0
              AND p.status = 'ACTIVE'
            GROUP BY p.id, p.name, p.sku, p.stock, p.price, p.currency
            HAVING COALESCE(SUM(CASE WHEN o.id IS NOT NULL THEN oi.quantity ELSE 0 END), 0) / :windowDays < :maxVelocity
            ORDER BY COALESCE(SUM(CASE WHEN o.id IS NOT NULL THEN oi.quantity ELSE 0 END), 0) / :windowDays ASC
            LIMIT :limitVal
            """)
    List<SlowMoverProjection> getSlowMovers(
            @Param("companyId") UUID companyId,
            @Param("since") Instant since,
            @Param("windowDays") double windowDays,
            @Param("maxVelocity") double maxVelocity,
            @Param("limitVal") int limit);
}
