package backend.repositories;

import backend.models.core.CommissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommissionRecordRepository extends JpaRepository<CommissionRecord, java.util.UUID> {

    Optional<CommissionRecord> findBySubOrderId(java.util.UUID subOrderId);

    List<CommissionRecord> findAllByVendorId(UUID vendorId);

    List<CommissionRecord> findAllByMarketplaceId(UUID marketplaceId);

    /** Returns unreleased commission records whose hold period has expired. */
    @Query("SELECT r FROM CommissionRecord r WHERE r.holdReleased = false AND r.computedAt < :cutoff")
    List<CommissionRecord> findEligibleForRelease(@Param("cutoff") Instant cutoff);

    List<CommissionRecord> findAllByVendorIdAndHoldReleasedFalse(UUID vendorId);
}
