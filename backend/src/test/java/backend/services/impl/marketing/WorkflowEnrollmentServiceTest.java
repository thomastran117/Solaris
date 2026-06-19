package backend.services.impl.marketing;

import backend.kafka.producers.NotificationEventPublisher;
import backend.models.core.MarketingWorkflow;
import backend.models.core.User;
import backend.models.core.WorkflowEnrollment;
import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowEnrollmentStatus;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;
import backend.repositories.LoyaltyAccountRepository;
import backend.repositories.MarketingWorkflowRepository;
import backend.repositories.UserPreferenceRepository;
import backend.repositories.UserRepository;
import backend.repositories.WorkflowDeliveryLogRepository;
import backend.repositories.WorkflowEnrollmentRepository;
import backend.services.intf.marketing.WorkflowEnrollmentService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEnrollmentServiceTest {

    @Mock MarketingWorkflowRepository workflowRepository;
    @Mock WorkflowEnrollmentRepository enrollmentRepository;
    @Mock WorkflowDeliveryLogRepository deliveryLogRepository;
    @Mock UserRepository userRepository;
    @Mock UserPreferenceRepository userPreferenceRepository;
    @Mock LoyaltyAccountRepository loyaltyAccountRepository;
    @Mock EmailService emailService;
    @Mock NotificationEventPublisher notificationEventPublisher;
    @Mock WorkflowEnrollmentService enrollmentServiceProxy;

    WorkflowEnrollmentServiceImpl service;

    static final UUID COMPANY_ID  = TestIds.uuid(1);
    static final UUID USER_ID     = TestIds.uuid(2);
    static final UUID WORKFLOW_ID = TestIds.uuid(3);

    @BeforeEach
    void setUp() {
        service = new WorkflowEnrollmentServiceImpl(
                workflowRepository, enrollmentRepository, deliveryLogRepository,
                userRepository, userPreferenceRepository, loyaltyAccountRepository,
                emailService, notificationEventPublisher);
        service.setSelf(enrollmentServiceProxy);
    }

    // ─── enrol ───────────────────────────────────────────────────────────────

    @Test
    void enrol_activeWorkflow_createsEnrollment() {
        MarketingWorkflow wf = stubWorkflow(0, 0, null);
        when(workflowRepository.findByTriggerAndStatusAndCompanyId(
                WorkflowTrigger.ORDER_DELIVERED, WorkflowStatus.ACTIVE, COMPANY_ID))
                .thenReturn(List.of(wf));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.enrol(WorkflowTrigger.ORDER_DELIVERED, COMPANY_ID, USER_ID);

        ArgumentCaptor<WorkflowEnrollment> captor = ArgumentCaptor.forClass(WorkflowEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getWorkflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowEnrollmentStatus.SCHEDULED);
    }

    @Test
    void enrol_cooldownActive_skipsEnrollment() {
        MarketingWorkflow wf = stubWorkflow(0, 30, null);
        when(workflowRepository.findByTriggerAndStatusAndCompanyId(any(), any(), any()))
                .thenReturn(List.of(wf));
        when(enrollmentRepository.existsByWorkflowIdAndUserIdAndEnrolledAtAfter(
                eq(WORKFLOW_ID), eq(USER_ID), any(Instant.class))).thenReturn(true);

        service.enrol(WorkflowTrigger.ORDER_DELIVERED, COMPANY_ID, USER_ID);

        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enrol_pausedWorkflow_skipsEnrollment() {
        when(workflowRepository.findByTriggerAndStatusAndCompanyId(any(), any(), any()))
                .thenReturn(List.of());

        service.enrol(WorkflowTrigger.ORDER_DELIVERED, COMPANY_ID, USER_ID);

        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enrol_segmentFilterMismatch_skipsEnrollment() {
        UUID segmentId = TestIds.uuid(99);
        MarketingWorkflow wf = stubWorkflow(0, 0, segmentId);
        when(workflowRepository.findByTriggerAndStatusAndCompanyId(any(), any(), any()))
                .thenReturn(List.of(wf));

        User user = new User();
        user.setId(USER_ID);
        user.setSegments(new HashSet<>());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.enrol(WorkflowTrigger.ORDER_DELIVERED, COMPANY_ID, USER_ID);

        verify(enrollmentRepository, never()).save(any());
    }

    // ─── processScheduledEnrollments ─────────────────────────────────────────

    @Test
    void processScheduledEnrollments_firesEmailForDueEnrollments() {
        MarketingWorkflow wf = stubWorkflow(0, 0, null);
        wf.setEmailSubject("Review us");
        wf.setEmailBody("<p>Hi</p>");

        WorkflowEnrollment enrollment = new WorkflowEnrollment();
        enrollment.setId(TestIds.uuid(10));
        enrollment.setWorkflowId(WORKFLOW_ID);
        enrollment.setUserId(USER_ID);
        enrollment.setEnrolledAt(Instant.now().minusSeconds(100));
        enrollment.setFireAt(Instant.now().minusSeconds(10));
        enrollment.setStatus(WorkflowEnrollmentStatus.SCHEDULED);

        when(enrollmentRepository.findByStatusAndFireAtBefore(
                eq(WorkflowEnrollmentStatus.SCHEDULED), any(), any()))
                .thenReturn(new PageImpl<>(List.of(enrollment)));
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(wf));

        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setFirstName("Alice");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processScheduledEnrollments();

        verify(emailService).sendMarketingWorkflowEmail(
                eq("test@example.com"), eq("Alice"), eq(WORKFLOW_ID),
                eq(COMPANY_ID), eq("Review us"), eq("<p>Hi</p>"));

        ArgumentCaptor<WorkflowEnrollment> captor = ArgumentCaptor.forClass(WorkflowEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowEnrollmentStatus.SENT);
    }

    @Test
    void processScheduledEnrollments_skipsNotYetDue() {
        when(enrollmentRepository.findByStatusAndFireAtBefore(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.processScheduledEnrollments();

        verify(emailService, never()).sendMarketingWorkflowEmail(any(), any(), any(), any(), any(), any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private MarketingWorkflow stubWorkflow(int delayHours, int cooldownDays, UUID targetSegmentId) {
        MarketingWorkflow w = new MarketingWorkflow();
        w.setId(WORKFLOW_ID);
        w.setCompanyId(COMPANY_ID);
        w.setName("Post-delivery");
        w.setTrigger(WorkflowTrigger.ORDER_DELIVERED);
        w.setDelayHours(delayHours);
        w.setTargetSegmentId(targetSegmentId);
        w.setActionType(WorkflowActionType.EMAIL);
        w.setCooldownDays(cooldownDays);
        w.setStatus(WorkflowStatus.ACTIVE);
        return w;
    }
}
