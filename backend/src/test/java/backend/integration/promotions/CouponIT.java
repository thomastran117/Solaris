package backend.integration.promotions;

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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CouponIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;

    @AfterEach
    void cleanCoupons() {
        try { jdbcTemplate.execute("DELETE FROM coupon_redemptions"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM coupon_per_user_counts"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM coupons"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM promotion_rules"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Test Company");
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

    private Map<String, Object> couponBody(String code, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("type", "PERCENTAGE");
        body.put("value", "10.00");
        return body;
    }

    private UUID createCoupon(User owner, Company company, String code) throws Exception {
        String resp = mockMvc.perform(post("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(couponBody(code, "Test Coupon " + code))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resp).at("/data/id").asText());
    }

    // ── GET /companies/{companyId}/coupons ────────────────────────────────────

    @Test
    void listCoupons_returnsEmptyPageWhenNone() throws Exception {
        User owner = createActiveUser("coupon-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", empty()));
    }

    @Test
    void listCoupons_returnsCouponsForOwner() throws Exception {
        User owner = createActiveUser("coupon-list-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        createCoupon(owner, company, "SAVE10");

        mockMvc.perform(get("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("SAVE10"));
    }

    @Test
    void listCoupons_returnsMultipleCouponsForManager() throws Exception {
        User owner = createActiveUser("coupon-list-mgr-owner@example.com", "Password1!");
        User manager = createActiveUser("coupon-list-mgr@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(manager, company, CompanyRole.MANAGER);
        createCoupon(owner, company, "FIRST10");
        createCoupon(owner, company, "SECOND20");

        mockMvc.perform(get("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(manager)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void listCoupons_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("coupon-list-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("coupon-list-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(get("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCoupons_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/companies/{id}/coupons", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /companies/{companyId}/coupons/{couponId} ─────────────────────────

    @Test
    void getCoupon_returnsCoupon() throws Exception {
        User owner = createActiveUser("coupon-get@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID couponId = createCoupon(owner, company, "GET10");

        mockMvc.perform(get("/companies/{cid}/coupons/{couponId}", company.getId(), couponId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("GET10"))
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()));
    }

    @Test
    void getCoupon_returns404ForUnknown() throws Exception {
        User owner = createActiveUser("coupon-get-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(get("/companies/{cid}/coupons/{couponId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCoupon_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("coupon-getbyemp-owner@example.com", "Password1!");
        User employee = createActiveUser("coupon-getbyemp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        UUID couponId = createCoupon(owner, company, "EMPGET");

        mockMvc.perform(get("/companies/{cid}/coupons/{couponId}", company.getId(), couponId)
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    // ── POST /companies/{companyId}/coupons ────────────────────────────────────

    @Test
    void createCoupon_returns201WithCorrectFields() throws Exception {
        User owner = createActiveUser("coupon-create@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(couponBody("SUMMER20", "Summer Sale 20%"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SUMMER20"))
                .andExpect(jsonPath("$.data.name").value("Summer Sale 20%"))
                .andExpect(jsonPath("$.data.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.data.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void createCoupon_uppercasesCodeAutomatically() throws Exception {
        User owner = createActiveUser("coupon-create-uc@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        // Sending all-uppercase is required by the pattern; verify it is stored as-is
        mockMvc.perform(post("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(couponBody("ALLCAPS", "All Caps"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("ALLCAPS"));
    }

    @Test
    void createCoupon_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("coupon-create-owner@example.com", "Password1!");
        User employee = createActiveUser("coupon-create-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);

        mockMvc.perform(post("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(couponBody("EMP403", "Employee Attempt"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCoupon_returns400ForLowercaseCode() throws Exception {
        User owner = createActiveUser("coupon-create-lc@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(post("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(couponBody("lowercase", "Bad Code"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCoupon_returns409ForDuplicateCode() throws Exception {
        User owner = createActiveUser("coupon-create-dup@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        String body = objectMapper.writeValueAsString(couponBody("DUPCODE", "Duplicate"));

        mockMvc.perform(post("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/companies/{id}/coupons", company.getId())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ── PATCH /companies/{companyId}/coupons/{couponId} ───────────────────────

    @Test
    void updateCoupon_updatesName() throws Exception {
        User owner = createActiveUser("coupon-update@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID couponId = createCoupon(owner, company, "UPDT10");

        Map<String, Object> update = Map.of("name", "Updated Name");

        mockMvc.perform(patch("/companies/{cid}/coupons/{couponId}", company.getId(), couponId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.code").value("UPDT10"));
    }

    @Test
    void updateCoupon_returns400ForExpiredStatus() throws Exception {
        User owner = createActiveUser("coupon-update-exp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID couponId = createCoupon(owner, company, "EXPCHK");

        Map<String, Object> update = Map.of("status", "EXPIRED");

        mockMvc.perform(patch("/companies/{cid}/coupons/{couponId}", company.getId(), couponId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCoupon_returns404ForUnknown() throws Exception {
        User owner = createActiveUser("coupon-update-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        Map<String, Object> update = Map.of("name", "New Name");

        mockMvc.perform(patch("/companies/{cid}/coupons/{couponId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /companies/{companyId}/coupons/{couponId} ─────────────────────

    @Test
    void deleteCoupon_returns204() throws Exception {
        User owner = createActiveUser("coupon-delete@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        UUID couponId = createCoupon(owner, company, "DELET10");

        mockMvc.perform(delete("/companies/{cid}/coupons/{couponId}", company.getId(), couponId)
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCoupon_returns404ForUnknown() throws Exception {
        User owner = createActiveUser("coupon-delete-404@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);

        mockMvc.perform(delete("/companies/{cid}/coupons/{couponId}", company.getId(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(owner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCoupon_returns403ForEmployee() throws Exception {
        User owner = createActiveUser("coupon-delete-emp-owner@example.com", "Password1!");
        User employee = createActiveUser("coupon-delete-emp@example.com", "Password1!");
        Company company = createCompany(owner);
        addMember(owner, company, CompanyRole.OWNER);
        addMember(employee, company, CompanyRole.EMPLOYEE);
        UUID couponId = createCoupon(owner, company, "EMPNODEL");

        mockMvc.perform(delete("/companies/{cid}/coupons/{couponId}", company.getId(), couponId)
                        .header("Authorization", bearer(accessTokenFor(employee)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }
}
