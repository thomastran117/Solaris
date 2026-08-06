package backend.integration.marketing;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CustomerSegment;
import backend.models.core.MarketingWorkflow;
import backend.models.core.User;
import backend.models.core.WorkflowEnrollment;
import backend.models.enums.CompanyStatus;
import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowEnrollmentStatus;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;
import backend.repositories.CompanyRepository;
import backend.repositories.CustomerSegmentRepository;
import backend.repositories.MarketingWorkflowRepository;
import backend.repositories.WorkflowDeliveryLogRepository;
import backend.repositories.WorkflowEnrollmentRepository;
import backend.services.intf.marketing.WorkflowEnrollmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEnrollmentIT extends AbstractIntegrationIT {

    @Autowired private WorkflowEnrollmentService enrollmentService;
    @Autowired private MarketingWorkflowRepository workflowRepository;
    @Autowired private WorkflowEnrollmentRepository enrollmentRepository;
    @Autowired private WorkflowDeliveryLogRepository deliveryLogRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CustomerSegmentRepository segmentRepository;


    // ── helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Enrol Co");
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private MarketingWorkflow activeWorkflow(Company company, WorkflowTrigger trigger,
                                             int cooldownDays, CustomerSegment segment) {
        MarketingWorkflow w = new MarketingWorkflow();
        w.setCompanyId(company.getId());
        w.setName("Test workflow");
        w.setTrigger(trigger);
        w.setDelayHours(0);
        w.setActionType(WorkflowActionType.EMAIL);
        w.setEmailSubject("Hi");
        w.setEmailBody("<p>body</p>");
        w.setCooldownDays(cooldownDays);
        w.setStatus(WorkflowStatus.ACTIVE);
        if (segment != null) {
            w.setTargetSegmentId(segment.getId());
        }
        return workflowRepository.save(w);
    }

    // ── enrol() ───────────────────────────────────────────────────────────────

    @Test
    void shouldEnrolUser_onOrderDeliveredTrigger() {
        User user = createActiveUser("enrol-delivered@example.com", "Password1!");
        Company company = createCompany(user);
        activeWorkflow(company, WorkflowTrigger.ORDER_DELIVERED, 0, null);

        enrollmentService.enrol(WorkflowTrigger.ORDER_DELIVERED, company.getId(), user.getId());

        var enrollments = enrollmentRepository.findAll();
        assertThat(enrollments).hasSize(1);
        assertThat(enrollments.get(0).getUserId()).isEqualTo(user.getId());
        assertThat(enrollments.get(0).getStatus()).isEqualTo(WorkflowEnrollmentStatus.SCHEDULED);
    }

    @Test
    void shouldNotEnrolUser_withinCooldownPeriod() {
        User user = createActiveUser("enrol-cooldown@example.com", "Password1!");
        Company company = createCompany(user);
        MarketingWorkflow wf = activeWorkflow(company, WorkflowTrigger.ORDER_DELIVERED, 7, null);

        // Seed an existing recent enrollment
        WorkflowEnrollment existing = new WorkflowEnrollment();
        existing.setWorkflowId(wf.getId());
        existing.setUserId(user.getId());
        existing.setEnrolledAt(Instant.now().minusSeconds(3600));
        existing.setFireAt(Instant.now());
        existing.setStatus(WorkflowEnrollmentStatus.SENT);
        enrollmentRepository.save(existing);

        enrollmentService.enrol(WorkflowTrigger.ORDER_DELIVERED, company.getId(), user.getId());

        assertThat(enrollmentRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldNotEnrolUser_notInTargetSegment() {
        User user = createActiveUser("enrol-segment-miss@example.com", "Password1!");
        Company company = createCompany(user);

        CustomerSegment segment = new CustomerSegment();
        segment.setCode("VIP");
        segment.setName("VIP Customers");
        segment = segmentRepository.save(segment);

        activeWorkflow(company, WorkflowTrigger.ORDER_DELIVERED, 0, segment);

        enrollmentService.enrol(WorkflowTrigger.ORDER_DELIVERED, company.getId(), user.getId());

        assertThat(enrollmentRepository.findAll()).isEmpty();
    }

    @Test
    void shouldEnrolUser_inTargetSegment() {
        User user = createActiveUser("enrol-segment-hit@example.com", "Password1!");
        Company company = createCompany(user);

        CustomerSegment segment = new CustomerSegment();
        segment.setCode("REGULARS");
        segment.setName("Regulars");
        segment = segmentRepository.save(segment);

        // Add user to segment
        user.getSegments().add(segment);
        userRepository.save(user);

        activeWorkflow(company, WorkflowTrigger.ORDER_DELIVERED, 0, segment);

        enrollmentService.enrol(WorkflowTrigger.ORDER_DELIVERED, company.getId(), user.getId());

        assertThat(enrollmentRepository.findAll()).hasSize(1);
    }

    // ── processScheduledEnrollments() ─────────────────────────────────────────

    @Test
    void shouldMarkEnrollmentSent_afterFireAt() {
        User user = createActiveUser("enrol-fire@example.com", "Password1!");
        Company company = createCompany(user);
        user.setFirstName("Alice");
        userRepository.save(user);

        MarketingWorkflow wf = activeWorkflow(company, WorkflowTrigger.ORDER_DELIVERED, 0, null);

        WorkflowEnrollment enrollment = new WorkflowEnrollment();
        enrollment.setWorkflowId(wf.getId());
        enrollment.setUserId(user.getId());
        enrollment.setEnrolledAt(Instant.now().minusSeconds(200));
        enrollment.setFireAt(Instant.now().minusSeconds(10));
        enrollment.setStatus(WorkflowEnrollmentStatus.SCHEDULED);
        enrollmentRepository.save(enrollment);

        enrollmentService.processScheduledEnrollments();

        WorkflowEnrollment updated = enrollmentRepository.findAll().get(0);
        assertThat(updated.getStatus()).isEqualTo(WorkflowEnrollmentStatus.SENT);
        assertThat(deliveryLogRepository.findAll()).hasSize(1);
    }
}
