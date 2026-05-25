package backend.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.Order;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, java.util.UUID> {
    Page<Order> findAllByUserId(UUID userId, Pageable pageable);
    Optional<Order> findByIdAndUserId(java.util.UUID id, UUID userId);
    Optional<Order> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdWithItems(@Param("id") java.util.UUID id, @Param("userId") UUID userId);

    /** Total orders placed by this user — feeds CouponAbuseEvaluator's first-order heuristic. */
    long countByUserId(UUID userId);

    /**
     * Count of the user's orders that have moved past the just-created RESERVED state.
     * Used by {@code CouponAbuseEvaluator} so a "first order" check doesn't get fooled
     * by another in-flight RESERVED order placed in the same instant — both would have
     * incremented {@link #countByUserId} but neither has confirmed payment yet, so
     * neither is really a "prior" order.
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.status <> :excludeStatus")
    long countByUserIdExcludingStatus(@Param("userId") UUID userId,
                                      @Param("excludeStatus") OrderStatus excludeStatus);
    Optional<Order> findByPaymentIntentId(String paymentIntentId);
    Optional<Order> findByStripeInvoiceId(String stripeInvoiceId);
    Optional<Order> findByTrackingNumber(String trackingNumber);
    Page<Order> findAllByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);
    List<Order> findAllByStatusAndCompensatedFalseAndCreatedAtBefore(OrderStatus status, Instant before);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.items oi WHERE oi.product.company.id = :companyId")
    Page<Order> findAllByProductCompanyId(@Param("companyId") java.util.UUID companyId, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.items oi WHERE oi.product.company.id = :companyId AND o.status = :status")
    Page<Order> findAllByProductCompanyIdAndStatus(@Param("companyId") java.util.UUID companyId, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN o.items oi WHERE o.id = :orderId AND oi.product.company.id = :companyId")
    Optional<Order> findByIdAndProductCompanyId(@Param("orderId") java.util.UUID orderId, @Param("companyId") java.util.UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o JOIN o.items oi WHERE o.id = :orderId AND oi.product.company.id = :companyId")
    Optional<Order> findByIdAndProductCompanyIdForUpdate(@Param("orderId") java.util.UUID orderId, @Param("companyId") java.util.UUID companyId);

    /**
     * Atomically claims compensation rights for an order. Returns 1 if this caller is the
     * first to set compensated=true, 0 if another thread already did so. Callers that
     * receive 0 must skip stock restoration to prevent double-restoring inventory.
     */
    @Modifying
    @Query("UPDATE Order o SET o.compensated = true WHERE o.id = :id AND o.compensated = false")
    int markCompensated(@Param("id") java.util.UUID id);

    /**
     * Atomically transitions an order from expectedStatus to newStatus. Returns 1 if the
     * transition succeeded, 0 if the order was already in a different state (concurrent webhook).
     * Use this instead of READ-COMPARE-WRITE patterns for idempotent webhook handlers.
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus WHERE o.id = :id AND o.status = :expectedStatus")
    int transitionStatus(@Param("id") java.util.UUID id,
                         @Param("expectedStatus") OrderStatus expectedStatus,
                         @Param("newStatus") OrderStatus newStatus);

    /**
     * Atomically applies {@code delta} (may be negative) to {@code refundedAmountCents}
     * and floors the result at zero. The {@code clearAutomatically} flag forces JPA to
     * re-load the entity on next access so the in-memory Order isn't out of sync with
     * the column we just updated outside of dirty-checking.
     *
     * <p>Returns the affected row count (0 if {@code id} doesn't exist).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.refundedAmountCents = " +
           "CASE WHEN o.refundedAmountCents + :delta < 0 THEN 0 ELSE o.refundedAmountCents + :delta END " +
           "WHERE o.id = :id")
    int addRefundAmountDelta(@Param("id") java.util.UUID id, @Param("delta") long delta);

    /**
     * FIFO: PAID orders that contain at least one BACKORDERED item for the given product.
     * Replaces the retired findBackordersByProductId (which queried BACKORDER-status orders).
     */
    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i " +
           "WHERE o.status = backend.models.enums.OrderStatus.PAID " +
           "AND i.product.id = :productId " +
           "AND i.fulfillmentStatus = :backordered " +
           "ORDER BY o.createdAt ASC")
    List<Order> findPaidOrdersWithBackorderedProduct(
            @Param("productId") java.util.UUID productId,
            @Param("backordered") FulfillmentStatus backordered);

    /**
     * FIFO: PAID orders that contain at least one BACKORDERED item for the given variant.
     * Replaces the retired findBackordersByVariantId.
     */
    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i " +
           "WHERE o.status = backend.models.enums.OrderStatus.PAID " +
           "AND i.variant.id = :variantId " +
           "AND i.fulfillmentStatus = :backordered " +
           "ORDER BY o.createdAt ASC")
    List<Order> findPaidOrdersWithBackorderedVariant(
            @Param("variantId") java.util.UUID variantId,
            @Param("backordered") FulfillmentStatus backordered);

    // -------------------------------------------------------------------------
    // SLA / Operations Dashboard support
    // -------------------------------------------------------------------------

    /**
     * Total distinct orders for the company in the window — denominator for backorder rate.
     * Counts orders by their {@code createdAt} timestamp.
     */
    @Query("SELECT COUNT(DISTINCT o) FROM Order o JOIN o.items i " +
           "WHERE i.product.company.id = :companyId " +
           "AND o.createdAt BETWEEN :from AND :to")
    long countOrdersInWindow(@Param("companyId") java.util.UUID companyId,
                             @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Distinct orders in the window that contained at least one BACKORDERED item — numerator
     * for backorder rate on the SLA dashboard.
     */
    @Query("SELECT COUNT(DISTINCT o) FROM Order o JOIN o.items i " +
           "WHERE i.product.company.id = :companyId " +
           "AND o.createdAt BETWEEN :from AND :to " +
           "AND i.fulfillmentStatus = backend.models.enums.FulfillmentStatus.BACKORDERED")
    long countOrdersWithBackorderedItemsInWindow(@Param("companyId") java.util.UUID companyId,
                                                 @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.items i " +
           "WHERE o.user.id = :userId " +
           "AND i.product.id = :productId " +
           "AND o.status IN (backend.models.enums.OrderStatus.SHIPPED, backend.models.enums.OrderStatus.DELIVERED)")
    boolean existsDeliveredOrShippedOrderForProduct(@Param("userId") UUID userId,
                                                    @Param("productId") java.util.UUID productId);
}
