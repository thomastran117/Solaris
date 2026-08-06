package backend.repositories;

import backend.models.core.DisputeEvidence;
import backend.repositories.projections.DisputeEvidenceCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {

    List<DisputeEvidence> findAllByDisputeCaseIdOrderByCreatedAtAsc(UUID disputeCaseId);

    long countByDisputeCaseId(UUID disputeCaseId);

    /** Counts for a whole page of cases in one query, instead of one count per row. */
    @Query("SELECT e.disputeCase.id AS disputeCaseId, COUNT(e) AS count " +
           "FROM DisputeEvidence e WHERE e.disputeCase.id IN :caseIds " +
           "GROUP BY e.disputeCase.id")
    List<DisputeEvidenceCountProjection> countByDisputeCaseIds(@Param("caseIds") Collection<UUID> caseIds);
}
