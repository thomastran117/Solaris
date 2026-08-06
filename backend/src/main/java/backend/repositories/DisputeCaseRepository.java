package backend.repositories;

import backend.models.core.DisputeCase;
import backend.models.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeCaseRepository extends JpaRepository<DisputeCase, UUID> {

    /** Idempotency lookup — {@code stripe_dispute_id} is unique. */
    Optional<DisputeCase> findByStripeDisputeId(String stripeDisputeId);

    /**
     * Open disputes = anything not yet CLOSED, ordered by urgency: soonest evidence deadline
     * first. This is a work queue, so page 1 must hold the cases closest to expiring — ordering
     * by {@code createdAt} would bury them on the last page once there are more than a page's
     * worth. Cases with no deadline sort last (nothing is expiring), with the newest first as a
     * tiebreak.
     */
    @Query("SELECT c FROM DisputeCase c WHERE c.status <> :status "
            + "ORDER BY c.evidenceDeadline ASC NULLS LAST, c.createdAt DESC")
    Page<DisputeCase> findOpenByDeadline(@Param("status") DisputeStatus status, Pageable pageable);

    List<DisputeCase> findAllByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
