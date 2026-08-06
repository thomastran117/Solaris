package backend.integration.analytics;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers OperationsController (/companies/{companyId}/operations/*).
 *
 * <p>Every endpoint now has a happy path. Fulfillment, refunds, pick-delays,
 * supplier-lateness and cancellations were previously untested because their native SQL
 * (TIMESTAMPDIFF/DATE/DATEDIFF) could not run on H2 in MySQL-compat mode; the suite runs
 * on real PostgreSQL and that SQL has been rewritten to portable equivalents.
 *
 * <p>The happy-path assertions deliberately cover the empty-data case. That is enough to
 * catch the failure these tests exist to catch — the controller converts any SQL error into
 * a 500, so a 200 with a well-formed body proves the aggregate query parsed, executed, and
 * bound its result to the projection. Value-level aggregation is covered by unit tests.
 */
class OperationsIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Ops Co " + UUID.randomUUID().toString().substring(0, 8));
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addMember(Company company, User user, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private String ops(UUID companyId, String metric) {
        return "/companies/" + companyId + "/operations/" + metric;
    }

    // ── Access control (auth check fires before any DB metric query) ──────────

    @Test
    void getSummary_employee_returns403() throws Exception {
        User owner = createActiveUser("ops-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("ops-employee@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        // EMPLOYEE lacks READ_ANALYTICS capability
        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_noMembership_returns403() throws Exception {
        User owner = createActiveUser("ops-nomem-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User outsider = createActiveUser("ops-outsider@example.com", "Password1!");

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", bearer(accessTokenFor(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ops(UUID.randomUUID(), "summary")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFulfillment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ops(UUID.randomUUID(), "fulfillment")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRefunds_noMembership_returns403() throws Exception {
        User owner = createActiveUser("ops-ref-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User outsider = createActiveUser("ops-ref-out@example.com", "Password1!");

        mockMvc.perform(get(ops(company.getId(), "refunds"))
                        .header("Authorization", bearer(accessTokenFor(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPickDelays_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ops(UUID.randomUUID(), "pick-delays")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSupplierLateness_noMembership_returns403() throws Exception {
        User owner = createActiveUser("ops-sup-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User outsider = createActiveUser("ops-sup-out@example.com", "Password1!");

        mockMvc.perform(get(ops(company.getId(), "supplier-lateness"))
                        .header("Authorization", bearer(accessTokenFor(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCancellations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ops(UUID.randomUUID(), "cancellations")))
                .andExpect(status().isUnauthorized());
    }

    // ── Stockouts — JPQL only, fully testable on H2 ───────────────────────────

    @Test
    void getStockouts_owner_returns200() throws Exception {
        User user = createActiveUser("ops-sto-owner@example.com", "Password1!");
        Company company = createCompany(user);
        addMember(company, user, CompanyRole.OWNER);

        mockMvc.perform(get(ops(company.getId(), "stockouts"))
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackedProducts").value(0))
                .andExpect(jsonPath("$.data.outOfStockRate").value(0.0))
                .andExpect(jsonPath("$.data.backorderRate").value(0.0));
    }

    @Test
    void getStockouts_manager_returns200() throws Exception {
        User owner = createActiveUser("ops-sto-co-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User manager = createActiveUser("ops-sto-mgr@example.com", "Password1!");
        addMember(company, manager, CompanyRole.MANAGER);

        // MANAGER has READ_ANALYTICS
        mockMvc.perform(get(ops(company.getId(), "stockouts"))
                        .header("Authorization", bearer(accessTokenFor(manager))))
                .andExpect(status().isOk());
    }

    @Test
    void getStockouts_employee_returns403() throws Exception {
        User owner = createActiveUser("ops-sto-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("ops-sto-emp@example.com", "Password1!");
        addMember(company, employee, CompanyRole.EMPLOYEE);

        mockMvc.perform(get(ops(company.getId(), "stockouts"))
                        .header("Authorization", bearer(accessTokenFor(employee))))
                .andExpect(status().isForbidden());
    }

    // ── Native-SQL metrics ────────────────────────────────────────────────────
    // These exercise the interval/date-truncation queries in OperationsMetricsRepository.

    /** Creates a company whose owner can read analytics, and returns a bearer header value. */
    private String ownerTokenFor(Company company, User owner) {
        addMember(company, owner, CompanyRole.OWNER);
        return bearer(accessTokenFor(owner));
    }

    @Test
    void getFulfillment_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-ful-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "fulfillment"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    @Test
    void getRefunds_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-ref2-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "refunds"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    @Test
    void getPickDelays_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-pick-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "pick-delays"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    @Test
    void getSupplierLateness_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-sup2-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "supplier-lateness"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.late").value(0))
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    @Test
    void getCancellations_owner_returns200() throws Exception {
        User owner = createActiveUser("ops-can-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "cancellations"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.byReason").isArray())
                .andExpect(jsonPath("$.data.daily").isArray());
    }

    /** Summary fans out to every metric above in one request. */
    @Test
    void getSummary_owner_returns200WithAllMetrics() throws Exception {
        User owner = createActiveUser("ops-sum-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.fulfillment").exists())
                .andExpect(jsonPath("$.data.refunds").exists())
                .andExpect(jsonPath("$.data.pickDelays").exists())
                .andExpect(jsonPath("$.data.openDisputeCount").exists());
    }

    /**
     * Feature 15, AC 6. A company with no chargebacks reports zero — which also proves the
     * native dispute-count join parses and executes against real PostgreSQL.
     */
    @Test
    void getSummary_includesZeroOpenDisputeCountWhenCompanyHasNoChargebacks() throws Exception {
        User owner = createActiveUser("ops-disputes-owner@example.com", "Password1!");
        Company company = createCompany(owner);

        mockMvc.perform(get(ops(company.getId(), "summary"))
                        .header("Authorization", ownerTokenFor(company, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openDisputeCount").value(0));
    }
}
