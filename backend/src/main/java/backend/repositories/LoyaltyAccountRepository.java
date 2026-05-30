package backend.repositories;

import backend.models.core.LoyaltyAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, UUID> {

    Optional<LoyaltyAccount> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<LoyaltyAccount> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    Page<LoyaltyAccount> findByCompanyId(UUID companyId, Pageable pageable);

    /**
     * Atomically deducts points. Returns 1 on success, 0 if balance would go negative.
     * Caller must verify the return value.
     */
    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.pointsBalance = a.pointsBalance - :delta " +
           "WHERE a.id = :id AND a.pointsBalance >= :delta")
    int deductPoints(@Param("id") UUID id, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.pointsBalance = a.pointsBalance + :delta, " +
           "a.lifetimePoints = a.lifetimePoints + :delta WHERE a.id = :id")
    void addPoints(@Param("id") UUID id, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.pointsBalance = a.pointsBalance + :delta WHERE a.id = :id")
    void addToBalance(@Param("id") UUID id, @Param("delta") long delta);

    /**
     * Atomically promotes or demotes the account tier only if it has actually changed.
     * Handles NULL tier (no tier yet) on both sides. Returns 1 if updated, 0 if already current.
     * Using an atomic update instead of an entity save prevents two concurrent earn transactions
     * from overwriting each other's tier decision.
     */
    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.currentTierId = :tierId, a.tierUpdatedAt = :now " +
           "WHERE a.id = :id AND (" +
           "  (:tierId IS NULL AND a.currentTierId IS NOT NULL) OR " +
           "  (:tierId IS NOT NULL AND a.currentTierId != :tierId)" +
           ")")
    int updateTierIfChanged(@Param("id") UUID id, @Param("tierId") UUID tierId, @Param("now") Instant now);

    /**
     * Atomically claims the birthday reward slot for the given calendar year.
     * Returns 1 if the claim succeeded (reward not yet issued this year), 0 if already claimed.
     * Using an atomic UPDATE prevents two concurrent scheduler runs from both issuing the reward.
     */
    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.lastBirthdayRewardYear = :year " +
           "WHERE a.id = :id AND (a.lastBirthdayRewardYear IS NULL OR a.lastBirthdayRewardYear != :year)")
    int claimBirthdayRewardForYear(@Param("id") UUID id, @Param("year") int year);

    Optional<LoyaltyAccount> findByReferralCodeAndCompanyId(String referralCode, UUID companyId);

    /**
     * Sets a referral code only if none is assigned yet. Returns 1 on success, 0 if already set.
     * Used by the lazy-generation loop to avoid overwriting a code set by a concurrent request.
     */
    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.referralCode = :code WHERE a.id = :id AND a.referralCode IS NULL")
    int setReferralCodeIfAbsent(@Param("id") UUID id, @Param("code") String code);

    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.currentStreak = :streak, a.lastOrderYearMonth = :month WHERE a.id = :id")
    void updateStreak(@Param("id") UUID id, @Param("streak") int streak, @Param("month") String month);

    /**
     * Atomically marks the referral as converted. Returns 1 if this caller won the race, 0 if already converted.
     */
    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.referralConverted = true WHERE a.id = :id AND a.referralConverted = false")
    int markReferralConverted(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE LoyaltyAccount a SET a.referredByCode = :code WHERE a.id = :id AND a.referredByCode IS NULL")
    int setReferredByCodeIfAbsent(@Param("id") UUID id, @Param("code") String code);

    /** Counts how many accounts have applied the given referral code (regardless of conversion). */
    long countByReferredByCodeAndCompanyId(String referredByCode, UUID companyId);
}
