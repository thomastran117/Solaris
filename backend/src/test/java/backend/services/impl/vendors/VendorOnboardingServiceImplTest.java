package backend.services.impl.vendors;

import backend.dtos.requests.vendor.ApplyVendorRequest;
import backend.dtos.requests.vendor.GenerateStripeOnboardingLinkRequest;
import backend.dtos.requests.vendor.SubmitVendorTaxRequest;
import backend.dtos.requests.vendor.UpdateVendorProfileRequest;
import backend.dtos.requests.vendor.VendorActionRequest;
import backend.dtos.responses.vendor.MarketplaceVendorResponse;
import backend.dtos.responses.vendor.StripeOnboardingLinkResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.VendorOnboardingDocument;
import backend.models.enums.OnboardingStep;
import backend.models.enums.StripeConnectStatus;
import backend.models.enums.VendorDocumentType;
import backend.models.enums.VendorStatus;
import backend.models.enums.VendorTier;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.UserRepository;
import backend.repositories.VendorAuditLogRepository;
import backend.repositories.VendorOnboardingDocumentRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.payments.PaymentService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VendorOnboardingServiceImplTest {

    private static final UUID MARKETPLACE_ID  = TestIds.uuid(1);
    private static final UUID VENDOR_ID       = TestIds.uuid(2);
    private static final UUID USER_ID         = TestIds.uuid(3);
    private static final UUID COMPANY_ID      = TestIds.uuid(4);

    private MarketplaceProfileRepository marketplaceProfileRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private VendorOnboardingDocumentRepository documentRepository;
    private VendorAuditLogRepository auditLogRepository;
    private CompanyAccessService companyAccessService;
    private UserRepository userRepository;
    private PaymentService paymentService;
    private VendorOnboardingServiceImpl service;

    @BeforeEach
    void setUp() {
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        marketplaceVendorRepository  = mock(MarketplaceVendorRepository.class);
        documentRepository           = mock(VendorOnboardingDocumentRepository.class);
        auditLogRepository           = mock(VendorAuditLogRepository.class);
        companyAccessService         = mock(CompanyAccessService.class);
        userRepository               = mock(UserRepository.class);
        paymentService               = mock(PaymentService.class);

        service = new VendorOnboardingServiceImpl(
                marketplaceProfileRepository,
                marketplaceVendorRepository,
                documentRepository,
                auditLogRepository,
                companyAccessService,
                userRepository,
                paymentService);
    }

    // ─── applyToMarketplace ───────────────────────────────────────────────────

    @Test
    void apply_happyPath_savesVendorAsDraft() {
        MarketplaceProfile profile = makeMarketplaceProfile(true);
        Company vendorCompany = makeCompany(COMPANY_ID, "Vendor Co");
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(profile));
        when(companyAccessService.require(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(vendorCompany);
        when(marketplaceVendorRepository.existsByMarketplaceIdAndVendorCompanyId(MARKETPLACE_ID, COMPANY_ID)).thenReturn(false);
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> {
            MarketplaceVendor v = inv.getArgument(0);
            v.setId(VENDOR_ID);
            return v;
        });

        ApplyVendorRequest req = mock(ApplyVendorRequest.class);
        when(req.getVendorCompanyId()).thenReturn(COMPANY_ID);

        MarketplaceVendorResponse resp = service.applyToMarketplace(MARKETPLACE_ID, USER_ID, req);

        assertEquals("DRAFT", resp.getStatus());
        verify(marketplaceVendorRepository).save(any(MarketplaceVendor.class));
    }

    @Test
    void apply_marketplaceNotAcceptingApplications_throwsBadRequest() {
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(makeMarketplaceProfile(false)));

        ApplyVendorRequest req = mock(ApplyVendorRequest.class);
        when(req.getVendorCompanyId()).thenReturn(COMPANY_ID);

        assertThrows(BadRequestException.class,
                () -> service.applyToMarketplace(MARKETPLACE_ID, USER_ID, req));
    }

    @Test
    void apply_duplicateApplication_throwsConflict() {
        MarketplaceProfile profile = makeMarketplaceProfile(true);
        Company vendorCompany = makeCompany(COMPANY_ID, "Vendor Co");
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(profile));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendorCompany);
        when(marketplaceVendorRepository.existsByMarketplaceIdAndVendorCompanyId(any(), any())).thenReturn(true);

        ApplyVendorRequest req = mock(ApplyVendorRequest.class);
        when(req.getVendorCompanyId()).thenReturn(COMPANY_ID);

        assertThrows(ConflictException.class,
                () -> service.applyToMarketplace(MARKETPLACE_ID, USER_ID, req));
    }

    // ─── updateProfile ────────────────────────────────────────────────────────

    @Test
    void updateProfile_fromProfileStep_advancesToTax() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.DRAFT, OnboardingStep.PROFILE);
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceVendorResponse resp = service.updateProfile(
                MARKETPLACE_ID, VENDOR_ID, USER_ID, mock(UpdateVendorProfileRequest.class));

        assertEquals("TAX", resp.getOnboardingStep());
    }

    @Test
    void updateProfile_alreadyComplete_throwsBadRequest() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPROVED, OnboardingStep.COMPLETE);
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());

        assertThrows(BadRequestException.class,
                () -> service.updateProfile(MARKETPLACE_ID, VENDOR_ID, USER_ID, mock(UpdateVendorProfileRequest.class)));
    }

    // ─── submitTaxInfo ────────────────────────────────────────────────────────

    @Test
    void submitTaxInfo_fromTaxStep_advancesToBanking() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.DRAFT, OnboardingStep.TAX);
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceVendorResponse resp = service.submitTaxInfo(
                MARKETPLACE_ID, VENDOR_ID, USER_ID, mock(SubmitVendorTaxRequest.class));

        assertEquals("BANKING", resp.getOnboardingStep());
    }

    // ─── generateStripeOnboardingLink ─────────────────────────────────────────

    @Test
    void generateStripeLink_noExistingAccount_createsConnectAccount() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.DRAFT, OnboardingStep.BANKING);
        // stripeConnectAccountId is null → new account created
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());

        PaymentService.ConnectAccountResult acct = new PaymentService.ConnectAccountResult("acct_new", false, false, false);
        when(paymentService.createConnectAccount(any(), any(), any())).thenReturn(acct);

        PaymentService.ConnectOnboardingLinkResult link =
                new PaymentService.ConnectOnboardingLinkResult("https://stripe.com/onboard", null);
        when(paymentService.generateConnectOnboardingLink(anyString(), anyString(), anyString())).thenReturn(link);
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateStripeOnboardingLinkRequest req = mock(GenerateStripeOnboardingLinkRequest.class);
        when(req.getReturnUrl()).thenReturn("https://app/return");
        when(req.getRefreshUrl()).thenReturn("https://app/refresh");

        StripeOnboardingLinkResponse resp = service.generateStripeOnboardingLink(
                MARKETPLACE_ID, VENDOR_ID, USER_ID, req);

        assertEquals("https://stripe.com/onboard", resp.getUrl());
        verify(paymentService).createConnectAccount(any(), any(), any());
    }

    @Test
    void generateStripeLink_existingAccount_skipsAccountCreation() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.DRAFT, OnboardingStep.DOCUMENTS);
        vendor.setStripeConnectAccountId("acct_existing");
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());

        PaymentService.ConnectOnboardingLinkResult link =
                new PaymentService.ConnectOnboardingLinkResult("https://stripe.com/continue", null);
        when(paymentService.generateConnectOnboardingLink(eq("acct_existing"), any(), any())).thenReturn(link);
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateStripeOnboardingLinkRequest req = mock(GenerateStripeOnboardingLinkRequest.class);
        when(req.getReturnUrl()).thenReturn("https://app/return");
        when(req.getRefreshUrl()).thenReturn("https://app/refresh");

        service.generateStripeOnboardingLink(MARKETPLACE_ID, VENDOR_ID, USER_ID, req);

        verify(paymentService, never()).createConnectAccount(any(), any(), any());
    }

    // ─── recordDocumentUpload ─────────────────────────────────────────────────

    @Test
    void recordDocumentUpload_newDocument_savesWithS3Key() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.DRAFT, OnboardingStep.DOCUMENTS);
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());
        when(documentRepository.findByMarketplaceVendorIdAndDocumentType(VENDOR_ID, VendorDocumentType.IDENTITY))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(inv -> {
            VendorOnboardingDocument d = inv.getArgument(0);
            d.setMarketplaceVendor(vendor);
            d.setDocumentType(VendorDocumentType.IDENTITY);
            return d;
        });

        service.recordDocumentUpload(MARKETPLACE_ID, VENDOR_ID, USER_ID, VendorDocumentType.IDENTITY, "s3://bucket/doc.pdf");

        verify(documentRepository).save(any(VendorOnboardingDocument.class));
    }

    @Test
    void recordDocumentUpload_existingDocument_updatesAndClearsVerification() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.DRAFT, OnboardingStep.DOCUMENTS);
        VendorOnboardingDocument existing = new VendorOnboardingDocument();
        existing.setMarketplaceVendor(vendor);
        existing.setDocumentType(VendorDocumentType.BUSINESS_LICENSE);
        existing.setS3Key("s3://old-key");
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());
        when(documentRepository.findByMarketplaceVendorIdAndDocumentType(VENDOR_ID, VendorDocumentType.BUSINESS_LICENSE))
                .thenReturn(Optional.of(existing));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordDocumentUpload(MARKETPLACE_ID, VENDOR_ID, USER_ID,
                VendorDocumentType.BUSINESS_LICENSE, "s3://new-key");

        assertNull(existing.getVerifiedAt());
        assertNull(existing.getRejectionNote());
        assertEquals("s3://new-key", existing.getS3Key());
    }

    // ─── submitForReview ──────────────────────────────────────────────────────

    @Test
    void submitForReview_draftVendor_setsApplied() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.DRAFT, OnboardingStep.DOCUMENTS);
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceVendorResponse resp = service.submitForReview(MARKETPLACE_ID, VENDOR_ID, USER_ID);

        assertEquals("APPLIED", resp.getStatus());
        assertEquals("REVIEW", resp.getOnboardingStep());
    }

    @Test
    void submitForReview_needsInfoVendor_setsApplied() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.NEEDS_INFO, OnboardingStep.DOCUMENTS);
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceVendorResponse resp = service.submitForReview(MARKETPLACE_ID, VENDOR_ID, USER_ID);

        assertEquals("APPLIED", resp.getStatus());
    }

    @Test
    void submitForReview_approvedVendor_throwsBadRequest() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPROVED, OnboardingStep.COMPLETE);
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(companyAccessService.require(any(), any(), any())).thenReturn(vendor.getVendorCompany());

        assertThrows(BadRequestException.class,
                () -> service.submitForReview(MARKETPLACE_ID, VENDOR_ID, USER_ID));
    }

    // ─── approveVendor ────────────────────────────────────────────────────────

    @Test
    void approveVendor_setsApprovedStatusAndCompleteStep() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPLIED, OnboardingStep.REVIEW);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(makeMarketplaceProfile(true)));
        when(companyAccessService.require(any(), any(), any())).thenReturn(makeCompany(MARKETPLACE_ID, "MP"));
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorActionRequest req = mock(VendorActionRequest.class);
        when(req.getTier()).thenReturn(null);
        when(req.getReason()).thenReturn(null);

        MarketplaceVendorResponse resp = service.approveVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID, req);

        assertEquals("APPROVED", resp.getStatus());
        assertEquals("COMPLETE", resp.getOnboardingStep());
        assertNotNull(resp.getApprovedAt());
    }

    @Test
    void approveVendor_withTierOverride_setsVendorTier() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPLIED, OnboardingStep.REVIEW);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(makeMarketplaceProfile(true)));
        when(companyAccessService.require(any(), any(), any())).thenReturn(makeCompany(MARKETPLACE_ID, "MP"));
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorActionRequest req = mock(VendorActionRequest.class);
        when(req.getTier()).thenReturn(VendorTier.PREMIUM);
        when(req.getReason()).thenReturn(null);

        MarketplaceVendorResponse resp = service.approveVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID, req);

        assertEquals("PREMIUM", resp.getTier());
    }

    // ─── rejectVendor ────────────────────────────────────────────────────────

    @Test
    void rejectVendor_setsRejectedStatusWithReason() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPLIED, OnboardingStep.REVIEW);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(makeMarketplaceProfile(true)));
        when(companyAccessService.require(any(), any(), any())).thenReturn(makeCompany(MARKETPLACE_ID, "MP"));
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorActionRequest req = mock(VendorActionRequest.class);
        when(req.getReason()).thenReturn("Incomplete documents");

        MarketplaceVendorResponse resp = service.rejectVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID, req);

        assertEquals("REJECTED", resp.getStatus());
        assertEquals("Incomplete documents", resp.getRejectionReason());
    }

    @Test
    void rejectVendor_blankReason_throwsBadRequest() {
        VendorActionRequest req = mock(VendorActionRequest.class);
        when(req.getReason()).thenReturn("");

        assertThrows(BadRequestException.class,
                () -> service.rejectVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID, req));
    }

    // ─── suspendVendor ────────────────────────────────────────────────────────

    @Test
    void suspendVendor_setsSuspendedStatusAndTimestamp() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPROVED, OnboardingStep.COMPLETE);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(makeMarketplaceProfile(true)));
        when(companyAccessService.require(any(), any(), any())).thenReturn(makeCompany(MARKETPLACE_ID, "MP"));
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorActionRequest req = mock(VendorActionRequest.class);
        when(req.getReason()).thenReturn("Policy violation");

        MarketplaceVendorResponse resp = service.suspendVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID, req);

        assertEquals("SUSPENDED", resp.getStatus());
        assertNotNull(resp.getSuspendedAt());
    }

    @Test
    void suspendVendor_blankReason_throwsBadRequest() {
        VendorActionRequest req = mock(VendorActionRequest.class);
        when(req.getReason()).thenReturn("  ");

        assertThrows(BadRequestException.class,
                () -> service.suspendVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID, req));
    }

    // ─── reinstateVendor ─────────────────────────────────────────────────────

    @Test
    void reinstateVendor_suspendedVendor_setsApprovedAndClearsSuspendedAt() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.SUSPENDED, OnboardingStep.COMPLETE);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(makeMarketplaceProfile(true)));
        when(companyAccessService.require(any(), any(), any())).thenReturn(makeCompany(MARKETPLACE_ID, "MP"));
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceVendorResponse resp = service.reinstateVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID);

        assertEquals("APPROVED", resp.getStatus());
        assertNull(resp.getSuspendedAt());
    }

    @Test
    void reinstateVendor_notSuspended_throwsBadRequest() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPROVED, OnboardingStep.COMPLETE);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(makeMarketplaceProfile(true)));
        when(companyAccessService.require(any(), any(), any())).thenReturn(makeCompany(MARKETPLACE_ID, "MP"));
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));

        assertThrows(BadRequestException.class,
                () -> service.reinstateVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID));
    }

    // ─── requestMoreInfo ──────────────────────────────────────────────────────

    @Test
    void requestMoreInfo_setsNeedsInfoAndDocumentsStep() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPLIED, OnboardingStep.REVIEW);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(makeMarketplaceProfile(true)));
        when(companyAccessService.require(any(), any(), any())).thenReturn(makeCompany(MARKETPLACE_ID, "MP"));
        when(marketplaceVendorRepository.findByIdAndMarketplaceId(VENDOR_ID, MARKETPLACE_ID))
                .thenReturn(Optional.of(vendor));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorActionRequest req = mock(VendorActionRequest.class);
        when(req.getReason()).thenReturn("Need tax certificate");

        MarketplaceVendorResponse resp = service.requestMoreInfo(MARKETPLACE_ID, VENDOR_ID, USER_ID, req);

        assertEquals("NEEDS_INFO", resp.getStatus());
        assertEquals("DOCUMENTS", resp.getOnboardingStep());
    }

    @Test
    void requestMoreInfo_blankNote_throwsBadRequest() {
        VendorActionRequest req = mock(VendorActionRequest.class);
        when(req.getReason()).thenReturn(null);

        assertThrows(BadRequestException.class,
                () -> service.requestMoreInfo(MARKETPLACE_ID, VENDOR_ID, USER_ID, req));
    }

    // ─── syncStripeConnectStatus ──────────────────────────────────────────────

    @Test
    void syncStripeConnectStatus_bothEnabled_setsEnabledStatus() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPROVED, OnboardingStep.COMPLETE);
        when(marketplaceVendorRepository.findByStripeConnectAccountId("acct_123"))
                .thenReturn(Optional.of(vendor));
        when(paymentService.getConnectAccountStatus("acct_123"))
                .thenReturn(new PaymentService.ConnectAccountResult("acct_123", true, true, true));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceVendorResponse resp = service.syncStripeConnectStatus("acct_123");

        assertEquals(StripeConnectStatus.ENABLED.name(), resp.getStripeConnectStatus());
        assertTrue(resp.isChargesEnabled());
        assertTrue(resp.isPayoutsEnabled());
    }

    @Test
    void syncStripeConnectStatus_detailsSubmittedNotEnabled_setsRestricted() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.APPROVED, OnboardingStep.COMPLETE);
        when(marketplaceVendorRepository.findByStripeConnectAccountId("acct_123"))
                .thenReturn(Optional.of(vendor));
        when(paymentService.getConnectAccountStatus("acct_123"))
                .thenReturn(new PaymentService.ConnectAccountResult("acct_123", false, false, true));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceVendorResponse resp = service.syncStripeConnectStatus("acct_123");

        assertEquals(StripeConnectStatus.RESTRICTED.name(), resp.getStripeConnectStatus());
    }

    @Test
    void syncStripeConnectStatus_nothingSubmitted_setsPending() {
        MarketplaceVendor vendor = makeVendor(VendorStatus.DRAFT, OnboardingStep.BANKING);
        when(marketplaceVendorRepository.findByStripeConnectAccountId("acct_123"))
                .thenReturn(Optional.of(vendor));
        when(paymentService.getConnectAccountStatus("acct_123"))
                .thenReturn(new PaymentService.ConnectAccountResult("acct_123", false, false, false));
        when(marketplaceVendorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceVendorResponse resp = service.syncStripeConnectStatus("acct_123");

        assertEquals(StripeConnectStatus.PENDING.name(), resp.getStripeConnectStatus());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private MarketplaceProfile makeMarketplaceProfile(boolean accepting) {
        Company mpCompany = makeCompany(MARKETPLACE_ID, "Test Marketplace");
        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(mpCompany);
        profile.setAcceptingApplications(accepting);
        return profile;
    }

    private MarketplaceVendor makeVendor(VendorStatus status, OnboardingStep step) {
        Company marketplace = makeCompany(MARKETPLACE_ID, "Test Marketplace");
        Company vendorCompany = makeCompany(COMPANY_ID, "Vendor Co");

        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setId(VENDOR_ID);
        vendor.setMarketplace(marketplace);
        vendor.setVendorCompany(vendorCompany);
        vendor.setStatus(status);
        vendor.setTier(VendorTier.STANDARD);
        vendor.setOnboardingStep(step);
        return vendor;
    }

    private Company makeCompany(UUID id, String name) {
        Company c = new Company();
        c.setId(id);
        c.setName(name);
        return c;
    }
}
