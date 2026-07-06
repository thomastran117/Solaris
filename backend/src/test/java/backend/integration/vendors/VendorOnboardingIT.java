package backend.integration.vendors;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.MarketplaceProfile;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.services.intf.payments.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VendorOnboardingIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private MarketplaceProfileRepository marketplaceProfileRepository;
    @MockitoBean private PaymentService paymentService;

    @AfterEach
    void cleanVendors() {
        try { jdbcTemplate.execute("DELETE FROM vendor_audit_logs"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM vendor_onboarding_documents"); } catch (Exception ignored) {}
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

    private Company createVendorCompany(User user) {
        Company company = new Company();
        company.setOwner(user);
        company.setName("Vendor Co " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        company = companyRepository.save(company);

        CompanyMembership membership = new CompanyMembership();
        membership.setCompany(company);
        membership.setUser(user);
        membership.setRole(CompanyRole.OWNER);
        membership.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        return company;
    }

    private String base(UUID marketplaceId) {
        return "/marketplaces/" + marketplaceId + "/vendors";
    }

    private String applyBody(UUID vendorCompanyId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("vendorCompanyId", vendorCompanyId.toString()));
    }

    private String taxBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "taxId", "EIN-12-3456789",
                "legalBusinessName", "Acme Corp LLC",
                "businessAddress", "123 Main St, Springfield, IL",
                "country", "US"
        ));
    }

    private String stripeLinkBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "returnUrl", "https://example.com/return",
                "refreshUrl", "https://example.com/refresh"
        ));
    }

    private String actionBody(String reason) throws Exception {
        return objectMapper.writeValueAsString(Map.of("reason", reason));
    }

    private UUID applyViaApi(User user, UUID marketplaceId, UUID vendorCompanyId) throws Exception {
        String response = mockMvc.perform(post(base(marketplaceId) + "/apply")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(vendorCompanyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.path("data").path("id").asText());
    }

    // ── POST /apply ───────────────────────────────────────────────────────────

    @Test
    void apply_returns201WithFields() throws Exception {
        User operator = createActiveUser("op-apply@example.com", "Password1!");
        User vendor = createActiveUser("vendor-apply@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);

        mockMvc.perform(post(base(marketplace.getId()) + "/apply")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(vendorCompany.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.marketplaceId").value(marketplace.getId().toString()))
                .andExpect(jsonPath("$.data.vendorCompanyId").value(vendorCompany.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.onboardingStep").value("PROFILE"))
                .andExpect(jsonPath("$.data.tier").value("STANDARD"));

        assertEquals("DRAFT",
                jdbcTemplate.queryForObject("SELECT status FROM marketplace_vendors", String.class),
                "Vendor application should be persisted in DRAFT");
    }

    @Test
    void apply_missingVendorCompanyId_returns400() throws Exception {
        User operator = createActiveUser("op-apply-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-apply-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/apply")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apply_marketplaceNotAcceptingApplications_returns400() throws Exception {
        User operator = createActiveUser("op-apply-closed@example.com", "Password1!");
        User vendor = createActiveUser("vendor-apply-closed@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);

        MarketplaceProfile profile = marketplaceProfileRepository.findByCompanyId(marketplace.getId()).orElseThrow();
        profile.setAcceptingApplications(false);
        marketplaceProfileRepository.save(profile);

        mockMvc.perform(post(base(marketplace.getId()) + "/apply")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(vendorCompany.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apply_duplicateApplication_returns409() throws Exception {
        User operator = createActiveUser("op-apply-dup@example.com", "Password1!");
        User vendor = createActiveUser("vendor-apply-dup@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/apply")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(vendorCompany.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void apply_userNotOwnerOfVendorCompany_returns403() throws Exception {
        User operator = createActiveUser("op-apply-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-apply-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        User vendorOwner = createActiveUser("vendorowner-apply-403@example.com", "Password1!");
        Company vendorCompany = createVendorCompany(vendorOwner);

        mockMvc.perform(post(base(marketplace.getId()) + "/apply")
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(vendorCompany.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void apply_unknownMarketplace_returns404() throws Exception {
        User vendor = createActiveUser("vendor-apply-404@example.com", "Password1!");
        Company vendorCompany = createVendorCompany(vendor);

        mockMvc.perform(post(base(UUID.randomUUID()) + "/apply")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(vendorCompany.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void apply_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-apply-401@example.com", "Password1!");
        User vendor = createActiveUser("vendor-apply-401u@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);

        mockMvc.perform(post(base(marketplace.getId()) + "/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(vendorCompany.getId())))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /{vendorId}/onboarding/profile ──────────────────────────────────

    @Test
    void updateProfile_returns200_advancesOnboardingStep() throws Exception {
        User operator = createActiveUser("op-profile@example.com", "Password1!");
        User vendor = createActiveUser("vendor-profile@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        String body = objectMapper.writeValueAsString(Map.of("displayName", "My Shop"));
        mockMvc.perform(patch(base(marketplace.getId()) + "/" + vendorId + "/onboarding/profile")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStep").value("TAX"));
    }

    @Test
    void updateProfile_unknownVendor_returns404() throws Exception {
        User operator = createActiveUser("op-profile-404@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        User vendor = createActiveUser("vendor-profile-404@example.com", "Password1!");

        mockMvc.perform(patch(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/profile")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProfile_nonOwner_returns403() throws Exception {
        User operator = createActiveUser("op-profile-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-profile-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-profile-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(patch(base(marketplace.getId()) + "/" + vendorId + "/onboarding/profile")
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-profile-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(patch(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/onboarding/tax ───────────────────────────────────────

    @Test
    void submitTax_returns200_advancesOnboardingStep() throws Exception {
        User operator = createActiveUser("op-tax@example.com", "Password1!");
        User vendor = createActiveUser("vendor-tax@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        // Advance from PROFILE to TAX first
        mockMvc.perform(patch(base(marketplace.getId()) + "/" + vendorId + "/onboarding/profile")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "My Shop"))))
                .andExpect(status().isOk());

        // Now submitTax advances TAX → BANKING
        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/tax")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taxBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStep").value("BANKING"));
    }

    @Test
    void submitTax_missingRequiredFields_returns400() throws Exception {
        User operator = createActiveUser("op-tax-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-tax-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/tax")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "legalBusinessName", "Acme",
                                "businessAddress", "123 Main",
                                "country", "US"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitTax_unknownVendor_returns404() throws Exception {
        User operator = createActiveUser("op-tax-404@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        User vendor = createActiveUser("vendor-tax-404@example.com", "Password1!");

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/tax")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taxBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitTax_nonOwner_returns403() throws Exception {
        User operator = createActiveUser("op-tax-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-tax-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-tax-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/tax")
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taxBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitTax_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-tax-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/tax")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taxBody()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/onboarding/stripe-link ───────────────────────────────

    @Test
    void stripeLink_returns200WithUrl() throws Exception {
        User operator = createActiveUser("op-stripe@example.com", "Password1!");
        User vendor = createActiveUser("vendor-stripe@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        when(paymentService.createConnectAccount(anyString(), anyString(), any()))
                .thenReturn(new PaymentService.ConnectAccountResult("acct_test_123", false, false, false));
        when(paymentService.generateConnectOnboardingLink(anyString(), anyString(), anyString()))
                .thenReturn(new PaymentService.ConnectOnboardingLinkResult(
                        "https://connect.stripe.com/setup/test", Instant.now().plusSeconds(300)));

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/stripe-link")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stripeLinkBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(startsWith("https://connect.stripe.com")))
                .andExpect(jsonPath("$.data.stripeConnectAccountId").value("acct_test_123"));
    }

    @Test
    void stripeLink_missingReturnUrl_returns400() throws Exception {
        User operator = createActiveUser("op-stripe-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-stripe-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/stripe-link")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshUrl", "https://example.com/refresh"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stripeLink_unknownVendor_returns404() throws Exception {
        User operator = createActiveUser("op-stripe-404@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        User vendor = createActiveUser("vendor-stripe-404@example.com", "Password1!");

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/stripe-link")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stripeLinkBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void stripeLink_nonOwner_returns403() throws Exception {
        User operator = createActiveUser("op-stripe-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-stripe-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-stripe-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/stripe-link")
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stripeLinkBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void stripeLink_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-stripe-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/stripe-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stripeLinkBody()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/onboarding/documents ─────────────────────────────────

    @Test
    void recordDocument_returns201WithFields() throws Exception {
        User operator = createActiveUser("op-doc@example.com", "Password1!");
        User vendor = createActiveUser("vendor-doc@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/documents")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .param("documentType", "BUSINESS_LICENSE")
                        .param("s3Key", "vendors/" + vendorId + "/business_license.pdf"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.documentType").value("BUSINESS_LICENSE"))
                .andExpect(jsonPath("$.data.s3Key").value("vendors/" + vendorId + "/business_license.pdf"));

        Integer docRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vendor_onboarding_documents", Integer.class);
        assertEquals(1, docRows, "Onboarding document should be persisted");
    }

    @Test
    void recordDocument_missingDocumentType_returns400() throws Exception {
        User operator = createActiveUser("op-doc-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-doc-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/documents")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .param("s3Key", "vendors/doc.pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordDocument_unknownVendor_returns404() throws Exception {
        User operator = createActiveUser("op-doc-404@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        User vendor = createActiveUser("vendor-doc-404@example.com", "Password1!");

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/documents")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .param("documentType", "IDENTITY")
                        .param("s3Key", "vendors/doc.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void recordDocument_nonOwner_returns403() throws Exception {
        User operator = createActiveUser("op-doc-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-doc-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-doc-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/documents")
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .param("documentType", "IDENTITY")
                        .param("s3Key", "vendors/doc.pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordDocument_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-doc-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/documents")
                        .param("documentType", "IDENTITY")
                        .param("s3Key", "vendors/doc.pdf"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{vendorId}/onboarding/documents ──────────────────────────────────

    @Test
    void listDocuments_emptyBeforeUpload_returns200() throws Exception {
        User operator = createActiveUser("op-listdoc@example.com", "Password1!");
        User vendor = createActiveUser("vendor-listdoc@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(get(base(marketplace.getId()) + "/" + vendorId + "/onboarding/documents")
                        .header("Authorization", bearer(accessTokenFor(vendor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listDocuments_returnsDocumentAfterUpload() throws Exception {
        User operator = createActiveUser("op-listdoc2@example.com", "Password1!");
        User vendor = createActiveUser("vendor-listdoc2@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/onboarding/documents")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .param("documentType", "TAX_FORM")
                        .param("s3Key", "vendors/tax.pdf"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(base(marketplace.getId()) + "/" + vendorId + "/onboarding/documents")
                        .header("Authorization", bearer(accessTokenFor(vendor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].documentType").value("TAX_FORM"));
    }

    @Test
    void listDocuments_nonOwner_returns403() throws Exception {
        User operator = createActiveUser("op-listdoc-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-listdoc-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-listdoc-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(get(base(marketplace.getId()) + "/" + vendorId + "/onboarding/documents")
                        .header("Authorization", bearer(accessTokenFor(stranger))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listDocuments_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-listdoc-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/onboarding/documents"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/submit ───────────────────────────────────────────────

    @Test
    void submitForReview_returns200_setsStatusApplied() throws Exception {
        User operator = createActiveUser("op-submit@example.com", "Password1!");
        User vendor = createActiveUser("vendor-submit@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/submit")
                        .header("Authorization", bearer(accessTokenFor(vendor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.onboardingStep").value("REVIEW"));

        assertEquals("APPLIED",
                jdbcTemplate.queryForObject("SELECT status FROM marketplace_vendors", String.class));
    }

    @Test
    void submitForReview_whenAlreadyApplied_returns400() throws Exception {
        User operator = createActiveUser("op-submit-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-submit-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        // First submit: moves to APPLIED
        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/submit")
                        .header("Authorization", bearer(accessTokenFor(vendor))))
                .andExpect(status().isOk());

        // Second submit: APPLIED status is not allowed
        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/submit")
                        .header("Authorization", bearer(accessTokenFor(vendor))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitForReview_nonOwner_returns403() throws Exception {
        User operator = createActiveUser("op-submit-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-submit-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-submit-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/submit")
                        .header("Authorization", bearer(accessTokenFor(stranger))))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitForReview_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-submit-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/submit"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /me ───────────────────────────────────────────────────────────────

    @Test
    void getMyVendorRecord_returns200() throws Exception {
        User operator = createActiveUser("op-me@example.com", "Password1!");
        User vendor = createActiveUser("vendor-me@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(get(base(marketplace.getId()) + "/me")
                        .header("Authorization", bearer(accessTokenFor(vendor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorCompanyId").value(vendorCompany.getId().toString()))
                .andExpect(jsonPath("$.data.marketplaceId").value(marketplace.getId().toString()));
    }

    @Test
    void getMyVendorRecord_whenNoRecord_returns404() throws Exception {
        User operator = createActiveUser("op-me-404@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        User vendor = createActiveUser("vendor-me-404@example.com", "Password1!");

        mockMvc.perform(get(base(marketplace.getId()) + "/me")
                        .header("Authorization", bearer(accessTokenFor(vendor))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyVendorRecord_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-me-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get(base(marketplace.getId()) + "/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET / (listVendors) ───────────────────────────────────────────────────

    @Test
    void listVendors_returns200PaginatedForOperator() throws Exception {
        User operator = createActiveUser("op-list@example.com", "Password1!");
        User vendor = createActiveUser("vendor-list@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(get(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void listVendors_withStatusFilter_returnsMatchingOnly() throws Exception {
        User operator = createActiveUser("op-list-filter@example.com", "Password1!");
        User vendor = createActiveUser("vendor-list-filter@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(get(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .param("status", "APPLIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void listVendors_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("op-list-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-list-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get(base(marketplace.getId()))
                        .header("Authorization", bearer(accessTokenFor(stranger))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listVendors_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-list-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get(base(marketplace.getId())))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /{vendorId} ───────────────────────────────────────────────────────

    @Test
    void getVendor_returns200ForOperator() throws Exception {
        User operator = createActiveUser("op-get@example.com", "Password1!");
        User vendor = createActiveUser("vendor-get@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(get(base(marketplace.getId()) + "/" + vendorId)
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(vendorId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void getVendor_unknownVendor_returns404() throws Exception {
        User operator = createActiveUser("op-get-404@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get(base(marketplace.getId()) + "/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getVendor_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("op-get-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-get-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-get-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(get(base(marketplace.getId()) + "/" + vendorId)
                        .header("Authorization", bearer(accessTokenFor(stranger))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVendor_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-get-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(get(base(marketplace.getId()) + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/approve ──────────────────────────────────────────────

    @Test
    void approve_returns200_setsStatusApproved() throws Exception {
        User operator = createActiveUser("op-approve@example.com", "Password1!");
        User vendor = createActiveUser("vendor-approve@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/approve")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.onboardingStep").value("COMPLETE"));

        assertEquals("APPROVED",
                jdbcTemplate.queryForObject("SELECT status FROM marketplace_vendors", String.class));
    }

    @Test
    void approve_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("op-approve-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-approve-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/approve")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-approve-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/reject ───────────────────────────────────────────────

    @Test
    void reject_returns200_setsStatusRejected() throws Exception {
        User operator = createActiveUser("op-reject@example.com", "Password1!");
        User vendor = createActiveUser("vendor-reject@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/reject")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("Does not meet our standards")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Does not meet our standards"));

        assertEquals("REJECTED",
                jdbcTemplate.queryForObject("SELECT status FROM marketplace_vendors", String.class));
    }

    @Test
    void reject_missingReason_returns400() throws Exception {
        User operator = createActiveUser("op-reject-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-reject-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/reject")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reject_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("op-reject-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-reject-403@example.com", "Password1!");
        User stranger = createActiveUser("stranger-reject-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/reject")
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("reason")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reject_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-reject-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("reason")))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/suspend ──────────────────────────────────────────────

    @Test
    void suspend_returns200_setsStatusSuspended() throws Exception {
        User operator = createActiveUser("op-suspend@example.com", "Password1!");
        User vendor = createActiveUser("vendor-suspend@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/suspend")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("Policy violation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertEquals("SUSPENDED",
                jdbcTemplate.queryForObject("SELECT status FROM marketplace_vendors", String.class));
    }

    @Test
    void suspend_missingReason_returns400() throws Exception {
        User operator = createActiveUser("op-suspend-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-suspend-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/suspend")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspend_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("op-suspend-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-suspend-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/suspend")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("reason")))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspend_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-suspend-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("reason")))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/reinstate ────────────────────────────────────────────

    @Test
    void reinstate_returns200_fromSuspended() throws Exception {
        User operator = createActiveUser("op-reinstate@example.com", "Password1!");
        User vendor = createActiveUser("vendor-reinstate@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/suspend")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("Policy violation")))
                .andExpect(status().isOk());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/reinstate")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void reinstate_whenNotSuspended_returns400() throws Exception {
        User operator = createActiveUser("op-reinstate-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-reinstate-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/approve")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/reinstate")
                        .header("Authorization", bearer(accessTokenFor(operator))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reinstate_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("op-reinstate-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-reinstate-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/reinstate")
                        .header("Authorization", bearer(accessTokenFor(vendor))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reinstate_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-reinstate-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/reinstate"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{vendorId}/needs-info ───────────────────────────────────────────

    @Test
    void needsInfo_returns200_setsStatusNeedsInfo() throws Exception {
        User operator = createActiveUser("op-needsinfo@example.com", "Password1!");
        User vendor = createActiveUser("vendor-needsinfo@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/needs-info")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("Please provide proof of address")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.data.onboardingStep").value("DOCUMENTS"));
    }

    @Test
    void needsInfo_missingReason_returns400() throws Exception {
        User operator = createActiveUser("op-needsinfo-400@example.com", "Password1!");
        User vendor = createActiveUser("vendor-needsinfo-400@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/needs-info")
                        .header("Authorization", bearer(accessTokenFor(operator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsInfo_nonOperator_returns403() throws Exception {
        User operator = createActiveUser("op-needsinfo-403@example.com", "Password1!");
        User vendor = createActiveUser("vendor-needsinfo-403@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);
        Company vendorCompany = createVendorCompany(vendor);
        UUID vendorId = applyViaApi(vendor, marketplace.getId(), vendorCompany.getId());

        mockMvc.perform(post(base(marketplace.getId()) + "/" + vendorId + "/needs-info")
                        .header("Authorization", bearer(accessTokenFor(vendor)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("reason")))
                .andExpect(status().isForbidden());
    }

    @Test
    void needsInfo_unauthenticated_returns401() throws Exception {
        User operator = createActiveUser("op-needsinfo-401@example.com", "Password1!");
        Company marketplace = createMarketplace(operator);

        mockMvc.perform(post(base(marketplace.getId()) + "/" + UUID.randomUUID() + "/needs-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody("reason")))
                .andExpect(status().isUnauthorized());
    }
}
