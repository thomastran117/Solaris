package backend.integration.company;

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
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;

    @AfterEach
    void cleanTeam() {
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner, String name) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName(name);
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private CompanyMembership addMember(User user, Company company, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        return membershipRepository.save(m);
    }

    private Map<String, Object> inviteBody(String email, CompanyRole role) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("role", role.name());
        return body;
    }

    // ── GET /companies/{companyId}/team ───────────────────────────────────────

    @Test
    void listTeam_returns200WithMembersForOwner() throws Exception {
        User owner = createActiveUser("team-list-owner@example.com", "Password1!");
        Company company = createCompany(owner, "Team List Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/team", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void listTeam_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("team-list-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("team-list-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Employee List Co");
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(get("/companies/{companyId}/team", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTeam_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("team-list-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth List Co");

        mockMvc.perform(get("/companies/{companyId}/team", company.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/team/invites ──────────────────────────────

    @Test
    void inviteMember_returns201WithPendingMembership() throws Exception {
        User owner = createActiveUser("team-invite-owner@example.com", "Password1!");
        Company company = createCompany(owner, "Invite Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/team/invites", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                inviteBody("new-member@example.com", CompanyRole.EMPLOYEE)))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company_memberships WHERE status = 'PENDING'", Integer.class);
        assertEquals(1, pending, "A pending membership invite should be persisted");
    }

    @Test
    void inviteMember_returns409WhenAlreadyPending() throws Exception {
        User owner = createActiveUser("team-invite-dup-owner@example.com", "Password1!");
        Company company = createCompany(owner, "Dup Invite Co");
        addMember(owner, company, CompanyRole.OWNER);

        String body = objectMapper.writeValueAsString(
                inviteBody("dup-invite@example.com", CompanyRole.EMPLOYEE));

        mockMvc.perform(post("/companies/{companyId}/team/invites", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/companies/{companyId}/team/invites", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void inviteMember_returns400WhenEmailMissing() throws Exception {
        User owner = createActiveUser("team-invite-noemail@example.com", "Password1!");
        Company company = createCompany(owner, "No Email Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/team/invites", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"EMPLOYEE\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inviteMember_returns400WhenRoleMissing() throws Exception {
        User owner = createActiveUser("team-invite-norole@example.com", "Password1!");
        Company company = createCompany(owner, "No Role Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/team/invites", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@example.com\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inviteMember_returns400WhenRoleIsOwner() throws Exception {
        User owner = createActiveUser("team-invite-asowner@example.com", "Password1!");
        Company company = createCompany(owner, "Owner Role Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/team/invites", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                inviteBody("future-owner@example.com", CompanyRole.OWNER)))
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inviteMember_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("team-invite-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("team-invite-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Emp Invite Co");
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{companyId}/team/invites", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                inviteBody("new-person@example.com", CompanyRole.EMPLOYEE)))
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviteMember_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("team-invite-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Invite Co");

        mockMvc.perform(post("/companies/{companyId}/team/invites", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                inviteBody("person@example.com", CompanyRole.EMPLOYEE))))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /companies/{companyId}/team/{membershipId} ──────────────────────

    @Test
    void updateMemberRole_returns200WithNewRole() throws Exception {
        User owner = createActiveUser("team-upd-owner@example.com", "Password1!");
        User employee = createActiveUser("team-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Role Update Co");
        addMember(owner, company, CompanyRole.OWNER);
        CompanyMembership empMembership = addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(patch("/companies/{companyId}/team/{membershipId}",
                        company.getId(), empMembership.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MANAGER"));

        assertEquals(CompanyRole.MANAGER,
                membershipRepository.findById(empMembership.getId()).orElseThrow().getRole());
    }

    @Test
    void updateMemberRole_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("team-upd-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("team-upd-emp2@example.com", "Password1!");
        User other = createActiveUser("team-upd-other@example.com", "Password1!");
        Company company = createCompany(owner, "Emp Role Co");
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        CompanyMembership otherMembership = addMember(other, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(patch("/companies/{companyId}/team/{membershipId}",
                        company.getId(), otherMembership.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}")
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMemberRole_returns404ForUnknownMembership() throws Exception {
        User owner = createActiveUser("team-upd-404@example.com", "Password1!");
        Company company = createCompany(owner, "404 Role Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(patch("/companies/{companyId}/team/{membershipId}",
                        company.getId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /companies/{companyId}/team/{membershipId} ─────────────────────

    @Test
    void revokeMember_returns204ForOwner() throws Exception {
        User owner = createActiveUser("team-revoke-owner@example.com", "Password1!");
        User employee = createActiveUser("team-revoke-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Revoke Co");
        addMember(owner, company, CompanyRole.OWNER);
        CompanyMembership empMembership = addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(delete("/companies/{companyId}/team/{membershipId}",
                        company.getId(), empMembership.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        // The membership must no longer be an active team member — whether hard-deleted or
        // soft-revoked via a status change.
        Optional<CompanyMembership> after = membershipRepository.findById(empMembership.getId());
        assertTrue(after.isEmpty() || after.get().getStatus() != CompanyMembershipStatus.ACTIVE,
                "Revoked member should be removed or no longer ACTIVE in the database");
    }

    @Test
    void revokeMember_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("team-revoke-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("team-revoke-emp2@example.com", "Password1!");
        User other = createActiveUser("team-revoke-other@example.com", "Password1!");
        Company company = createCompany(owner, "Emp Revoke Co");
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        CompanyMembership otherMembership = addMember(other, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(delete("/companies/{companyId}/team/{membershipId}",
                        company.getId(), otherMembership.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeMember_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("team-revoke-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Revoke Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{companyId}/team/{membershipId}",
                        company.getId(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/me ─────────────────────────────────────────

    @Test
    void describeMyAccess_returns200WithRoleForMember() throws Exception {
        User owner = createActiveUser("team-me-owner@example.com", "Password1!");
        Company company = createCompany(owner, "My Access Co");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/me", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    @Test
    void describeMyAccess_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("team-me-owner2@example.com", "Password1!");
        User stranger = createActiveUser("team-me-stranger@example.com", "Password1!");
        Company company = createCompany(owner, "Stranger Access Co");

        mockMvc.perform(get("/companies/{companyId}/me", company.getId())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void describeMyAccess_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("team-me-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Me Co");

        mockMvc.perform(get("/companies/{companyId}/me", company.getId()))
                .andExpect(status().isUnauthorized());
    }
}
