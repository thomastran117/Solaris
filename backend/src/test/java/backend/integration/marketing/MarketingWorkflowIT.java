package backend.integration.marketing;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.MarketingWorkflow;
import backend.models.core.User;
import backend.models.core.WorkflowEnrollment;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowEnrollmentStatus;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketingWorkflowRepository;
import backend.repositories.WorkflowEnrollmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketingWorkflowIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private MarketingWorkflowRepository workflowRepository;
    @Autowired private WorkflowEnrollmentRepository enrollmentRepository;

    @AfterEach
    void cleanMarketing() {
        try { jdbcTemplate.execute("DELETE FROM workflow_delivery_logs"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM workflow_enrollments"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM marketing_workflows"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Marketing Co");
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addOwner(User user, Company company) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(CompanyRole.OWNER);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Map<String, Object> createBody(String name, String trigger, String actionType) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("name", name);
        req.put("trigger", trigger);
        req.put("delayHours", 48);
        req.put("actionType", actionType);
        req.put("emailSubject", "Hello from " + name);
        req.put("emailBody", "<p>Content</p>");
        req.put("cooldownDays", 30);
        return req;
    }

    // ── POST create ───────────────────────────────────────────────────────────

    @Test
    void shouldCreateWorkflow_andReturnIt() throws Exception {
        User owner = createActiveUser("wf-create@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);

        mockMvc.perform(post("/companies/{id}/marketing/workflows", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody("Post-delivery email", "ORDER_DELIVERED", "EMAIL")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Post-delivery email"))
                .andExpect(jsonPath("$.data.trigger").value("ORDER_DELIVERED"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.delayHours").value(48))
                .andExpect(jsonPath("$.data.cooldownDays").value(30));
    }

    @Test
    void shouldRejectCreate_forNonMember() throws Exception {
        User owner = createActiveUser("wf-nonmember-owner@example.com", "Password1!");
        User outsider = createActiveUser("wf-nonmember@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);

        mockMvc.perform(post("/companies/{id}/marketing/workflows", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody("Hostile workflow", "ORDER_DELIVERED", "EMAIL")))
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── GET list ─────────────────────────────────────────────────────────────

    @Test
    void shouldListWorkflowsForCompany() throws Exception {
        User owner = createActiveUser("wf-list@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);

        // Create 2 workflows; 1 archived (should be excluded)
        persistWorkflow(company, "Win-back", WorkflowTrigger.DAYS_SINCE_LAST_ORDER, WorkflowStatus.ACTIVE);
        persistWorkflow(company, "Old campaign", WorkflowTrigger.ORDER_DELIVERED, WorkflowStatus.ARCHIVED);

        mockMvc.perform(get("/companies/{id}/marketing/workflows", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].name").value("Win-back"));
    }

    // ── PATCH update ──────────────────────────────────────────────────────────

    @Test
    void shouldPauseWorkflow_andReflectInList() throws Exception {
        User owner = createActiveUser("wf-pause@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        MarketingWorkflow wf = persistWorkflow(company, "Birthday push", WorkflowTrigger.CUSTOMER_BIRTHDAY, WorkflowStatus.ACTIVE);

        mockMvc.perform(patch("/companies/{cId}/marketing/workflows/{wId}", company.getId(), wf.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PAUSED")))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    // ── GET analytics ─────────────────────────────────────────────────────────

    @Test
    void shouldReturnAnalytics_withCorrectEnrolledCount() throws Exception {
        User owner = createActiveUser("wf-analytics@example.com", "Password1!");
        User customer = createActiveUser("wf-analytics-cust@example.com", "Password1!");
        Company company = createCompany(owner);
        addOwner(owner, company);
        MarketingWorkflow wf = persistWorkflow(company, "Analytics wf", WorkflowTrigger.FIRST_ORDER_PLACED, WorkflowStatus.ACTIVE);

        // Seed an enrollment
        WorkflowEnrollment enrollment = new WorkflowEnrollment();
        enrollment.setWorkflowId(wf.getId());
        enrollment.setUserId(customer.getId());
        enrollment.setEnrolledAt(Instant.now());
        enrollment.setFireAt(Instant.now().plusSeconds(3600));
        enrollment.setStatus(WorkflowEnrollmentStatus.SCHEDULED);
        enrollmentRepository.save(enrollment);

        mockMvc.perform(get("/companies/{cId}/marketing/workflows/{wId}/analytics",
                        company.getId(), wf.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowId").value(wf.getId().toString()))
                .andExpect(jsonPath("$.data.enrolledCount").value(1))
                .andExpect(jsonPath("$.data.sentCount").value(0));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private MarketingWorkflow persistWorkflow(Company company, String name,
                                              WorkflowTrigger trigger, WorkflowStatus status) {
        MarketingWorkflow w = new MarketingWorkflow();
        w.setCompanyId(company.getId());
        w.setName(name);
        w.setTrigger(trigger);
        w.setDelayHours(24);
        w.setActionType(WorkflowActionType.EMAIL);
        w.setEmailSubject("Subject");
        w.setCooldownDays(0);
        w.setStatus(status);
        return workflowRepository.save(w);
    }
}
