package backend.services.impl.marketing;

import backend.dtos.requests.marketing.CreateWorkflowRequest;
import backend.dtos.requests.marketing.UpdateWorkflowRequest;
import backend.dtos.responses.marketing.WorkflowAnalyticsResponse;
import backend.dtos.responses.marketing.WorkflowResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.MarketingWorkflow;
import backend.models.enums.CompanyCapability;
import backend.models.enums.WorkflowStatus;
import backend.repositories.MarketingWorkflowRepository;
import backend.repositories.WorkflowDeliveryLogRepository;
import backend.repositories.WorkflowEnrollmentRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.marketing.MarketingWorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MarketingWorkflowServiceImpl implements MarketingWorkflowService {

    private final MarketingWorkflowRepository workflowRepository;
    private final WorkflowEnrollmentRepository enrollmentRepository;
    private final WorkflowDeliveryLogRepository deliveryLogRepository;
    private final CompanyAccessService companyAccessService;

    public MarketingWorkflowServiceImpl(
            MarketingWorkflowRepository workflowRepository,
            WorkflowEnrollmentRepository enrollmentRepository,
            WorkflowDeliveryLogRepository deliveryLogRepository,
            CompanyAccessService companyAccessService) {
        this.workflowRepository = workflowRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.companyAccessService = companyAccessService;
    }

    @Override
    @Transactional
    public WorkflowResponse createWorkflow(UUID companyId, UUID ownerId, CreateWorkflowRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        MarketingWorkflow workflow = new MarketingWorkflow();
        workflow.setCompanyId(companyId);
        workflow.setName(request.name());
        workflow.setTrigger(request.trigger());
        workflow.setDelayHours(request.delayHours());
        workflow.setTargetSegmentId(request.targetSegmentId());
        workflow.setActionType(request.actionType());
        workflow.setEmailSubject(request.emailSubject());
        workflow.setEmailBody(request.emailBody());
        workflow.setCooldownDays(request.cooldownDays());
        workflow.setStatus(WorkflowStatus.ACTIVE);

        return WorkflowResponse.from(workflowRepository.save(workflow));
    }

    @Override
    @Transactional
    public WorkflowResponse updateWorkflow(UUID companyId, UUID workflowId, UUID ownerId, UpdateWorkflowRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        MarketingWorkflow workflow = workflowRepository.findById(workflowId)
                .filter(w -> w.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        if (request.status() != null) {
            workflow.setStatus(request.status());
        }
        if (request.name() != null && !request.name().isBlank()) {
            workflow.setName(request.name());
        }
        if (request.emailSubject() != null) {
            workflow.setEmailSubject(request.emailSubject());
        }
        if (request.emailBody() != null) {
            workflow.setEmailBody(request.emailBody());
        }

        return WorkflowResponse.from(workflowRepository.save(workflow));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowResponse> getWorkflows(UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);
        return workflowRepository.findByCompanyIdAndStatusNot(companyId, WorkflowStatus.ARCHIVED)
                .stream()
                .map(WorkflowResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowAnalyticsResponse getAnalytics(UUID companyId, UUID workflowId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PROMOTIONS);

        workflowRepository.findById(workflowId)
                .filter(w -> w.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        long enrolledCount = enrollmentRepository.countByWorkflowId(workflowId);

        List<UUID> enrollmentIds = enrollmentRepository.findByWorkflowId(workflowId)
                .stream().map(e -> e.getId()).toList();
        long sentCount = enrollmentIds.isEmpty() ? 0 : deliveryLogRepository.countByEnrollmentIdIn(enrollmentIds);

        return new WorkflowAnalyticsResponse(workflowId, enrolledCount, sentCount);
    }
}
