package backend.services.impl.marketing;

import backend.events.notification.NotificationEvent;
import backend.kafka.producers.NotificationEventPublisher;
import backend.models.core.LoyaltyAccount;
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
import backend.services.intf.marketing.WorkflowEnrollmentService;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkflowEnrollmentServiceImpl implements WorkflowEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEnrollmentServiceImpl.class);
    private static final int SCHEDULER_BATCH_SIZE = 500;

    private final MarketingWorkflowRepository workflowRepository;
    private final WorkflowEnrollmentRepository enrollmentRepository;
    private final WorkflowDeliveryLogRepository deliveryLogRepository;
    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final EmailService emailService;
    private final NotificationEventPublisher notificationEventPublisher;

    // Lazy self-reference so calls from scheduled methods go through the Spring AOP proxy,
    // ensuring @Transactional(REQUIRES_NEW) on enrol() and processOneEnrollment() are honoured.
    private WorkflowEnrollmentService self;

    public WorkflowEnrollmentServiceImpl(
            MarketingWorkflowRepository workflowRepository,
            WorkflowEnrollmentRepository enrollmentRepository,
            WorkflowDeliveryLogRepository deliveryLogRepository,
            UserRepository userRepository,
            UserPreferenceRepository userPreferenceRepository,
            LoyaltyAccountRepository loyaltyAccountRepository,
            EmailService emailService,
            NotificationEventPublisher notificationEventPublisher) {
        this.workflowRepository = workflowRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.emailService = emailService;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Autowired
    public void setSelf(@Lazy WorkflowEnrollmentService self) {
        this.self = self;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enrol(WorkflowTrigger trigger, UUID companyId, UUID userId) {
        List<backend.models.core.MarketingWorkflow> workflows =
                workflowRepository.findByTriggerAndStatusAndCompanyId(trigger, WorkflowStatus.ACTIVE, companyId);
        for (backend.models.core.MarketingWorkflow workflow : workflows) {
            scheduleEnrollment(workflow, userId);
        }
    }

    private void scheduleEnrollment(backend.models.core.MarketingWorkflow workflow, UUID userId) {
        scheduleEnrollment(workflow, userId, Instant.now().plus(workflow.getDelayHours(), ChronoUnit.HOURS));
    }

    private void scheduleEnrollment(backend.models.core.MarketingWorkflow workflow, UUID userId, Instant fireAt) {
        if (!passesSegmentFilter(workflow, userId)) return;
        if (!passesCooldown(workflow, userId)) return;
        WorkflowEnrollment enrollment = new WorkflowEnrollment();
        enrollment.setWorkflowId(workflow.getId());
        enrollment.setUserId(userId);
        enrollment.setEnrolledAt(Instant.now());
        enrollment.setFireAt(fireAt);
        enrollment.setStatus(WorkflowEnrollmentStatus.SCHEDULED);
        enrollmentRepository.save(enrollment);
        log.debug("[WORKFLOW] Enrolled userId={} in workflowId={} fireAt={}", userId, workflow.getId(), enrollment.getFireAt());
    }

    // Fetch due enrollment IDs; each is processed in its own REQUIRES_NEW transaction via
    // self.processOneEnrollment(). A failure in one enrollment cannot roll back others.
    @Override
    @Scheduled(fixedDelay = 60_000)
    public void processScheduledEnrollments() {
        List<UUID> dueIds = enrollmentRepository.findIdsByStatusAndFireAtBefore(
                WorkflowEnrollmentStatus.SCHEDULED, Instant.now(), PageRequest.of(0, SCHEDULER_BATCH_SIZE));

        for (UUID id : dueIds) {
            try {
                self.processOneEnrollment(id);
            } catch (Exception e) {
                log.error("[WORKFLOW] Failed to process enrollment id={}: {}", id, e.getMessage(), e);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOneEnrollment(UUID enrollmentId) {
        WorkflowEnrollment enrollment = enrollmentRepository.findById(enrollmentId).orElse(null);
        if (enrollment == null || enrollment.getStatus() != WorkflowEnrollmentStatus.SCHEDULED) return;

        backend.models.core.MarketingWorkflow workflow =
                workflowRepository.findById(enrollment.getWorkflowId()).orElse(null);

        if (workflow == null || workflow.getStatus() == WorkflowStatus.ARCHIVED) {
            enrollment.setStatus(WorkflowEnrollmentStatus.CANCELLED);
            enrollmentRepository.save(enrollment);
            return;
        }
        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            // PAUSED — keep SCHEDULED so the enrollment fires after the workflow is resumed.
            return;
        }

        User user = userRepository.findById(enrollment.getUserId()).orElse(null);
        if (user == null) {
            enrollment.setStatus(WorkflowEnrollmentStatus.CANCELLED);
            enrollmentRepository.save(enrollment);
            return;
        }

        // Write DB state before dispatching; if save throws the enrollment stays SCHEDULED
        // and will be retried on the next tick — no Kafka message will have been sent.
        enrollment.setStatus(WorkflowEnrollmentStatus.SENT);
        enrollmentRepository.save(enrollment);
        deliveryLogRepository.save(new WorkflowDeliveryLog(
                enrollment.getId(), workflow.getActionType(), WorkflowDeliveryStatus.SENT, Instant.now()));

        if (workflow.getActionType() == WorkflowActionType.EMAIL) {
            final String toEmail = user.getEmail();
            final String firstName = user.getFirstName();
            final UUID workflowId = workflow.getId();
            final UUID companyId = workflow.getCompanyId();
            final String subject = workflow.getEmailSubject();
            final String body = workflow.getEmailBody() != null ? workflow.getEmailBody() : "";
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        emailService.sendMarketingWorkflowEmail(toEmail, firstName, workflowId, companyId, subject, body);
                    }
                });
            } else {
                emailService.sendMarketingWorkflowEmail(toEmail, firstName, workflowId, companyId, subject, body);
            }
        } else if (workflow.getActionType() == WorkflowActionType.PUSH) {
            notificationEventPublisher.publish(new NotificationEvent.MarketingWorkflowPush(
                    user.getId(),
                    workflow.getId(),
                    workflow.getEmailSubject() != null ? workflow.getEmailSubject() : "Message",
                    workflow.getEmailBody() != null ? workflow.getEmailBody() : ""));
        }
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void dailyBirthdayEnrol() {
        LocalDate today = LocalDate.now();
        List<backend.models.core.MarketingWorkflow> workflows =
                workflowRepository.findByTriggerAndStatus(WorkflowTrigger.CUSTOMER_BIRTHDAY, WorkflowStatus.ACTIVE);
        if (workflows.isEmpty()) return;

        Set<UUID> companyIds = workflows.stream()
                .map(backend.models.core.MarketingWorkflow::getCompanyId)
                .collect(Collectors.toSet());

        int page = 0;
        List<UserPreference> chunk;
        do {
            chunk = userPreferenceRepository.findByBirthDateMonthAndDay(
                    today.getMonthValue(), today.getDayOfMonth(),
                    PageRequest.of(page++, SCHEDULER_BATCH_SIZE)).getContent();
            for (UserPreference pref : chunk) {
                for (UUID companyId : companyIds) {
                    try {
                        self.enrol(WorkflowTrigger.CUSTOMER_BIRTHDAY, companyId, pref.getUserId());
                    } catch (Exception e) {
                        log.error("[WORKFLOW] Birthday enrol failed userId={} companyId={}: {}",
                                pref.getUserId(), companyId, e.getMessage());
                    }
                }
            }
        } while (chunk.size() == SCHEDULER_BATCH_SIZE);
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void dailyWinBackEnrol() {
        List<backend.models.core.MarketingWorkflow> workflows =
                workflowRepository.findByTriggerAndStatus(WorkflowTrigger.DAYS_SINCE_LAST_ORDER, WorkflowStatus.ACTIVE);
        if (workflows.isEmpty()) return;

        for (backend.models.core.MarketingWorkflow workflow : workflows) {
            int inactiveDays = workflow.getDelayHours() / 24;
            if (inactiveDays <= 0) continue;

            LocalDate cutoffDate = LocalDate.now().minusDays(inactiveDays);
            String cutoffYearMonth = String.format("%04d-%02d", cutoffDate.getYear(), cutoffDate.getMonthValue());

            int page = 0;
            List<LoyaltyAccount> chunk;
            do {
                chunk = loyaltyAccountRepository.findByCompanyIdAndLastOrderYearMonth(
                        workflow.getCompanyId(), cutoffYearMonth,
                        PageRequest.of(page++, SCHEDULER_BATCH_SIZE)).getContent();
                for (LoyaltyAccount account : chunk) {
                    try {
                        // fireAt = now: delayHours only drives the inactivity window, not a send delay.
                        scheduleEnrollment(workflow, account.getUserId(), Instant.now());
                    } catch (Exception e) {
                        log.error("[WORKFLOW] Win-back enrol failed userId={} workflowId={}: {}",
                                account.getUserId(), workflow.getId(), e.getMessage());
                    }
                }
            } while (chunk.size() == SCHEDULER_BATCH_SIZE);
        }
    }

    private boolean passesSegmentFilter(backend.models.core.MarketingWorkflow workflow, UUID userId) {
        if (workflow.getTargetSegmentId() == null) return true;
        return userRepository.findById(userId)
                .map(u -> u.getSegments().stream()
                        .anyMatch(s -> s.getId().equals(workflow.getTargetSegmentId())))
                .orElse(false);
    }

    private boolean passesCooldown(backend.models.core.MarketingWorkflow workflow, UUID userId) {
        if (workflow.getCooldownDays() <= 0) return true;
        Instant cutoff = Instant.now().minus(workflow.getCooldownDays(), ChronoUnit.DAYS);
        return !enrollmentRepository.existsByWorkflowIdAndUserIdAndEnrolledAtAfter(
                workflow.getId(), userId, cutoff);
    }
}
