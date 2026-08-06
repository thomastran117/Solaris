package backend.repositories;

import backend.models.core.Order;
import backend.repositories.projections.CancellationReasonCountProjection;
import backend.repositories.projections.DailyCountProjection;
import backend.repositories.projections.DailyDurationProjection;
import backend.repositories.projections.DurationStatsProjection;
import backend.repositories.projections.SupplierLatenessProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregation queries for the SLA / Operations Dashboard. Bound to {@link Order}
 * solely so Spring Data has an entity to attach to — there is no entity-level
 * use of these methods. All queries are company-scoped and time-windowed.
 *
 * Native SQL is used wherever date truncation or interval arithmetic is needed,
 * since these have no portable JPQL equivalent. Aggregate "headline" stats use
 * JPQL where possible.
 *
 * <p>Elapsed seconds are computed as {@code EXTRACT(EPOCH FROM (b - a))} and cast to
 * {@code double precision}. The cast is required, not cosmetic: PostgreSQL 14+ returns
 * {@code numeric} from {@code EXTRACT}, and {@code AVG(numeric)} is also {@code numeric},
 * which arrives as a {@code BigDecimal} and fails to bind to the {@code Double} getters
 * on the projection interfaces.
 */
@Repository
public interface OperationsMetricsRepository extends JpaRepository<Order, UUID> {

    // -------------------------------------------------------------------------
    // 1) Average fulfillment time (createdAt -> shippedAt)
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT COUNT(*) AS count, AVG(avgSeconds) AS avgSeconds FROM (" +
            "  SELECT DISTINCT o.id, CAST(EXTRACT(EPOCH FROM (o.shipped_at - o.created_at)) AS double precision) AS avgSeconds " +
            "  FROM orders o " +
            "  JOIN order_items i ON i.order_id = o.id " +
            "  JOIN products p ON p.id = i.product_id " +
            "  WHERE p.company_id = :companyId " +
            "  AND o.shipped_at IS NOT NULL " +
            "  AND o.shipped_at BETWEEN :from AND :to" +
            ") t",
            nativeQuery = true)
    DurationStatsProjection fulfillmentStats(@Param("companyId") UUID companyId,
                                             @Param("from") Instant from, @Param("to") Instant to);

    @Query(value =
            "SELECT day, COUNT(*) AS count, AVG(avgSeconds) AS avgSeconds FROM (" +
            "  SELECT DISTINCT o.id, CAST(o.shipped_at AS date) AS day, " +
            "         CAST(EXTRACT(EPOCH FROM (o.shipped_at - o.created_at)) AS double precision) AS avgSeconds " +
            "  FROM orders o " +
            "  JOIN order_items i ON i.order_id = o.id " +
            "  JOIN products p ON p.id = i.product_id " +
            "  WHERE p.company_id = :companyId " +
            "  AND o.shipped_at IS NOT NULL " +
            "  AND o.shipped_at BETWEEN :from AND :to" +
            ") t GROUP BY day ORDER BY day",
            nativeQuery = true)
    List<DailyDurationProjection> fulfillmentDaily(@Param("companyId") UUID companyId,
                                                   @Param("from") Instant from, @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // 2) Refund resolution time (Return.createdAt -> completedAt)
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT COUNT(DISTINCT r.id) AS count, " +
            "       AVG(CAST(EXTRACT(EPOCH FROM (r.completed_at - r.created_at)) AS double precision)) AS avgSeconds " +
            "FROM returns r " +
            "JOIN return_items ri ON ri.return_id = r.id " +
            "JOIN order_items oi ON oi.id = ri.order_item_id " +
            "JOIN products p ON p.id = oi.product_id " +
            "WHERE p.company_id = :companyId " +
            "AND r.completed_at IS NOT NULL " +
            "AND r.completed_at BETWEEN :from AND :to",
            nativeQuery = true)
    DurationStatsProjection refundStats(@Param("companyId") UUID companyId,
                                        @Param("from") Instant from, @Param("to") Instant to);

