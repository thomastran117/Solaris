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
 * NOTE: fulfillment, refunds, pick-delays, supplier-lateness, and cancellations
 * all trigger native SQL with TIMESTAMPDIFF/DATE/DATEDIFF which H2 MySQL-compat
 * mode cannot execute. Happy-path tests for those five endpoints are omitted.
 * They work correctly against production MySQL.
 *
 * getStockouts uses JPQL exclusively and is fully tested.
 * Access-control tests (403/401) are safe for all endpoints because the auth
 * check fires before any DB query.
 */
class OperationsIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

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
}
