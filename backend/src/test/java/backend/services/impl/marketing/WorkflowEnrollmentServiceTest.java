package backend.services.impl.marketing;

import backend.kafka.producers.NotificationEventPublisher;
import backend.models.core.LoyaltyAccount;
import backend.models.core.MarketingWorkflow;
import backend.models.core.User;
import backend.models.core.UserPreference;
import backend.models.core.WorkflowDeliveryLog;
import backend.models.core.WorkflowEnrollment;
import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowDeliveryStatus;
import backend.models.enums.WorkflowEnrollmentStatus;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;
import backend.repositories.LoyaltyAccountRepository;
import backend.repositories.MarketingWorkflowRepository;
import backend.repositories.UserPreferenceRepository;
import backend.repositories.UserRepository;
import backend.repositories.WorkflowDeliveryLogRepository;
import backend.repositories.WorkflowEnrollmentRepository;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

    // Self-reference is typed to the impl class since processOneEnrollment / scheduleEnrollmentInNewTx
    // / incrementRetryOrMarkFailed are not on the public WorkflowEnrollmentService interface.
    @Mock WorkflowEnrollmentServiceImpl enrollmentServiceProxy;

    WorkflowEnrollmentServiceImpl service;

    static final UUID COMPANY_ID    = TestIds.uuid(1);
    static final UUID USER_ID       = TestIds.uuid(2);
    static final UUID WORKFLOW_ID   = TestIds.uuid(3);
    static final UUID ENROLLMENT_ID = TestIds.uuid(10);

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
    void enrol_noActiveWorkflowsForTrigger_skipsEnrollment() {
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
    void processScheduledEnrollments_delegatesToProcessOneEnrollment() {
        when(enrollmentRepository.findIdsByStatusAndFireAtBefore(
                eq(WorkflowEnrollmentStatus.SCHEDULED), any(), any()))
                .thenReturn(List.of(ENROLLMENT_ID));

        service.processScheduledEnrollments();

        verify(enrollmentServiceProxy).processOneEnrollment(ENROLLMENT_ID);
    }

    @Test
    void processScheduledEnrollments_skipsWhenNoDueEnrollments() {
        when(enrollmentRepository.findIdsByStatusAndFireAtBefore(any(), any(), any()))
                .thenReturn(List.of());

        service.processScheduledEnrollments();

        verify(enrollmentServiceProxy, never()).processOneEnrollment(any());
    }

    @Test
    void processScheduledEnrollments_callsIncrementRetryOrMarkFailed_onProcessingFailure() {
        when(enrollmentRepository.findIdsByStatusAndFireAtBefore(
                eq(WorkflowEnrollmentStatus.SCHEDULED), any(), any()))
                .thenReturn(List.of(ENROLLMENT_ID));
        doThrow(new RuntimeException("db error")).when(enrollmentServiceProxy).processOneEnrollment(ENROLLMENT_ID);

        service.processScheduledEnrollments();

        verify(enrollmentServiceProxy).incrementRetryOrMarkFailed(ENROLLMENT_ID);
    }

    // ─── processOneEnrollment ─────────────────────────────────────────────────

    @Test
    void processOneEnrollment_firesEmailForActiveWorkflow() {
        MarketingWorkflow wf = stubWorkflow(0, 0, null);
        wf.setEmailSubject("Review us");
        wf.setEmailBody("<p>Hi</p>");

        WorkflowEnrollment enrollment = scheduledEnrollment();
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(wf));

        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setFirstName("Alice");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processOneEnrollment(ENROLLMENT_ID);

        ArgumentCaptor<WorkflowEnrollment> enrollCaptor = ArgumentCaptor.forClass(WorkflowEnrollment.class);
        verify(enrollmentRepository).save(enrollCaptor.capture());
        assertThat(enrollCaptor.getValue().getStatus()).isEqualTo(WorkflowEnrollmentStatus.SENT);

        ArgumentCaptor<WorkflowDeliveryLog> logCaptor = ArgumentCaptor.forClass(WorkflowDeliveryLog.class);
        verify(deliveryLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(WorkflowDeliveryStatus.SENT);

        // isSynchronizationActive() is false in unit tests, so EmailServiceImpl.publish()
        // calls doSend() directly — but here emailService is mocked so we just verify the call.
        verify(emailService).sendMarketingWorkflowEmail(
                eq("test@example.com"), eq("Alice"), eq(WORKFLOW_ID),
                eq(COMPANY_ID), eq("Review us"), eq("<p>Hi</p>"));
    }

    @Test
    void processOneEnrollment_pausedWorkflow_setsDeferredStatus() {
        WorkflowEnrollment enrollment = scheduledEnrollment();
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));

        MarketingWorkflow wf = stubWorkflow(0, 0, null);
        wf.setStatus(WorkflowStatus.PAUSED);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(wf));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processOneEnrollment(ENROLLMENT_ID);

        ArgumentCaptor<WorkflowEnrollment> captor = ArgumentCaptor.forClass(WorkflowEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowEnrollmentStatus.DEFERRED);
        verify(emailService, never()).sendMarketingWorkflowEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void processOneEnrollment_archivedWorkflow_cancelsEnrollment() {
        WorkflowEnrollment enrollment = scheduledEnrollment();
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));

        MarketingWorkflow wf = stubWorkflow(0, 0, null);
        wf.setStatus(WorkflowStatus.ARCHIVED);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(wf));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processOneEnrollment(ENROLLMENT_ID);

        ArgumentCaptor<WorkflowEnrollment> captor = ArgumentCaptor.forClass(WorkflowEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowEnrollmentStatus.CANCELLED);
        verify(emailService, never()).sendMarketingWorkflowEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void processOneEnrollment_alreadySentEnrollment_skips() {
        WorkflowEnrollment enrollment = scheduledEnrollment();
        enrollment.setStatus(WorkflowEnrollmentStatus.SENT);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));

        service.processOneEnrollment(ENROLLMENT_ID);

        verify(workflowRepository, never()).findById(any());
        verify(emailService, never()).sendMarketingWorkflowEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void processOneEnrollment_missingWorkflow_cancelsEnrollment() {
        WorkflowEnrollment enrollment = scheduledEnrollment();
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.empty());
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processOneEnrollment(ENROLLMENT_ID);

        ArgumentCaptor<WorkflowEnrollment> captor = ArgumentCaptor.forClass(WorkflowEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowEnrollmentStatus.CANCELLED);
    }

    // ─── incrementRetryOrMarkFailed ───────────────────────────────────────────

    @Test
    void incrementRetryOrMarkFailed_incrementsRetryCount() {
        WorkflowEnrollment enrollment = scheduledEnrollment();
        enrollment.setRetryCount(1);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.incrementRetryOrMarkFailed(ENROLLMENT_ID);

        ArgumentCaptor<WorkflowEnrollment> captor = ArgumentCaptor.forClass(WorkflowEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getRetryCount()).isEqualTo(2);
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowEnrollmentStatus.SCHEDULED);
    }

    @Test
    void incrementRetryOrMarkFailed_setsFailedAfterMaxRetries() {
        WorkflowEnrollment enrollment = scheduledEnrollment();
        enrollment.setRetryCount(2);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.incrementRetryOrMarkFailed(ENROLLMENT_ID);

        ArgumentCaptor<WorkflowEnrollment> captor = ArgumentCaptor.forClass(WorkflowEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getRetryCount()).isEqualTo(3);
        assertThat(captor.getValue().getStatus()).isEqualTo(WorkflowEnrollmentStatus.FAILED);
    }

    @Test
    void incrementRetryOrMarkFailed_skipsNonScheduledEnrollment() {
        WorkflowEnrollment enrollment = scheduledEnrollment();
        enrollment.setStatus(WorkflowEnrollmentStatus.SENT);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));

        service.incrementRetryOrMarkFailed(ENROLLMENT_ID);

        verify(enrollmentRepository, never()).save(any());
    }

    // ─── dailyBirthdayEnrol ───────────────────────────────────────────────────

    @Test
    void dailyBirthdayEnrol_callsEnrolForEachUserAndCompany() {
        MarketingWorkflow wf = stubWorkflow(0, 0, null);
        wf.setTrigger(WorkflowTrigger.CUSTOMER_BIRTHDAY);
        when(workflowRepository.findByTriggerAndStatus(WorkflowTrigger.CUSTOMER_BIRTHDAY, WorkflowStatus.ACTIVE))
                .thenReturn(List.of(wf));

        UserPreference pref = new UserPreference();
        pref.setUserId(USER_ID);
        when(userPreferenceRepository.findByBirthDateMonthAndDay(anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pref)));

        service.dailyBirthdayEnrol();

        verify(enrollmentServiceProxy).enrol(WorkflowTrigger.CUSTOMER_BIRTHDAY, COMPANY_ID, USER_ID);
    }

    @Test
    void dailyBirthdayEnrol_noWorkflows_skips() {
        when(workflowRepository.findByTriggerAndStatus(WorkflowTrigger.CUSTOMER_BIRTHDAY, WorkflowStatus.ACTIVE))
                .thenReturn(List.of());

        service.dailyBirthdayEnrol();

        verifyNoInteractions(userPreferenceRepository, enrollmentServiceProxy);
    }

    // ─── dailyWinBackEnrol ────────────────────────────────────────────────────

    @Test
    void dailyWinBackEnrol_schedulesEnrollmentsForInactiveUsersUsingLessThanEqual() {
        MarketingWorkflow wf = stubWorkflow(720, 0, null); // 720h = 30 days inactivity
        wf.setTrigger(WorkflowTrigger.DAYS_SINCE_LAST_ORDER);
        when(workflowRepository.findByTriggerAndStatus(WorkflowTrigger.DAYS_SINCE_LAST_ORDER, WorkflowStatus.ACTIVE))
                .thenReturn(List.of(wf));

        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(USER_ID);
        // Verify the repository call uses the LessThanEqual variant (fixes #2: exact equality missed older users).
        when(loyaltyAccountRepository.findByCompanyIdAndLastOrderYearMonthLessThanEqual(
                eq(COMPANY_ID), any(String.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));

        service.dailyWinBackEnrol();

        // Verify self.scheduleEnrollmentInNewTx is used (fixes TOCTOU race, issue #6).
        verify(enrollmentServiceProxy).scheduleEnrollmentInNewTx(eq(wf), eq(USER_ID), any(Instant.class));
    }

    @Test
    void dailyWinBackEnrol_noWorkflows_skips() {
        when(workflowRepository.findByTriggerAndStatus(WorkflowTrigger.DAYS_SINCE_LAST_ORDER, WorkflowStatus.ACTIVE))
                .thenReturn(List.of());

        service.dailyWinBackEnrol();

        verifyNoInteractions(loyaltyAccountRepository);
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

    private WorkflowEnrollment scheduledEnrollment() {
        WorkflowEnrollment e = new WorkflowEnrollment();
        e.setId(ENROLLMENT_ID);
        e.setWorkflowId(WORKFLOW_ID);
        e.setUserId(USER_ID);
        e.setEnrolledAt(Instant.now().minusSeconds(100));
        e.setFireAt(Instant.now().minusSeconds(10));
        e.setStatus(WorkflowEnrollmentStatus.SCHEDULED);
        return e;
    }
}
