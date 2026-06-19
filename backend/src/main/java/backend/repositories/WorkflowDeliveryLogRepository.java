package backend.repositories;

import backend.models.core.WorkflowDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WorkflowDeliveryLogRepository extends JpaRepository<WorkflowDeliveryLog, UUID> {

    @Query("SELECT COUNT(d) FROM WorkflowDeliveryLog d WHERE d.enrollmentId IN " +
           "(SELECT e.id FROM WorkflowEnrollment e WHERE e.workflowId = :workflowId)")
    long countByWorkflowId(@Param("workflowId") UUID workflowId);
}
