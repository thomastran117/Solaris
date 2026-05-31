package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import backend.models.core.Return;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnRepository extends JpaRepository<Return, java.util.UUID> {

    @Query("SELECT r FROM Return r LEFT JOIN FETCH r.evidenceUrls WHERE r.order.id = :orderId")
    List<Return> findAllByOrderId(@Param("orderId") java.util.UUID orderId);

    /** Used to correlate Stripe charge.refunded / refund.updated webhook events back to a Return. */
    Optional<Return> findByStripeRefundId(String stripeRefundId);

    /** Returns all non-terminal return requests for an order (excludes REJECTED and FAILED). */
    @Query("SELECT r FROM Return r LEFT JOIN FETCH r.evidenceUrls " +
           "WHERE r.order.id = :orderId AND r.status NOT IN ('REJECTED','FAILED')")
    List<Return> findActiveByOrderId(@Param("orderId") java.util.UUID orderId);

    /** All returns for orders belonging to a company — used for merchant return management views. */
    @Query("SELECT DISTINCT r FROM Return r LEFT JOIN FETCH r.evidenceUrls " +
           "JOIN r.items ri JOIN ri.orderItem oi WHERE oi.product.company.id = :companyId")
    List<Return> findAllByCompanyId(@Param("companyId") java.util.UUID companyId);

    /** Returns for a specific order scoped to items belonging to a company. */
    @Query("SELECT DISTINCT r FROM Return r LEFT JOIN FETCH r.evidenceUrls " +
           "JOIN r.items ri JOIN ri.orderItem oi " +
           "WHERE r.order.id = :orderId AND oi.product.company.id = :companyId")
    List<Return> findAllByOrderIdAndCompanyId(@Param("orderId") java.util.UUID orderId, @Param("companyId") java.util.UUID companyId);

    /** Single return scoped to items belonging to a company — prevents cross-company access. */
    @Query("SELECT DISTINCT r FROM Return r LEFT JOIN FETCH r.evidenceUrls " +
           "JOIN r.items ri JOIN ri.orderItem oi " +
           "WHERE r.id = :returnId AND oi.product.company.id = :companyId")
    Optional<Return> findByIdAndCompanyId(@Param("returnId") java.util.UUID returnId, @Param("companyId") java.util.UUID companyId);

    /**
     * Same scope as {@link #findByIdAndCompanyId} but acquires a pessimistic write lock,
     * serialising concurrent approval/rejection attempts on the same return row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Return r JOIN r.items ri JOIN ri.orderItem oi " +
           "WHERE r.id = :returnId AND oi.product.company.id = :companyId")
    Optional<Return> findByIdAndCompanyIdForUpdate(@Param("returnId") java.util.UUID returnId, @Param("companyId") java.util.UUID companyId);

    /** Total lifetime return count for a user — feeds ReturnPatternEvaluator's return-rate signal. */
    @Query("SELECT COUNT(r) FROM Return r WHERE r.order.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);

    /** Returns created by a user after {@code since} — used for repeat-return velocity. */
    @Query("SELECT COUNT(r) FROM Return r WHERE r.order.user.id = :userId AND r.createdAt > :since")
    long countByUserIdAndCreatedAtAfter(@Param("userId") UUID userId, @Param("since") Instant since);
}
