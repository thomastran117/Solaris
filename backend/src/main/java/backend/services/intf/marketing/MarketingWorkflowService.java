package backend.services.intf.marketing;

import backend.dtos.requests.marketing.CreateWorkflowRequest;
import backend.dtos.requests.marketing.UpdateWorkflowRequest;
import backend.dtos.responses.marketing.WorkflowAnalyticsResponse;
import backend.dtos.responses.marketing.WorkflowResponse;
import backend.dtos.responses.marketing.WorkflowSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface MarketingWorkflowService {

    WorkflowResponse createWorkflow(UUID companyId, UUID ownerId, CreateWorkflowRequest request);

    WorkflowResponse updateWorkflow(UUID companyId, UUID workflowId, UUID ownerId, UpdateWorkflowRequest request);

    List<WorkflowSummaryResponse> getWorkflows(UUID companyId, UUID ownerId);

    WorkflowAnalyticsResponse getAnalytics(UUID companyId, UUID workflowId, UUID ownerId);
}
