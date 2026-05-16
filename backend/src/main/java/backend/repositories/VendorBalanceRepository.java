package backend.repositories;

import backend.models.core.VendorBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VendorBalanceRepository extends JpaRepository<VendorBalance, Long> {

    Optional<VendorBalance> findByVendorId(long vendorId);

    Slice<VendorBalance> findByAvailableCentsGreaterThan(long threshold, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM VendorBalance b WHERE b.vendorId = :vendorId")
    Optional<VendorBalance> findByVendorIdForUpdate(@Param("vendorId") long vendorId);

    /**
     * Upserts the pending balance for a vendor. Creates the row if it doesn't exist,
     * otherwise increments pendingCents atomically. Uses native MySQL ON DUPLICATE KEY UPDATE.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO vendor_balances
                (vendor_id, pending_cents, available_cents, in_transit_cents,
                 lifetime_gross_cents, lifetime_commission_cents, lifetime_paid_out_cents,
                 currency, updated_at)
            VALUES (:vendorId, :pendingAmount, 0, 0, :grossAmount, :commissionAmount, 0, :currency, NOW())
            ON DUPLICATE KEY UPDATE
                pending_cents              = pending_cents + :pendingAmount,
                lifetime_gross_cents       = lifetime_gross_cents + :grossAmount,
                lifetime_commission_cents  = lifetime_commission_cents + :commissionAmount,
                updated_at                 = NOW()
            """)
    void upsertPending(
            @Param("vendorId") long vendorId,
            @Param("pendingAmount") long pendingAmount,
            @Param("grossAmount") long grossAmount,
            @Param("commissionAmount") long commissionAmount,
            @Param("currency") String currency);

    /**
     * Moves amount from pendingCents to availableCents atomically.
     * Returns 1 on success, 0 if pendingCents would go negative.
     */
    @Modifying
    @Query("""
            UPDATE VendorBalance b
            SET b.availableCents = b.availableCents + :amount,
                b.pendingCents   = b.pendingCents   - :amount
            WHERE b.vendorId = :vendorId
              AND b.pendingCents >= :amount
            """)
    int releasePending(@Param("vendorId") long vendorId, @Param("amount") long amount);

    /**
     * Moves amount from availableCents to inTransitCents when a payout is dispatched.
     * Returns 1 on success, 0 if availableCents would go negative.
     */
    @Modifying
    @Query("""
            UPDATE VendorBalance b
            SET b.inTransitCents  = b.inTransitCents  + :amount,
                b.availableCents  = b.availableCents  - :amount
            WHERE b.vendorId = :vendorId
              AND b.availableCents >= :amount
            """)
    int moveToInTransit(@Param("vendorId") long vendorId, @Param("amount") long amount);

    /**
     * Confirms a payout: clears inTransitCents and increments lifetimePaidOutCents.
     */
    @Modifying
    @Query("""
            UPDATE VendorBalance b
            SET b.inTransitCents       = b.inTransitCents       - :amount,
                b.lifetimePaidOutCents = b.lifetimePaidOutCents + :amount
            WHERE b.vendorId = :vendorId
              AND b.inTransitCents >= :amount
            """)
    int confirmPayout(@Param("vendorId") long vendorId, @Param("amount") long amount);

    /**
     * Returns funds from inTransitCents back to availableCents on payout failure.
     */
    @Modifying
    @Query("""
            UPDATE VendorBalance b
            SET b.availableCents = b.availableCents + :amount,
                b.inTransitCents = b.inTransitCents - :amount
            WHERE b.vendorId = :vendorId
              AND b.inTransitCents >= :amount
            """)
    int returnFromInTransit(@Param("vendorId") long vendorId, @Param("amount") long amount);
}
