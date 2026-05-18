package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.OrderCompensation;
import backend.models.enums.CompensationStatus;

import java.util.List;

@Repository
public interface OrderCompensationRepository extends JpaRepository<OrderCompensation, java.util.UUID> {
    List<OrderCompensation> findAllByOrderId(java.util.UUID orderId);
    List<OrderCompensation> findAllByStatus(CompensationStatus status);
    List<OrderCompensation> findAllByStatusAndAttemptsLessThan(CompensationStatus status, int maxAttempts);

    /**
     * Atomically claims a failed compensation row for retry. Returns 1 if this caller
     * is the first to claim it, 0 if another scheduler worker already did.
     */
    @Modifying
    @Query("UPDATE OrderCompensation c SET c.status = 'CLAIMED' WHERE c.id = :id AND c.status = backend.models.enums.CompensationStatus.FAILED")
    int claimForRetry(@Param("id") java.util.UUID id);
}
