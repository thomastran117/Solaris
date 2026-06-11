package backend.integration.marketplace;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.User;
import backend.models.core.VendorSLABreach;
import backend.models.core.VendorSLAMetric;
import backend.models.core.VendorSLAPolicy;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.SLABreachAction;
import backend.models.enums.VendorStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorSLABreachRepository;
import backend.repositories.VendorSLAMetricRepository;
import backend.repositories.VendorSLAPolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketplaceAnalyticsIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private MarketplaceProfileRepository marketplaceProfileRepository;
    @Autowired private MarketplaceVendorRepository marketplaceVendorRepository;
    @Autowired private VendorSLAPolicyRepository policyRepository;
    @Autowired private VendorSLAMetricRepository metricRepository;
    @Autowired private VendorSLABreachRepository breachRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM vendor_sla_breaches"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM vendor_sla_metrics"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM vendor_sla_policies"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM marketplace_vendors"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM marketplace_profiles"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM company_memberships"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM companies"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createMarketplace(User operator) {
        Company company = new Company();
        company.setOwner(operator);
        company.setName("Market Co " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        CompanyMembership membership = new CompanyMembership();
        membership.setCompany(company);
        membership.setUser(operator);
        membership.setRole(CompanyRole.OWNER);
        membership.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(company);
        profile.setSlug("market-" + UUID.randomUUID().toString().substring(0, 8));
        profile.setAcceptingApplications(true);
        marketplaceProfileRepository.save(profile);

        return company;
    }

    private Company createVendorCompany(User owner) {
        Company company = new Company();
        company.setOwner(owner);
        company.setName("Vendor Co " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(company);
    }

    private MarketplaceVendor createVendor(Company marketplace, Company vendorCompany) {
        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setMarketplace(marketplace);
        vendor.setVendorCompany(vendorCompany);
        vendor.setStatus(VendorStatus.APPROVED);
        return marketplaceVendorRepository.save(vendor);
    }

    private VendorSLAPolicy newPolicy(UUID marketplaceId, String name, boolean active) {
        VendorSLAPolicy policy = new VendorSLAPolicy();
        policy.setMarketplaceId(marketplaceId);
        policy.setName(name);
        policy.setBreachAction(SLABreachAction.WARN);
        policy.setActive(active);
        return policy;
    }

    private VendorSLAMetric newMetric(UUID vendorId, UUID marketplaceId, LocalDate date) {
        VendorSLAMetric metric = new VendorSLAMetric();
        metric.setVendorId(vendorId);
        metric.setMarketplaceId(marketplaceId);
        metric.setDate(date);
        metric.setTotalOrders(10);
        metric.setCancellationRate(0.05);
        metric.setRefundRate(0.02);
        metric.setLateShipmentRate(0.01);
        metric.setDefectRate(0.03);
        return metric;
    }

    private VendorSLABreach newBreach(UUID vendorId, UUID policyId, Instant resolvedAt) {
        VendorSLABreach breach = new VendorSLABreach();
        breach.setVendorId(vendorId);
        breach.setPolicyId(policyId);
        breach.setMetric("cancellationRate");
        breach.setActualValue(0.15);
        breach.setThreshold(0.05);
        breach.setDetectedAt(Instant.now());
        breach.setResolvedAt(resolvedAt);
        breach.setActionTaken(SLABreachAction.WARN);
        return breach;
    }

    private String policyBody(String name, String breachAction, int evaluationWindowDays) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "breachAction", breachAction,
                "evaluationWindowDays", evaluationWindowDays
        ));
    }

    // ── GET /marketplaces/{marketplaceId}/analytics/summary ───────────────────

    @Test
    void getSummary_emptyData_returns200WithZeros() throws Exception {
        User operator = createActiveUser("ma-summary-empty@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/analytics/summary")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marketplaceId").value(marketplace.getId().toString()))
                .andExpect(jsonPath("$.data.windowDays").value(30))
                .andExpect(jsonPath("$.data.totalOrders").value(0))
                .andExpect(jsonPath("$.data.gmv").value(0))
                .andExpect(jsonPath("$.data.totalCommission").value(0))
                .andExpect(jsonPath("$.data.takeRate").value(0.0))
                .andExpect(jsonPath("$.data.activeVendors").value(0))
                .andExpect(jsonPath("$.data.ordersDaily").isArray())
                .andExpect(jsonPath("$.data.ordersDaily").isEmpty());
    }

    @Test
    void getSummary_daysAboveMax_clampsTo365() throws Exception {
        User operator = createActiveUser("ma-summary-clamp-hi@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/analytics/summary")
                        .param("days", "1000")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowDays").value(365));
    }

    @Test
    void getSummary_daysBelowMin_clampsTo7() throws Exception {
        User operator = createActiveUser("ma-summary-clamp-lo@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/analytics/summary")
                        .param("days", "1")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowDays").value(7));
    }

    @Test
    void getSummary_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("ma-summary-op@example.com", "Password1!");
        User other = createActiveUser("ma-summary-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/analytics/summary")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_unknownMarketplace_returns403() throws Exception {
        User user = createActiveUser("ma-summary-unknown@example.com", "Password1!");

        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/analytics/summary")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSummary_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/analytics/summary"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/analytics/top-vendors ───────────────

    @Test
    void getTopVendors_emptyData_returns200EmptyList() throws Exception {
        User operator = createActiveUser("ma-topvendors-empty@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/analytics/top-vendors")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getTopVendors_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("ma-topvendors-op@example.com", "Password1!");
        User other = createActiveUser("ma-topvendors-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/analytics/top-vendors")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTopVendors_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/analytics/top-vendors"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /marketplaces/{marketplaceId}/sla/policies ────────────────────────

    @Test
    void createPolicy_validRequest_returns201() throws Exception {
        User operator = createActiveUser("ma-create-policy@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/policies")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("Standard SLA", "WARN", 30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.marketplaceId").value(marketplace.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Standard SLA"))
                .andExpect(jsonPath("$.data.breachAction").value("WARN"))
                .andExpect(jsonPath("$.data.evaluationWindowDays").value(30))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void createPolicy_invalidBreachAction_returns400() throws Exception {
        User operator = createActiveUser("ma-create-policy-badaction@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/policies")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("Bad Action SLA", "INVALID_ACTION", 30)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPolicy_blankName_returns400() throws Exception {
        User operator = createActiveUser("ma-create-policy-noname@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/policies")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("", "WARN", 30)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPolicy_evaluationWindowBelowMin_returns400() throws Exception {
        User operator = createActiveUser("ma-create-policy-lowwindow@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/policies")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("Too Short Window", "WARN", 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPolicy_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("ma-create-policy-op@example.com", "Password1!");
        User other = createActiveUser("ma-create-policy-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/policies")
                        .header("Authorization", bearer(accessTokenFor(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("Unauthorized SLA", "WARN", 30)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPolicy_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/marketplaces/" + UUID.randomUUID() + "/sla/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("Test", "WARN", 30)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/sla/policies ─────────────────────────

    @Test
    void listPolicies_returnsAllPolicies() throws Exception {
        User operator = createActiveUser("ma-list-policies@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        policyRepository.save(newPolicy(marketplace.getId(), "Active Policy", true));
        policyRepository.save(newPolicy(marketplace.getId(), "Inactive Policy", false));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/sla/policies")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].name", containsInAnyOrder("Active Policy", "Inactive Policy")));
    }

    @Test
    void listPolicies_empty_returns200EmptyList() throws Exception {
        User operator = createActiveUser("ma-list-policies-empty@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/sla/policies")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listPolicies_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/sla/policies"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/sla/policies/active ──────────────────

    @Test
    void getActivePolicy_exists_returns200() throws Exception {
        User user = createActiveUser("ma-active-policy@example.com", "Password1!");
        Company marketplace = createMarketplace(user);
        VendorSLAPolicy active = policyRepository.save(newPolicy(marketplace.getId(), "Active SLA", true));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/sla/policies/active")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(active.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Active SLA"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void getActivePolicy_none_returns404() throws Exception {
        User user = createActiveUser("ma-active-policy-none@example.com", "Password1!");
        Company marketplace = createMarketplace(user);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/sla/policies/active")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getActivePolicy_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/sla/policies/active"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/sla/metrics ───────

    @Test
    void listMetrics_vendorOwner_returns200() throws Exception {
        User vendorOwner = createActiveUser("ma-metrics-owner@example.com", "Password1!");
        User operator = createActiveUser("ma-metrics-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        metricRepository.save(newMetric(vendor.getId(), marketplace.getId(), LocalDate.now()));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/metrics")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void listMetrics_operator_returns200() throws Exception {
        User vendorOwner = createActiveUser("ma-metrics-owner2@example.com", "Password1!");
        User operator = createActiveUser("ma-metrics-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        metricRepository.save(newMetric(vendor.getId(), marketplace.getId(), LocalDate.now()));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/metrics")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void listMetrics_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("ma-metrics-owner3@example.com", "Password1!");
        User operator = createActiveUser("ma-metrics-op3@example.com", "Password1!");
        User other = createActiveUser("ma-metrics-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/metrics")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMetrics_unknownVendor_returns404() throws Exception {
        User operator = createActiveUser("ma-metrics-op4@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + UUID.randomUUID() + "/sla/metrics")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMetrics_vendorFromOtherMarketplace_returns404() throws Exception {
        User vendorOwner = createActiveUser("ma-metrics-owner5@example.com", "Password1!");
        User operatorA = createActiveUser("ma-metrics-opA@example.com", "Password1!");
        User operatorB = createActiveUser("ma-metrics-opB@example.com", "Password1!");
        Company marketplaceA = createMarketplace(operatorA);
        Company marketplaceB = createMarketplace(operatorB);
        MarketplaceVendor vendor = createVendor(marketplaceA, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplaceB.getId() + "/vendors/" + vendor.getId() + "/sla/metrics")
                        .header("Authorization", bearer(accessTokenFor(operatorB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMetrics_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/sla/metrics"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/sla/metrics/latest ─

    @Test
    void getLatestMetric_exists_returns200() throws Exception {
        User vendorOwner = createActiveUser("ma-latest-owner@example.com", "Password1!");
        User operator = createActiveUser("ma-latest-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        metricRepository.save(newMetric(vendor.getId(), marketplace.getId(), LocalDate.now().minusDays(1)));
        metricRepository.save(newMetric(vendor.getId(), marketplace.getId(), LocalDate.now()));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/metrics/latest")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data.date").value(LocalDate.now().toString()));
    }

    @Test
    void getLatestMetric_none_returns404() throws Exception {
        User vendorOwner = createActiveUser("ma-latest-owner-none@example.com", "Password1!");
        User operator = createActiveUser("ma-latest-op-none@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/metrics/latest")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLatestMetric_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("ma-latest-owner2@example.com", "Password1!");
        User operator = createActiveUser("ma-latest-op2@example.com", "Password1!");
        User other = createActiveUser("ma-latest-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/metrics/latest")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLatestMetric_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/sla/metrics/latest"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /marketplaces/{marketplaceId}/vendors/{vendorId}/sla/breaches ──────

    @Test
    void listBreaches_vendorOwner_returns200() throws Exception {
        User vendorOwner = createActiveUser("ma-breaches-owner@example.com", "Password1!");
        User operator = createActiveUser("ma-breaches-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        VendorSLAPolicy policy = policyRepository.save(newPolicy(marketplace.getId(), "Breach Policy", true));
        breachRepository.save(newBreach(vendor.getId(), policy.getId(), null));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/breaches")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.data[0].resolvedAt").doesNotExist());
    }

    @Test
    void listBreaches_empty_returns200EmptyPage() throws Exception {
        User vendorOwner = createActiveUser("ma-breaches-owner-empty@example.com", "Password1!");
        User operator = createActiveUser("ma-breaches-op-empty@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/breaches")
                        .header("Authorization", bearer(accessTokenFor(vendorOwner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listBreaches_unauthorizedUser_returns403() throws Exception {
        User vendorOwner = createActiveUser("ma-breaches-owner2@example.com", "Password1!");
        User operator = createActiveUser("ma-breaches-op2@example.com", "Password1!");
        User other = createActiveUser("ma-breaches-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));

        mockMvc.perform(get("/marketplaces/" + marketplace.getId() + "/vendors/" + vendor.getId() + "/sla/breaches")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listBreaches_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/marketplaces/" + UUID.randomUUID() + "/vendors/" + UUID.randomUUID() + "/sla/breaches"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /marketplaces/{marketplaceId}/sla/breaches/{breachId}/resolve ─────

    @Test
    void resolveBreach_validRequest_returns200() throws Exception {
        User vendorOwner = createActiveUser("ma-resolve-owner@example.com", "Password1!");
        User operator = createActiveUser("ma-resolve-op@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        VendorSLAPolicy policy = policyRepository.save(newPolicy(marketplace.getId(), "Resolve Policy", true));
        VendorSLABreach breach = breachRepository.save(newBreach(vendor.getId(), policy.getId(), null));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/breaches/" + breach.getId() + "/resolve")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(breach.getId().toString()))
                .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());
    }

    @Test
    void resolveBreach_alreadyResolved_returns400() throws Exception {
        User vendorOwner = createActiveUser("ma-resolve-owner2@example.com", "Password1!");
        User operator = createActiveUser("ma-resolve-op2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        VendorSLAPolicy policy = policyRepository.save(newPolicy(marketplace.getId(), "Resolve Policy 2", true));
        VendorSLABreach breach = breachRepository.save(newBreach(vendor.getId(), policy.getId(), Instant.now()));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/breaches/" + breach.getId() + "/resolve")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolveBreach_unknownBreach_returns404() throws Exception {
        User operator = createActiveUser("ma-resolve-op3@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/breaches/" + UUID.randomUUID() + "/resolve")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveBreach_nonOperator_returns403() throws Exception {
        User vendorOwner = createActiveUser("ma-resolve-owner3@example.com", "Password1!");
        User operator = createActiveUser("ma-resolve-op4@example.com", "Password1!");
        User other = createActiveUser("ma-resolve-other@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        MarketplaceVendor vendor = createVendor(marketplace, createVendorCompany(vendorOwner));
        VendorSLAPolicy policy = policyRepository.save(newPolicy(marketplace.getId(), "Resolve Policy 3", true));
        VendorSLABreach breach = breachRepository.save(newBreach(vendor.getId(), policy.getId(), null));

        mockMvc.perform(post("/marketplaces/" + marketplace.getId() + "/sla/breaches/" + breach.getId() + "/resolve")
                        .header("Authorization", bearer(accessTokenFor(other))))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolveBreach_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/marketplaces/" + UUID.randomUUID() + "/sla/breaches/" + UUID.randomUUID() + "/resolve"))
                .andExpect(status().isUnauthorized());
    }
}
