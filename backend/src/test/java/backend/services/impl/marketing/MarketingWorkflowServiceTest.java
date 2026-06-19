package backend.services.impl.marketing;

import backend.dtos.requests.marketing.CreateWorkflowRequest;
import backend.dtos.requests.marketing.UpdateWorkflowRequest;
import backend.dtos.responses.marketing.WorkflowResponse;
import backend.dtos.responses.marketing.WorkflowSummaryResponse;
import org.springframework.data.domain.Page;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.MarketingWorkflow;
import backend.models.enums.CompanyCapability;
import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;
import backend.repositories.MarketingWorkflowRepository;
import backend.repositories.WorkflowDeliveryLogRepository;
import backend.repositories.WorkflowEnrollmentRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketingWorkflowServiceTest {

    @Mock MarketingWorkflowRepository workflowRepository;
    @Mock WorkflowEnrollmentRepository enrollmentRepository;
    @Mock WorkflowDeliveryLogRepository deliveryLogRepository;
    @Mock CompanyAccessService companyAccessService;

    MarketingWorkflowServiceImpl service;

    static final UUID COMPANY_ID  = TestIds.uuid(1);
    static final UUID OWNER_ID    = TestIds.uuid(2);
    static final UUID WORKFLOW_ID = TestIds.uuid(3);

    @BeforeEach
    void setUp() {
        service = new MarketingWorkflowServiceImpl(
                workflowRepository, enrollmentRepository, deliveryLogRepository, companyAccessService);
    }

    // ─── createWorkflow ───────────────────────────────────────────────────────

    @Test
    void createWorkflow_savesAndReturnsResponse() {
        stubCompanyAccess();
        CreateWorkflowRequest req = new CreateWorkflowRequest(
                "Post-delivery", WorkflowTrigger.ORDER_DELIVERED, 72,
                null, WorkflowActionType.EMAIL, "How was it?", "<p>Hi</p>", 30);

        MarketingWorkflow saved = stubWorkflow(WorkflowStatus.ACTIVE);
        when(workflowRepository.save(any())).thenReturn(saved);

        WorkflowResponse response = service.createWorkflow(COMPANY_ID, OWNER_ID, req);

        assertThat(response.status()).isEqualTo(WorkflowStatus.ACTIVE);
        assertThat(response.trigger()).isEqualTo(WorkflowTrigger.ORDER_DELIVERED);
        verify(workflowRepository).save(any());
    }

    @Test
    void createWorkflow_unauthorizedUser_throwsForbidden() {
        doThrow(new ForbiddenException("denied"))
                .when(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_PROMOTIONS);

        CreateWorkflowRequest req = new CreateWorkflowRequest(
                "x", WorkflowTrigger.ORDER_DELIVERED, 0, null, WorkflowActionType.EMAIL, null, null, 0);

        assertThatThrownBy(() -> service.createWorkflow(COMPANY_ID, OWNER_ID, req))
                .isInstanceOf(ForbiddenException.class);
        verify(workflowRepository, never()).save(any());
    }

    // ─── updateWorkflow ───────────────────────────────────────────────────────

    @Test
    void updateWorkflow_archive_setsArchivedStatus() {
        stubCompanyAccess();
        MarketingWorkflow existing = stubWorkflow(WorkflowStatus.ACTIVE);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(existing));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowResponse response = service.updateWorkflow(
                COMPANY_ID, WORKFLOW_ID, OWNER_ID, new UpdateWorkflowRequest(WorkflowStatus.ARCHIVED, null, null, null));

        assertThat(response.status()).isEqualTo(WorkflowStatus.ARCHIVED);
    }

    @Test
    void updateWorkflow_notFound_throwsResourceNotFound() {
        stubCompanyAccess();
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateWorkflow(
                COMPANY_ID, WORKFLOW_ID, OWNER_ID,
                new UpdateWorkflowRequest(WorkflowStatus.PAUSED, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWorkflow_wrongCompany_throwsResourceNotFound() {
        stubCompanyAccess();
        UUID otherCompanyId = TestIds.uuid(99);
        MarketingWorkflow existing = stubWorkflow(WorkflowStatus.ACTIVE);
        existing.setCompanyId(otherCompanyId);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateWorkflow(
                COMPANY_ID, WORKFLOW_ID, OWNER_ID,
                new UpdateWorkflowRequest(WorkflowStatus.PAUSED, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getWorkflows ────────────────────────────────────────────────────────

    @Test
    void getWorkflows_returnsNonArchivedList() {
        stubCompanyAccess();
        when(workflowRepository.findByCompanyIdAndStatusNot(
                eq(COMPANY_ID), eq(WorkflowStatus.ARCHIVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stubWorkflow(WorkflowStatus.ACTIVE))));

        Page<WorkflowSummaryResponse> result = service.getWorkflows(COMPANY_ID, OWNER_ID, 0, 50);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(WORKFLOW_ID);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void stubCompanyAccess() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), eq(CompanyCapability.MANAGE_PROMOTIONS)))
                .thenReturn(c);
    }

    private MarketingWorkflow stubWorkflow(WorkflowStatus status) {
        MarketingWorkflow w = new MarketingWorkflow();
        w.setId(WORKFLOW_ID);
        w.setCompanyId(COMPANY_ID);
        w.setName("Post-delivery");
        w.setTrigger(WorkflowTrigger.ORDER_DELIVERED);
        w.setDelayHours(72);
        w.setActionType(WorkflowActionType.EMAIL);
        w.setEmailSubject("How was it?");
        w.setCooldownDays(30);
        w.setStatus(status);
        w.setCreatedAt(Instant.now());
        w.setUpdatedAt(Instant.now());
        return w;
    }
}
