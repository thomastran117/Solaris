package backend.repositories;

import backend.models.core.WorkflowEnrollment;
import backend.models.enums.WorkflowEnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface WorkflowEnrollmentRepository extends JpaRepository<WorkflowEnrollment, UUID> {

    boolean existsByWorkflowIdAndUserIdAndEnrolledAtAfter(UUID workflowId, UUID userId, Instant cutoff);

    Page<WorkflowEnrollment> findByStatusAndFireAtBefore(WorkflowEnrollmentStatus status, Instant now, Pageable pageable);

    long countByWorkflowId(UUID workflowId);
}
