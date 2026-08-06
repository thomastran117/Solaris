package backend.repositories;

import backend.models.core.DisputeCase;
import backend.models.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeCaseRepository extends JpaRepository<DisputeCase, UUID> {

    /** Idempotency lookup — {@code stripe_dispute_id} is unique. */
    Optional<DisputeCase> findByStripeDisputeId(String stripeDisputeId);

    /** Open disputes = anything not yet CLOSED. Newest first so the freshest deadline leads. */
    Page<DisputeCase> findAllByStatusNotOrderByCreatedAtDesc(DisputeStatus status, Pageable pageable);

    List<DisputeCase> findAllByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