    @Query(value =
            "SELECT day, count, avgSeconds FROM (" +
            "  SELECT CAST(r.completed_at AS date) AS day, " +
            "         COUNT(DISTINCT r.id) AS count, " +
            "         AVG(CAST(EXTRACT(EPOCH FROM (r.completed_at - r.created_at)) AS double precision)) AS avgSeconds " +
            "  FROM returns r " +
            "  JOIN return_items ri ON ri.return_id = r.id " +
            "  JOIN order_items oi ON oi.id = ri.order_item_id " +
            "  JOIN products p ON p.id = oi.product_id " +
            "  WHERE p.company_id = :companyId " +
            "  AND r.completed_at IS NOT NULL " +
            "  AND r.completed_at BETWEEN :from AND :to " +
            "  GROUP BY CAST(r.completed_at AS date)" +
            ") t ORDER BY day",
            nativeQuery = true)
    List<DailyDurationProjection> refundDaily(@Param("companyId") UUID companyId,
                                              @Param("from") Instant from, @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // 3) Warehouse pick delay (paidAt -> packedAt)
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT COUNT(*) AS count, AVG(avgSeconds) AS avgSeconds FROM (" +
            "  SELECT DISTINCT o.id, CAST(EXTRACT(EPOCH FROM (o.packed_at - o.paid_at)) AS double precision) AS avgSeconds " +
            "  FROM orders o " +
            "  JOIN order_items i ON i.order_id = o.id " +
            "  JOIN products p ON p.id = i.product_id " +
            "  WHERE p.company_id = :companyId " +
            "  AND o.paid_at IS NOT NULL AND o.packed_at IS NOT NULL " +
            "  AND o.packed_at BETWEEN :from AND :to" +
            ") t",
            nativeQuery = true)
    DurationStatsProjection pickDelayStats(@Param("companyId") UUID companyId,
                                           @Param("from") Instant from, @Param("to") Instant to);

    @Query(value =
            "SELECT day, COUNT(*) AS count, AVG(avgSeconds) AS avgSeconds FROM (" +
            "  SELECT DISTINCT o.id, CAST(o.packed_at AS date) AS day, " +
            "         CAST(EXTRACT(EPOCH FROM (o.packed_at - o.paid_at)) AS double precision) AS avgSeconds " +
            "  FROM orders o " +
            "  JOIN order_items i ON i.order_id = o.id " +
            "  JOIN products p ON p.id = i.product_id " +
            "  WHERE p.company_id = :companyId " +
            "  AND o.paid_at IS NOT NULL AND o.packed_at IS NOT NULL " +
            "  AND o.packed_at BETWEEN :from AND :to" +
            ") t GROUP BY day ORDER BY day",
            nativeQuery = true)
    List<DailyDurationProjection> pickDelayDaily(@Param("companyId") UUID companyId,
                                                 @Param("from") Instant from, @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // 5) Supplier lateness (RestockRequest.expectedArrivalDate -> receivedAt)
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT COUNT(*) AS total, " +
            "       SUM(CASE WHEN CAST(rr.received_at AS date) > rr.expected_arrival_date THEN 1 ELSE 0 END) AS late, " +
            "       AVG(CASE WHEN CAST(rr.received_at AS date) > rr.expected_arrival_date " +
            "                THEN CAST(CAST(rr.received_at AS date) - rr.expected_arrival_date AS double precision) END) AS avgLateDays " +
            "FROM restock_requests rr " +
            "WHERE rr.company_id = :companyId " +
            "AND rr.expected_arrival_date IS NOT NULL AND rr.received_at IS NOT NULL " +
            "AND rr.received_at BETWEEN :from AND :to",
            nativeQuery = true)
    SupplierLatenessProjection supplierLatenessStats(@Param("companyId") UUID companyId,
                                                     @Param("from") Instant from, @Param("to") Instant to);

    @Query(value =
            "SELECT CAST(rr.received_at AS date) AS day, " +
            "       SUM(CASE WHEN CAST(rr.received_at AS date) > rr.expected_arrival_date THEN 1 ELSE 0 END) AS count " +
            "FROM restock_requests rr " +
            "WHERE rr.company_id = :companyId " +
            "AND rr.expected_arrival_date IS NOT NULL AND rr.received_at IS NOT NULL " +
            "AND rr.received_at BETWEEN :from AND :to " +
            "GROUP BY CAST(rr.received_at AS date) " +
            "ORDER BY CAST(rr.received_at AS date)",
            nativeQuery = true)
    List<DailyCountProjection> supplierLatenessDaily(@Param("companyId") UUID companyId,
                                                     @Param("from") Instant from, @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // 6) Cancellations
    // -------------------------------------------------------------------------

    @Query("SELECT o.cancellationReason AS reason, COUNT(DISTINCT o) AS count " +
           "FROM Order o JOIN o.items i " +
           "WHERE i.product.company.id = :companyId " +
           "AND o.cancelledAt IS NOT NULL " +
           "AND o.cancelledAt BETWEEN :from AND :to " +
           "GROUP BY o.cancellationReason")
    List<CancellationReasonCountProjection> cancellationsByReason(@Param("companyId") UUID companyId,
                                                                  @Param("from") Instant from, @Param("to") Instant to);

    @Query(value =
            "SELECT CAST(o.cancelled_at AS date) AS day, COUNT(DISTINCT o.id) AS count " +
            "FROM orders o " +
            "JOIN order_items i ON i.order_id = o.id " +
            "JOIN products p ON p.id = i.product_id " +
            "WHERE p.company_id = :companyId " +
            "AND o.cancelled_at IS NOT NULL " +
            "AND o.cancelled_at BETWEEN :from AND :to " +
            "GROUP BY CAST(o.cancelled_at AS date) " +
            "ORDER BY CAST(o.cancelled_at AS date)",
            nativeQuery = true)
    List<DailyCountProjection> cancellationsDaily(@Param("companyId") UUID companyId,
                                                  @Param("from") Instant from, @Param("to") Instant to);
}
