package backend.repositories;

import backend.models.core.WorkflowEnrollment;
import backend.models.enums.WorkflowEnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowEnrollmentRepository extends JpaRepository<WorkflowEnrollment, UUID> {

    boolean existsByWorkflowIdAndUserIdAndEnrolledAtAfter(UUID workflowId, UUID userId, Instant cutoff);

    Page<WorkflowEnrollment> findByStatusAndFireAtBefore(WorkflowEnrollmentStatus status, Instant now, Pageable pageable);

    @Query("SELECT e.id FROM WorkflowEnrollment e WHERE e.status = :status AND e.fireAt < :before")
    List<UUID> findIdsByStatusAndFireAtBefore(@Param("status") WorkflowEnrollmentStatus status,
                                              @Param("before") Instant before,
                                              Pageable pageable);

    long countByWorkflowId(UUID workflowId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE WorkflowEnrollment e " +
           "SET e.status = backend.models.enums.WorkflowEnrollmentStatus.SCHEDULED, e.retryCount = 0 " +
           "WHERE e.workflowId = :workflowId AND e.status = backend.models.enums.WorkflowEnrollmentStatus.DEFERRED")
    void reactivateDeferredForWorkflow(@Param("workflowId") UUID workflowId);
}
