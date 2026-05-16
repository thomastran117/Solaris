package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.CustomerCredit;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerCreditRepository extends JpaRepository<CustomerCredit, Long> {

    List<CustomerCredit> findAllByUserIdOrderByCreatedAtDesc(long userId);

    Optional<CustomerCredit> findFirstBySourceOrderIssueId(Long sourceOrderIssueId);

    /** Sum of all non-expired, non-reversed credit entries for a user — the current balance. */
    @Query("SELECT COALESCE(SUM(c.amountCents), 0) FROM CustomerCredit c " +
           "WHERE c.user.id = :userId " +
           "AND c.type NOT IN ('EXPIRED', 'REVERSED') " +
           "AND (c.expiresAt IS NULL OR c.expiresAt > :now)")
    long sumBalanceByUserId(@Param("userId") long userId, @Param("now") Instant now);

    /** Pessimistic lock on all entries for a user — used during redemption to prevent double-spend. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CustomerCredit c WHERE c.user.id = :userId")
    List<CustomerCredit> findAllByUserIdForUpdate(@Param("userId") long userId);

    /** Credits that have passed their expiry date and haven't been expired yet. */
    @Query("SELECT c FROM CustomerCredit c " +
           "WHERE c.expiresAt IS NOT NULL AND c.expiresAt <= :now " +
           "AND c.type NOT IN ('EXPIRED', 'REVERSED', 'REDEEMED')")
    List<CustomerCredit> findExpiredCredits(@Param("now") Instant now);

    /**
     * Atomically claims a credit entry for reversal by flipping its type to REVERSED.
     * Returns 1 if this caller won the race, 0 if it was already reversed or expired.
     */
    @Modifying
    @Query("UPDATE CustomerCredit c SET c.type = backend.models.enums.CreditEntryType.REVERSED " +
           "WHERE c.id = :id AND c.type NOT IN (backend.models.enums.CreditEntryType.REVERSED, backend.models.enums.CreditEntryType.EXPIRED)")
    int claimForReversal(@Param("id") long id);

    /**
     * Atomically claims a credit entry for expiry by flipping its type to EXPIRED.
     * Returns 1 if this caller won the race, 0 if it was already expired or reversed.
     */
    @Modifying
    @Query("UPDATE CustomerCredit c SET c.type = backend.models.enums.CreditEntryType.EXPIRED " +
           "WHERE c.id = :id AND c.type NOT IN (backend.models.enums.CreditEntryType.EXPIRED, backend.models.enums.CreditEntryType.REVERSED, backend.models.enums.CreditEntryType.REDEEMED)")
    int claimForExpiry(@Param("id") long id);
}
