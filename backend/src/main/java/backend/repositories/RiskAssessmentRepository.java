package backend.repositories;

import backend.models.core.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

    /** Latest assessment for an order — powers the review detail view. */
    Optional<RiskAssessment> findTopByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
