package backend.repositories;

import backend.models.core.WorkflowEnrollment;
import backend.models.enums.WorkflowEnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowEnrollmentRepository extends JpaRepository<WorkflowEnrollment, UUID> {

    boolean existsByWorkflowIdAndUserIdAndEnrolledAtAfter(UUID workflowId, UUID userId, Instant cutoff);

    List<WorkflowEnrollment> findByStatusAndFireAtBefore(WorkflowEnrollmentStatus status, Instant now);

    List<WorkflowEnrollment> findByWorkflowId(UUID workflowId);

    long countByWorkflowId(UUID workflowId);
}
