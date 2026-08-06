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
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner, String name) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName(name);
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addMember(User user, Company company, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Map<String, Object> createCompanyBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("industry", "Technology");
        return body;
    }

    // ── GET /companies ────────────────────────────────────────────────────────

    @Test
    void listCompanies_returnsEmptyPageWhenNone() throws Exception {
        mockMvc.perform(get("/companies")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listCompanies_returnsActiveCompanies() throws Exception {
        User owner = createActiveUser("co-list@example.com", "Password1!");
        createCompany(owner, "Listed Company");

        mockMvc.perform(get("/companies")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── GET /companies/mine ───────────────────────────────────────────────────

    @Test
    void getMyCompany_returns200ForOwner() throws Exception {
        User owner = createActiveUser("co-mine@example.com", "Password1!");
        Company company = createCompany(owner, "My Company");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/mine")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("My Company"));
    }

    @Test
    void getMyCompany_returns404WhenUserHasNoCompany() throws Exception {
        User user = createActiveUser("co-mine-none@example.com", "Password1!");

        mockMvc.perform(get("/companies/mine")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyCompany_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/companies/mine")
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{id}/public ────────────────────────────────────────────

    @Test
    void getPublicCompany_returns200ForActiveCompany() throws Exception {
        User owner = createActiveUser("co-pub@example.com", "Password1!");
        Company company = createCompany(owner, "Public Company");

        mockMvc.perform(get("/companies/{id}/public", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Public Company"));
    }

    @Test
    void getPublicCompany_returns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/companies/{id}/public", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicCompany_returns404ForInactiveCompany() throws Exception {
        User owner = createActiveUser("co-pub-inactive@example.com", "Password1!");
        Company company = createCompany(owner, "Inactive Company");
        company.setStatus(CompanyStatus.INACTIVE);
        companyRepository.save(company);

        mockMvc.perform(get("/companies/{id}/public", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── GET /companies/{id} ───────────────────────────────────────────────────

    @Test
    void getCompany_returns200ForMember() throws Exception {
        User owner = createActiveUser("co-get-member@example.com", "Password1!");
        Company company = createCompany(owner, "Member Company");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{id}", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(company.getId().toString()));
    }

    @Test
    void getCompany_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("co-get-owner@example.com", "Password1!");
        User stranger = createActiveUser("co-get-stranger@example.com", "Password1!");
        Company company = createCompany(owner, "Strangers Company");

        mockMvc.perform(get("/companies/{id}", company.getId())
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCompany_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("co-get-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Company");

        mockMvc.perform(get("/companies/{id}", company.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies ───────────────────────────────────────────────────────

    @Test
    void createCompany_returns201WithName() throws Exception {
        User user = createActiveUser("co-create@example.com", "Password1!");

        MvcResult result = mockMvc.perform(post("/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createCompanyBody("Brand New Co")))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Brand New Co"))
                .andReturn();

        UUID companyId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
        assertEquals("Brand New Co", companyRepository.findById(companyId).orElseThrow().getName());
    }

    @Test
    void createCompany_returns400WhenNameMissing() throws Exception {
        User user = createActiveUser("co-create-noname@example.com", "Password1!");

        mockMvc.perform(post("/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"Tech\"}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCompany_returns409WhenNameAlreadyExists() throws Exception {
        User user = createActiveUser("co-create-dup@example.com", "Password1!");

        String body = objectMapper.writeValueAsString(createCompanyBody("Duplicate Co"));

        mockMvc.perform(post("/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void createCompany_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createCompanyBody("Unauth Co"))))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /companies/{id} ─────────────────────────────────────────────────

    @Test
    void updateCompany_returns200WithUpdatedName() throws Exception {
        User owner = createActiveUser("co-update@example.com", "Password1!");
        Company company = createCompany(owner, "Old Name");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(patch("/companies/{id}", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"));

        assertEquals("New Name",
                companyRepository.findById(company.getId()).orElseThrow().getName());
    }

    @Test
    void updateCompany_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("co-upd-owner@example.com", "Password1!");
        User employee = createActiveUser("co-upd-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Employee Update");
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(patch("/companies/{id}", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}")
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCompany_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("co-upd-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Update");

        mockMvc.perform(patch("/companies/{id}", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateCompany_returns400ForInvalidFoundedYear() throws Exception {
        User owner = createActiveUser("co-upd-year@example.com", "Password1!");
        Company company = createCompany(owner, "Year Company");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(patch("/companies/{id}", company.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foundedYear\":1500}")
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /companies/{id} ────────────────────────────────────────────────

    @Test
    void deleteCompany_returns204ForOwner() throws Exception {
        User owner = createActiveUser("co-del@example.com", "Password1!");
        Company company = createCompany(owner, "Delete Me");
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{id}", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        // The company must no longer be an active, retrievable record — whether the delete
        // is a hard delete or a soft delete (status change).
        Optional<Company> after = companyRepository.findById(company.getId());
        assertTrue(after.isEmpty() || after.get().getStatus() != CompanyStatus.ACTIVE,
                "Deleted company should be removed or no longer ACTIVE in the database");
    }

    @Test
    void deleteCompany_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("co-del-owner@example.com", "Password1!");
        User employee = createActiveUser("co-del-emp@example.com", "Password1!");
        Company company = createCompany(owner, "Protected Company");
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(delete("/companies/{id}", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCompany_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("co-del-unauth@example.com", "Password1!");
        Company company = createCompany(owner, "Unauth Delete");

        mockMvc.perform(delete("/companies/{id}", company.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── Optimistic locking (@Version) ───────────────────────────────────────────

    @Test
    void updatingCompany_incrementsOptimisticLockVersion() {
        User owner = createActiveUser("co-version@example.com", "Password1!");
        Company company = createCompany(owner, "Versioned Co");
        Long initialVersion = company.getVersion();
        org.junit.jupiter.api.Assertions.assertNotNull(initialVersion,
                "@Version column should be populated on first save");

        company.setIndustry("Logistics");
        Company saved = companyRepository.saveAndFlush(company);

        org.junit.jupiter.api.Assertions.assertEquals(initialVersion + 1, saved.getVersion(),
                "saving a modified Company should bump the optimistic-lock version");
    }
}
