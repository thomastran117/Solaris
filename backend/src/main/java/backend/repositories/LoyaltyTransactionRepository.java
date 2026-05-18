package backend.repositories;

import backend.models.core.LoyaltyTransaction;
import backend.models.enums.LoyaltyTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {

    Page<LoyaltyTransaction> findByAccountId(UUID accountId, Pageable pageable);

    Optional<LoyaltyTransaction> findFirstBySourceOrderIdAndType(UUID sourceOrderId, LoyaltyTransactionType type);

    boolean existsBySourceOrderIdAndType(UUID sourceOrderId, LoyaltyTransactionType type);

    /**
     * Sum of absolute points already reversed for {@code orderId} (used to compute the
     * incremental delta when a second partial refund lands on the same order).
     */
    @Query("SELECT COALESCE(SUM(ABS(t.pointsDelta)), 0) FROM LoyaltyTransaction t " +
           "WHERE t.sourceOrderId = :orderId AND t.type = :type")
    long sumAbsPointsForOrderAndType(@Param("orderId") UUID orderId,
                                     @Param("type") LoyaltyTransactionType type);

    List<LoyaltyTransaction> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    /** Earn transactions that have expired and haven't been claimed by a prior expiry run. */
    @Query("SELECT t FROM LoyaltyTransaction t WHERE t.account.id = :accountId " +
           "AND t.type IN ('EARN_ORDER', 'EARN_BONUS', 'EARN_BIRTHDAY') " +
           "AND t.expiresAt IS NOT NULL AND t.expiresAt < :now AND t.expired = false")
    List<LoyaltyTransaction> findExpiredEarns(@Param("accountId") UUID accountId, @Param("now") Instant now);

    /**
     * Atomically claims an earn row for expiry. Returns 1 if this caller won the race,
     * 0 if a concurrent scheduler run already claimed it.
     */
    @Modifying
    @Query("UPDATE LoyaltyTransaction t SET t.expired = true WHERE t.id = :id AND t.expired = false")
    int claimForExpiry(@Param("id") UUID id);

    /** Check whether a birthday reward has already been given this calendar year. */
    @Query("SELECT COUNT(t) > 0 FROM LoyaltyTransaction t WHERE t.account.id = :accountId " +
           "AND t.type = 'EARN_BIRTHDAY' AND YEAR(t.createdAt) = :year")
    boolean existsBirthdayRewardForYear(@Param("accountId") UUID accountId, @Param("year") int year);

    /** Accounts with at least one unclaimed expired earn row and a positive balance — candidates for expiry run. */
    @Query(value =
           "SELECT DISTINCT t.account_id FROM loyalty_transactions t " +
           "JOIN loyalty_accounts a ON a.id = t.account_id " +
           "WHERE t.type IN ('EARN_ORDER','EARN_BONUS','EARN_BIRTHDAY') " +
           "AND t.expires_at IS NOT NULL AND t.expires_at < :now " +
           "AND t.expired = false " +
           "AND a.points_balance > 0",
           nativeQuery = true)
    List<UUID> findAccountIdsWithExpiredPoints(@Param("now") Instant now);
}
