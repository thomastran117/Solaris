package backend.repositories;

import backend.models.core.SubOrder;
import backend.repositories.projections.DailyCountProjection;
import backend.repositories.projections.MarketplaceSummaryProjection;
import backend.repositories.projections.TopVendorProjection;
import backend.repositories.projections.VendorRevenueDailyProjection;
import backend.repositories.projections.VendorRevenueSummaryProjection;
import backend.repositories.projections.VendorShipHoursProjection;
import backend.repositories.projections.VendorTopProductProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregation queries for vendor and marketplace analytics dashboards.
 * Bound to {@link SubOrder} so Spring Data has an entity to attach to.
 * All queries are vendor/marketplace-scoped and time-windowed.
 */
@Repository
public interface VendorAnalyticsRepository extends JpaRepository<SubOrder, UUID> {

    // -------------------------------------------------------------------------
    // Vendor revenue
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT COALESCE(SUM(cr.gross_amount), 0) AS totalGross, " +
            "       COALESCE(SUM(cr.commission_amount), 0) AS totalCommission, " +
            "       COALESCE(SUM(cr.net_vendor_amount), 0) AS totalNet, " +
            "       COUNT(DISTINCT so.id) AS totalOrders, " +
            "       CASE WHEN COUNT(DISTINCT so.id) > 0 " +
            "            THEN SUM(cr.gross_amount) / COUNT(DISTINCT so.id) ELSE 0 END AS avgOrderValue " +
            "FROM sub_orders so " +
            "LEFT JOIN commission_records cr ON cr.sub_order_id = so.id " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.created_at BETWEEN :from AND :to " +
            "AND so.status != 'CANCELLED'",
            nativeQuery = true)
    VendorRevenueSummaryProjection vendorRevenueSummary(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value =
            "SELECT DATE(so.created_at) AS day, " +
            "       COALESCE(SUM(cr.gross_amount), 0) AS gross, " +
            "       COALESCE(SUM(cr.commission_amount), 0) AS commission, " +
            "       COALESCE(SUM(cr.net_vendor_amount), 0) AS net, " +
            "       COUNT(DISTINCT so.id) AS orderCount " +
            "FROM sub_orders so " +
            "LEFT JOIN commission_records cr ON cr.sub_order_id = so.id " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.created_at BETWEEN :from AND :to " +
            "AND so.status != 'CANCELLED' " +
            "GROUP BY DATE(so.created_at) " +
            "ORDER BY DATE(so.created_at)",
            nativeQuery = true)
    List<VendorRevenueDailyProjection> vendorRevenueDaily(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // Vendor top products
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT p.id AS productId, p.name AS productName, " +
            "       SUM(oi.quantity) AS totalUnitsSold, " +
            "       SUM(oi.unit_price * oi.quantity) AS totalRevenue " +
            "FROM sub_orders so " +
            "JOIN order_items oi ON oi.sub_order_id = so.id " +
            "JOIN products p ON p.id = oi.product_id " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.created_at BETWEEN :from AND :to " +
            "AND so.status != 'CANCELLED' " +
            "GROUP BY p.id, p.name " +
            "ORDER BY totalUnitsSold DESC " +
            "LIMIT :lim",
            nativeQuery = true)
    List<VendorTopProductProjection> vendorTopProducts(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("lim") int limit);

    // -------------------------------------------------------------------------
    // Vendor order counts
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT COUNT(*) AS count " +
            "FROM sub_orders so " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.created_at BETWEEN :from AND :to",
            nativeQuery = true)
    Long vendorTotalOrders(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value =
            "SELECT DATE(so.created_at) AS day, COUNT(*) AS count " +
            "FROM sub_orders so " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.created_at BETWEEN :from AND :to " +
            "GROUP BY DATE(so.created_at) " +
            "ORDER BY DATE(so.created_at)",
            nativeQuery = true)
    List<DailyCountProjection> vendorOrdersDaily(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value =
            "SELECT COUNT(*) AS count " +
            "FROM sub_orders so " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.status = 'CANCELLED' " +
            "AND so.created_at BETWEEN :from AND :to",
            nativeQuery = true)
    Long vendorCancelledCount(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value =
            "SELECT COUNT(*) AS count " +
            "FROM sub_orders so " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.status = 'RETURNED' " +
            "AND so.created_at BETWEEN :from AND :to",
            nativeQuery = true)
    Long vendorReturnedCount(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value =
            "SELECT DATE(so.cancelled_at) AS day, COUNT(*) AS count " +
            "FROM sub_orders so " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.status = 'CANCELLED' " +
            "AND so.cancelled_at BETWEEN :from AND :to " +
            "GROUP BY DATE(so.cancelled_at) " +
            "ORDER BY DATE(so.cancelled_at)",
            nativeQuery = true)
    List<DailyCountProjection> vendorRefundsDaily(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // Vendor ship hours (SLA + analytics)
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT AVG(TIMESTAMPDIFF(SECOND, so.paid_at, so.shipped_at) / 3600.0) AS avgShipHours, " +
            "       COUNT(*) AS totalShipped, " +
            "       SUM(CASE WHEN TIMESTAMPDIFF(SECOND, so.paid_at, so.shipped_at) / 3600.0 > :targetHours " +
            "                THEN 1 ELSE 0 END) AS totalLate " +
            "FROM sub_orders so " +
            "WHERE so.marketplace_vendor_id = :vendorId " +
            "AND so.marketplace_id = :marketplaceId " +
            "AND so.shipped_at IS NOT NULL " +
            "AND so.created_at BETWEEN :from AND :to",
            nativeQuery = true)
    VendorShipHoursProjection vendorShipHours(
            @Param("vendorId") UUID vendorId,
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("targetHours") double targetHours);

    // -------------------------------------------------------------------------
    // Marketplace-level aggregates
    // -------------------------------------------------------------------------

    @Query(value =
            "SELECT COALESCE(SUM(cr.gross_amount), 0) AS gmv, " +
            "       COALESCE(SUM(cr.commission_amount), 0) AS totalCommission, " +
            "       COUNT(DISTINCT so.marketplace_vendor_id) AS activeVendors, " +
            "       COUNT(DISTINCT so.id) AS totalOrders " +
            "FROM sub_orders so " +
            "LEFT JOIN commission_records cr ON cr.sub_order_id = so.id " +
            "WHERE so.marketplace_id = :marketplaceId " +
            "AND so.created_at BETWEEN :from AND :to " +
            "AND so.status != 'CANCELLED'",
            nativeQuery = true)
    MarketplaceSummaryProjection marketplaceSummary(
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value =
            "SELECT mv.id AS vendorId, c.name AS vendorName, " +
            "       COUNT(DISTINCT so.id) AS totalSubOrders, " +
            "       COALESCE(SUM(cr.gross_amount), 0) AS totalGrossRevenue, " +
            "       COALESCE(SUM(cr.commission_amount), 0) AS totalCommission, " +
            "       COALESCE(SUM(CASE WHEN so.status = 'CANCELLED' THEN 1 ELSE 0 END) " +
            "                / NULLIF(COUNT(DISTINCT so.id), 0), 0) AS cancellationRate " +
            "FROM sub_orders so " +
            "JOIN marketplace_vendors mv ON mv.id = so.marketplace_vendor_id " +
            "JOIN companies c ON c.id = mv.vendor_company_id " +
            "LEFT JOIN commission_records cr ON cr.sub_order_id = so.id " +
            "WHERE so.marketplace_id = :marketplaceId " +
            "AND so.created_at BETWEEN :from AND :to " +
            "GROUP BY mv.id, c.name " +
            "ORDER BY totalGrossRevenue DESC " +
            "LIMIT :lim",
            nativeQuery = true)
    List<TopVendorProjection> marketplaceTopVendors(
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("lim") int limit);

    @Query(value =
            "SELECT DATE(so.created_at) AS day, COUNT(DISTINCT so.id) AS count " +
            "FROM sub_orders so " +
            "WHERE so.marketplace_id = :marketplaceId " +
            "AND so.created_at BETWEEN :from AND :to " +
            "GROUP BY DATE(so.created_at) " +
            "ORDER BY DATE(so.created_at)",
            nativeQuery = true)
    List<DailyCountProjection> marketplaceOrdersDaily(
            @Param("marketplaceId") UUID marketplaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
