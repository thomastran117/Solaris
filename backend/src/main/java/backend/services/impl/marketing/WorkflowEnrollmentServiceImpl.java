package backend.services.impl.marketing;

import backend.events.notification.NotificationEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkflowEnrollmentServiceImpl implements WorkflowSchedulerPort {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEnrollmentServiceImpl.class);
    private static final int SCHEDULER_BATCH_SIZE = 500;
    private static final int MAX_RETRY_COUNT = 3;

    private final MarketingWorkflowRepository workflowRepository;
    private final WorkflowEnrollmentRepository enrollmentRepository;
    private final WorkflowDeliveryLogRepository deliveryLogRepository;
    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final EmailService emailService;
    private final NotificationEventPublisher notificationEventPublisher;

    // Lazy self-reference typed to the package-private WorkflowSchedulerPort so Spring AOP
    // proxy interception works under both CGLIB and JDK dynamic proxy modes.
    private WorkflowSchedulerPort self;

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
    public void setSelf(@Lazy WorkflowSchedulerPort self) {
        this.self = self;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enrol(WorkflowTrigger trigger, UUID companyId, UUID userId) {
        List<MarketingWorkflow> workflows =
                workflowRepository.findByTriggerAndStatusAndCompanyId(trigger, WorkflowStatus.ACTIVE, companyId);
        for (MarketingWorkflow workflow : workflows) {
            scheduleEnrollment(workflow, userId);
        }
    }

    private void scheduleEnrollment(MarketingWorkflow workflow, UUID userId) {
        scheduleEnrollment(workflow, userId, Instant.now().plus(workflow.getDelayHours(), ChronoUnit.HOURS));
    }

    private void scheduleEnrollment(MarketingWorkflow workflow, UUID userId, Instant fireAt) {
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

    // REQUIRES_NEW so that the cooldown check and enrollment insert are atomic,
    // preventing the TOCTOU race in dailyWinBackEnrol on multi-node deployments.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleEnrollmentInNewTx(MarketingWorkflow workflow, UUID userId, Instant fireAt) {
        scheduleEnrollment(workflow, userId, fireAt);
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
                try {
                    self.incrementRetryOrMarkFailed(id);
                } catch (Exception e2) {
                    log.error("[WORKFLOW] Failed to update retry count for enrollment id={}", id, e2);
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOneEnrollment(UUID enrollmentId) {
        WorkflowEnrollment enrollment = enrollmentRepository.findById(enrollmentId).orElse(null);
        if (enrollment == null || enrollment.getStatus() != WorkflowEnrollmentStatus.SCHEDULED) return;

        MarketingWorkflow workflow = workflowRepository.findById(enrollment.getWorkflowId()).orElse(null);

        if (workflow == null || workflow.getStatus() == WorkflowStatus.ARCHIVED) {
            enrollment.setStatus(WorkflowEnrollmentStatus.CANCELLED);
            enrollmentRepository.save(enrollment);
            return;
        }
        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            // PAUSED — defer so the scheduler won't re-fetch every 60s until the workflow resumes.
            enrollment.setStatus(WorkflowEnrollmentStatus.DEFERRED);
            enrollmentRepository.save(enrollment);
            return;
        }

        User user = userRepository.findById(enrollment.getUserId()).orElse(null);
        if (user == null) {
            enrollment.setStatus(WorkflowEnrollmentStatus.CANCELLED);
            enrollmentRepository.save(enrollment);
            return;
        }

        // Persist DB state before dispatching. If the save succeeds and dispatch later fails,
        // the enrollment is already SENT and the log is written — dispatch errors are logged
        // but not retried (Kafka publishes are fire-and-forget per the project's outbox design).
        enrollment.setStatus(WorkflowEnrollmentStatus.SENT);
        enrollmentRepository.save(enrollment);
        deliveryLogRepository.save(new WorkflowDeliveryLog(
                enrollment.getId(), workflow.getActionType(), WorkflowDeliveryStatus.SENT, Instant.now()));

        if (workflow.getActionType() == WorkflowActionType.EMAIL) {
            // EmailServiceImpl.publish() registers its own afterCommit hook internally;
            // call it directly here (inside the REQUIRES_NEW tx) so it defers the Kafka send
            // to after this transaction commits. A nested afterCommit wrapper would be silently
            // dropped because Spring captures the synchronization list before iteration.
            emailService.sendMarketingWorkflowEmail(
                    user.getEmail(), user.getFirstName(),
                    workflow.getId(), workflow.getCompanyId(),
                    workflow.getEmailSubject(),
                    workflow.getEmailBody() != null ? workflow.getEmailBody() : "");
        } else if (workflow.getActionType() == WorkflowActionType.PUSH) {
            notificationEventPublisher.publish(new NotificationEvent.MarketingWorkflowPush(
                    user.getId(),
                    workflow.getId(),
                    workflow.getEmailSubject() != null ? workflow.getEmailSubject() : "Message",
                    workflow.getEmailBody() != null ? workflow.getEmailBody() : ""));
        }
    }

    // Called when processOneEnrollment throws — runs in its own REQUIRES_NEW so the retry
    // count update commits even though the processing transaction rolled back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementRetryOrMarkFailed(UUID enrollmentId) {
        WorkflowEnrollment enrollment = enrollmentRepository.findById(enrollmentId).orElse(null);
        if (enrollment == null || enrollment.getStatus() != WorkflowEnrollmentStatus.SCHEDULED) return;
        enrollment.setRetryCount(enrollment.getRetryCount() + 1);
        if (enrollment.getRetryCount() >= MAX_RETRY_COUNT) {
            enrollment.setStatus(WorkflowEnrollmentStatus.FAILED);
            log.warn("[WORKFLOW] Enrollment id={} permanently failed after {} attempts", enrollmentId, enrollment.getRetryCount());
        }
        enrollmentRepository.save(enrollment);
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void dailyBirthdayEnrol() {
        LocalDate today = LocalDate.now();
        List<MarketingWorkflow> workflows =
                workflowRepository.findByTriggerAndStatus(WorkflowTrigger.CUSTOMER_BIRTHDAY, WorkflowStatus.ACTIVE);
        if (workflows.isEmpty()) return;

        Set<UUID> companyIds = workflows.stream()
                .map(MarketingWorkflow::getCompanyId)
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
        List<MarketingWorkflow> workflows =
                workflowRepository.findByTriggerAndStatus(WorkflowTrigger.DAYS_SINCE_LAST_ORDER, WorkflowStatus.ACTIVE);
        if (workflows.isEmpty()) return;

        for (MarketingWorkflow workflow : workflows) {
            int inactiveDays = workflow.getDelayHours() / 24;
            if (inactiveDays <= 0) continue;

            LocalDate cutoffDate = LocalDate.now().minusDays(inactiveDays);
            String cutoffYearMonth = String.format("%04d-%02d", cutoffDate.getYear(), cutoffDate.getMonthValue());

            int page = 0;
            List<LoyaltyAccount> chunk;
            do {
                // Use < so only months entirely before the cutoff are targeted, avoiding
                // false positives for customers who ordered late in the cutoff month.
                chunk = loyaltyAccountRepository.findByCompanyIdAndLastOrderYearMonthLessThan(
                        workflow.getCompanyId(), cutoffYearMonth,
                        PageRequest.of(page++, SCHEDULER_BATCH_SIZE)).getContent();
                for (LoyaltyAccount account : chunk) {
                    try {
                        // Use self.scheduleEnrollmentInNewTx so the cooldown check and insert
                        // run atomically in a REQUIRES_NEW transaction (TOCTOU protection).
                        // fireAt = now: for win-back the delay drives the inactivity window,
                        // not a send delay.
                        self.scheduleEnrollmentInNewTx(workflow, account.getUserId(), Instant.now());
                    } catch (Exception e) {
                        log.error("[WORKFLOW] Win-back enrol failed userId={} workflowId={}: {}",
                                account.getUserId(), workflow.getId(), e.getMessage());
                    }
                }
            } while (chunk.size() == SCHEDULER_BATCH_SIZE);
        }
    }

    private boolean passesSegmentFilter(MarketingWorkflow workflow, UUID userId) {
        if (workflow.getTargetSegmentId() == null) return true;
        return userRepository.findById(userId)
                .map(u -> u.getSegments().stream()
                        .anyMatch(s -> s.getId().equals(workflow.getTargetSegmentId())))
                .orElse(false);
    }

    private boolean passesCooldown(MarketingWorkflow workflow, UUID userId) {
        if (workflow.getCooldownDays() <= 0) return true;
        Instant cutoff = Instant.now().minus(workflow.getCooldownDays(), ChronoUnit.DAYS);
        return !enrollmentRepository.existsByWorkflowIdAndUserIdAndEnrolledAtAfter(
                workflow.getId(), userId, cutoff);
    }
}
