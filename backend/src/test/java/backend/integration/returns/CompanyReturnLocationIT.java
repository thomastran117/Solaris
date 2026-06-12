package backend.integration.returns;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.CompanyReturnLocation;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.CompanyReturnLocationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyReturnLocationIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private CompanyReturnLocationRepository locationRepository;

    @AfterEach
    void cleanLocations() {
        try { jdbcTemplate.execute("DELETE FROM company_return_locations"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Location Company " + UUID.randomUUID());
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

    private String locationBody(String address, String city, String country) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("address", address);
        body.put("city", city);
        body.put("country", country);
        body.put("primary", false);
        return objectMapper.writeValueAsString(body);
    }

    private UUID createLocationViaApi(User owner, UUID companyId, String address) throws Exception {
        String response = mockMvc.perform(post("/companies/{companyId}/return-locations", companyId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody(address, "Austin", "US")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    private CompanyReturnLocation createLocationDirect(Company company, String address, boolean primary) {
        CompanyReturnLocation loc = new CompanyReturnLocation();
        loc.setCompany(company);
        loc.setAddress(address);
        loc.setCity("Austin");
        loc.setCountry("US");
        loc.setPrimary(primary);
        return locationRepository.save(loc);
    }

    // ── GET /companies/{companyId}/return-locations ───────────────────────────

    @Test
    void listReturnLocations_returnsEmptyListWhenNoneExist() throws Exception {
        User owner = createActiveUser("loc-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{companyId}/return-locations", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listReturnLocations_returnsExistingLocations() throws Exception {
        User owner = createActiveUser("loc-list-items@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createLocationDirect(company, "123 Main St", false);
        createLocationDirect(company, "456 Oak Ave", false);

        mockMvc.perform(get("/companies/{companyId}/return-locations", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void listReturnLocations_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("loc-list-403-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        User outsider = createActiveUser("loc-list-403-out@example.com", "Password1!");

        mockMvc.perform(get("/companies/{companyId}/return-locations", company.getId())
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void listReturnLocations_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/return-locations", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /companies/{companyId}/return-locations ──────────────────────────

    @Test
    void createReturnLocation_returns201WithLocationData() throws Exception {
        User owner = createActiveUser("loc-create@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{companyId}/return-locations", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("789 Pine Rd", "Seattle", "US")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.address").value("789 Pine Rd"))
                .andExpect(jsonPath("$.data.city").value("Seattle"))
                .andExpect(jsonPath("$.data.country").value("US"));
    }

    @Test
    void createReturnLocation_managerCanCreate() throws Exception {
        User owner = createActiveUser("loc-create-mgr-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User manager = createActiveUser("loc-create-mgr@example.com", "Password1!");
        addMember(manager, company, CompanyRole.MANAGER);

        mockMvc.perform(post("/companies/{companyId}/return-locations", company.getId())
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("1 Manager Way", "Portland", "US")))
                .andExpect(status().isCreated());
    }

    @Test
    void createReturnLocation_returns400WhenAddressMissing() throws Exception {
        User owner = createActiveUser("loc-create-400@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("city", "Houston");
        body.put("country", "US");
        body.put("primary", false);

        mockMvc.perform(post("/companies/{companyId}/return-locations", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturnLocation_employeeCanCreate() throws Exception {
        User owner = createActiveUser("loc-create-emp-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        User employee = createActiveUser("loc-create-emp@example.com", "Password1!");
        addMember(employee, company, CompanyRole.EMPLOYEE);

        // EMPLOYEE has FULFILL_ORDERS capability — allowed to create return locations
        mockMvc.perform(post("/companies/{companyId}/return-locations", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("5 Employee St", "Miami", "US")))
                .andExpect(status().isCreated());
    }

    @Test
    void createReturnLocation_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/companies/{companyId}/return-locations", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("1 Anon St", "Denver", "US")))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /companies/{companyId}/return-locations/{locationId} ────────────

    @Test
    void updateReturnLocation_ownerCanUpdateAddress() throws Exception {
        User owner = createActiveUser("loc-update@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID locationId = createLocationViaApi(owner, company.getId(), "Old Address");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("address", "New Address 999");

        mockMvc.perform(patch("/companies/{companyId}/return-locations/{locationId}",
                        company.getId(), locationId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("New Address 999"));
    }

    @Test
    void updateReturnLocation_returns404ForUnknownLocation() throws Exception {
        User owner = createActiveUser("loc-update-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("address", "Updated Address");

        mockMvc.perform(patch("/companies/{companyId}/return-locations/{locationId}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateReturnLocation_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("loc-update-403-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID locationId = createLocationViaApi(owner, company.getId(), "Some Address");
        User outsider = createActiveUser("loc-update-403-out@example.com", "Password1!");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("address", "Hacked Address");

        mockMvc.perform(patch("/companies/{companyId}/return-locations/{locationId}",
                        company.getId(), locationId)
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /companies/{companyId}/return-locations/{locationId} ───────────

    @Test
    void deleteReturnLocation_returns204WhenSecondLocationExists() throws Exception {
        User owner = createActiveUser("loc-del@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        // Need two locations so deletion of one is allowed
        UUID locationId = createLocationViaApi(owner, company.getId(), "First Location");
        createLocationDirect(company, "Second Location", false);

        mockMvc.perform(delete("/companies/{companyId}/return-locations/{locationId}",
                        company.getId(), locationId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturnLocation_returns409WhenLastLocation() throws Exception {
        User owner = createActiveUser("loc-del-last@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID locationId = createLocationViaApi(owner, company.getId(), "Only Location");

        mockMvc.perform(delete("/companies/{companyId}/return-locations/{locationId}",
                        company.getId(), locationId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturnLocation_returns404ForUnknownLocation() throws Exception {
        User owner = createActiveUser("loc-del-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{companyId}/return-locations/{locationId}",
                        company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnLocation_returns403ForNonMember() throws Exception {
        User owner = createActiveUser("loc-del-403-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID locationId = createLocationViaApi(owner, company.getId(), "A Location");
        createLocationDirect(company, "B Location", false);
        User outsider = createActiveUser("loc-del-403-out@example.com", "Password1!");

        mockMvc.perform(delete("/companies/{companyId}/return-locations/{locationId}",
                        company.getId(), locationId)
                        .header("Authorization", bearer(accessTokenFor(outsider)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReturnLocation_returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(delete("/companies/{companyId}/return-locations/{locationId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }
}
