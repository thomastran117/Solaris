package backend.services.impl.marketing;

import backend.events.email.EmailEvent;
import backend.events.notification.NotificationEvent;
import backend.kafka.producers.NotificationEventPublisher;
import backend.models.core.LoyaltyAccount;
import backend.models.core.User;
import backend.models.core.UserPreference;
import backend.models.core.WorkflowDeliveryLog;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class WorkflowEnrollmentServiceImpl implements WorkflowEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEnrollmentServiceImpl.class);

    private final MarketingWorkflowRepository workflowRepository;
    private final WorkflowEnrollmentRepository enrollmentRepository;
    private final WorkflowDeliveryLogRepository deliveryLogRepository;
    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final EmailService emailService;
    private final NotificationEventPublisher notificationEventPublisher;

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
        if (!passesSegmentFilter(workflow, userId)) return;
        if (!passesCooldown(workflow, userId)) return;
        Instant now = Instant.now();
        WorkflowEnrollment enrollment = new WorkflowEnrollment();
        enrollment.setWorkflowId(workflow.getId());
        enrollment.setUserId(userId);
        enrollment.setEnrolledAt(now);
        enrollment.setFireAt(now.plus(workflow.getDelayHours(), ChronoUnit.HOURS));
        enrollment.setStatus(WorkflowEnrollmentStatus.SCHEDULED);
        enrollmentRepository.save(enrollment);
        log.debug("[WORKFLOW] Enrolled userId={} in workflowId={} fireAt={}", userId, workflow.getId(), enrollment.getFireAt());
    }

    @Override
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processScheduledEnrollments() {
        List<WorkflowEnrollment> due =
                enrollmentRepository.findByStatusAndFireAtBefore(WorkflowEnrollmentStatus.SCHEDULED, Instant.now());

        for (WorkflowEnrollment enrollment : due) {
            try {
                backend.models.core.MarketingWorkflow workflow =
                        workflowRepository.findById(enrollment.getWorkflowId()).orElse(null);
                if (workflow == null || workflow.getStatus() != WorkflowStatus.ACTIVE) {
                    enrollment.setStatus(WorkflowEnrollmentStatus.CANCELLED);
                    enrollmentRepository.save(enrollment);
                    continue;
                }

                User user = userRepository.findById(enrollment.getUserId()).orElse(null);
                if (user == null) {
                    enrollment.setStatus(WorkflowEnrollmentStatus.CANCELLED);
                    enrollmentRepository.save(enrollment);
                    continue;
                }

                if (workflow.getActionType() == WorkflowActionType.EMAIL) {
                    emailService.sendMarketingWorkflowEmail(
                            user.getEmail(),
                            user.getFirstName(),
                            workflow.getId(),
                            workflow.getCompanyId(),
                            workflow.getEmailSubject(),
                            workflow.getEmailBody() != null ? workflow.getEmailBody() : "");
                } else if (workflow.getActionType() == WorkflowActionType.PUSH) {
                    notificationEventPublisher.publish(new NotificationEvent.MarketingWorkflowPush(
                            user.getId(),
                            workflow.getId(),
                            workflow.getEmailSubject() != null ? workflow.getEmailSubject() : "Message",
                            workflow.getEmailBody() != null ? workflow.getEmailBody() : ""));
                }

                enrollment.setStatus(WorkflowEnrollmentStatus.SENT);
                enrollmentRepository.save(enrollment);
                deliveryLogRepository.save(new WorkflowDeliveryLog(
                        enrollment.getId(), workflow.getActionType(), "SENT", Instant.now()));

            } catch (Exception e) {
                log.error("[WORKFLOW] Failed to process enrollment id={}: {}", enrollment.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void dailyBirthdayEnrol() {
        LocalDate today = LocalDate.now();
        List<backend.models.core.MarketingWorkflow> workflows =
                workflowRepository.findByTriggerAndStatus(WorkflowTrigger.CUSTOMER_BIRTHDAY, WorkflowStatus.ACTIVE);
        if (workflows.isEmpty()) return;

        List<UserPreference> todayBirthdays =
                userPreferenceRepository.findByBirthDateMonthAndDay(today.getMonthValue(), today.getDayOfMonth());

        // Deduplicate by company: enrol() already fans out to all active workflows for a company,
        // so calling it per-workflow would create N×N enrollments for N workflows per company.
        Set<UUID> companyIds = workflows.stream()
                .map(backend.models.core.MarketingWorkflow::getCompanyId)
                .collect(Collectors.toSet());

        for (UserPreference pref : todayBirthdays) {
            for (UUID companyId : companyIds) {
                try {
                    enrol(WorkflowTrigger.CUSTOMER_BIRTHDAY, companyId, pref.getUserId());
                } catch (Exception e) {
                    log.error("[WORKFLOW] Birthday enrol failed userId={} companyId={}: {}",
                            pref.getUserId(), companyId, e.getMessage());
                }
            }
        }
    }

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void dailyWinBackEnrol() {
        List<backend.models.core.MarketingWorkflow> workflows =
                workflowRepository.findByTriggerAndStatus(WorkflowTrigger.DAYS_SINCE_LAST_ORDER, WorkflowStatus.ACTIVE);
        if (workflows.isEmpty()) return;

        for (backend.models.core.MarketingWorkflow workflow : workflows) {
            int inactiveDays = workflow.getDelayHours() / 24;
            if (inactiveDays <= 0) continue;

            // Compute target year-month: today minus inactiveDays, formatted as "yyyy-MM"
            LocalDate cutoffDate = LocalDate.now().minusDays(inactiveDays);
            String cutoffYearMonth = String.format("%04d-%02d", cutoffDate.getYear(), cutoffDate.getMonthValue());

            List<LoyaltyAccount> accounts =
                    loyaltyAccountRepository.findByCompanyIdAndLastOrderYearMonth(
                            workflow.getCompanyId(), cutoffYearMonth);

            // Enrol in this specific workflow only — calling enrol() would fan out to every
            // active DAYS_SINCE_LAST_ORDER workflow regardless of its inactivity threshold.
            for (LoyaltyAccount account : accounts) {
                try {
                    scheduleEnrollment(workflow, account.getUserId());
                } catch (Exception e) {
                    log.error("[WORKFLOW] Win-back enrol failed userId={} workflowId={}: {}",
                            account.getUserId(), workflow.getId(), e.getMessage());
                }
            }
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
