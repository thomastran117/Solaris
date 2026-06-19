package backend.repositories;

import backend.models.core.MarketingWorkflow;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketingWorkflowRepository extends JpaRepository<MarketingWorkflow, UUID> {

    List<MarketingWorkflow> findByCompanyIdAndStatusNot(UUID companyId, WorkflowStatus excluded);

    List<MarketingWorkflow> findByTriggerAndStatus(WorkflowTrigger trigger, WorkflowStatus status);

    List<MarketingWorkflow> findByTriggerAndStatusAndCompanyId(WorkflowTrigger trigger, WorkflowStatus status, UUID companyId);
}
