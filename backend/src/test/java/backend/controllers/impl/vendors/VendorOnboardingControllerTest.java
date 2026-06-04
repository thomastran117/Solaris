package backend.controllers.impl.vendors;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.requests.vendor.ApplyVendorRequest;
import backend.dtos.requests.vendor.GenerateStripeOnboardingLinkRequest;
import backend.dtos.requests.vendor.SubmitVendorTaxRequest;
import backend.dtos.requests.vendor.UpdateVendorProfileRequest;
import backend.dtos.requests.vendor.VendorActionRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.vendor.MarketplaceVendorResponse;
import backend.dtos.responses.vendor.StripeOnboardingLinkResponse;
import backend.dtos.responses.vendor.VendorDocumentResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.VendorStatus;
import backend.services.intf.vendors.VendorOnboardingService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VendorOnboardingControllerTest {

    private static final UUID USER_ID        = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(2);
    private static final UUID VENDOR_ID      = TestIds.uuid(3);

    private VendorOnboardingService vendorOnboardingService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        vendorOnboardingService = mock(VendorOnboardingService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new VendorOnboardingController(vendorOnboardingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /marketplaces/{id}/vendors/apply ────────────────────────────────

    @Test
    void apply_returns201() throws Exception {
        when(vendorOnboardingService.applyToMarketplace(eq(MARKETPLACE_ID), eq(USER_ID), any()))
                .thenReturn(makeVendorResponse("DRAFT"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/apply", MARKETPLACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplyVendorRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void apply_marketplaceNotAccepting_returns400() throws Exception {
        when(vendorOnboardingService.applyToMarketplace(any(), any(), any()))
                .thenThrow(new BadRequestException("not accepting"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/apply", MARKETPLACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apply_duplicate_returns409() throws Exception {
        when(vendorOnboardingService.applyToMarketplace(any(), any(), any()))
                .thenThrow(new ConflictException("already applied"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/apply", MARKETPLACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    // ─── PATCH /{vendorId}/onboarding/profile ─────────────────────────────────

    @Test
    void updateProfile_returns200() throws Exception {
        when(vendorOnboardingService.updateProfile(eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID), any()))
                .thenReturn(makeVendorResponse("DRAFT"));

        mockMvc.perform(patch("/marketplaces/{mId}/vendors/{vId}/onboarding/profile", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVendorProfileRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_alreadyComplete_returns400() throws Exception {
        when(vendorOnboardingService.updateProfile(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("onboarding complete"));

        mockMvc.perform(patch("/marketplaces/{mId}/vendors/{vId}/onboarding/profile", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /{vendorId}/onboarding/tax ──────────────────────────────────────

    @Test
    void submitTax_returns200() throws Exception {
        when(vendorOnboardingService.submitTaxInfo(eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID), any()))
                .thenReturn(makeVendorResponse("DRAFT"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/onboarding/tax", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitVendorTaxRequest())))
                .andExpect(status().isOk());
    }

    // ─── POST /{vendorId}/onboarding/stripe-link ──────────────────────────────

    @Test
    void getStripeLink_returns200() throws Exception {
        when(vendorOnboardingService.generateStripeOnboardingLink(
                eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID), any()))
                .thenReturn(new StripeOnboardingLinkResponse("https://stripe.com/onboard", "acct_123"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/onboarding/stripe-link", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GenerateStripeOnboardingLinkRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://stripe.com/onboard"));
    }

    // ─── POST /{vendorId}/onboarding/documents ────────────────────────────────

    @Test
    void recordDocument_returns201() throws Exception {
        when(vendorOnboardingService.recordDocumentUpload(
                eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID), any(), anyString()))
                .thenReturn(makeDocumentResponse());

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/onboarding/documents", MARKETPLACE_ID, VENDOR_ID)
                        .param("documentType", "IDENTITY")
                        .param("s3Key", "s3://bucket/doc.pdf"))
                .andExpect(status().isCreated());
    }

    // ─── GET /{vendorId}/onboarding/documents ────────────────────────────────

    @Test
    void listDocuments_returns200() throws Exception {
        when(vendorOnboardingService.listDocuments(eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID)))
                .thenReturn(List.of(makeDocumentResponse()));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}/onboarding/documents", MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value("IDENTITY"));
    }

    // ─── POST /{vendorId}/submit ───────────────────────────────────────────────

    @Test
    void submitForReview_returns200() throws Exception {
        when(vendorOnboardingService.submitForReview(eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID)))
                .thenReturn(makeVendorResponse("APPLIED"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/submit", MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void submitForReview_invalidStatus_returns400() throws Exception {
        when(vendorOnboardingService.submitForReview(any(), any(), any()))
                .thenThrow(new BadRequestException("cannot submit"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/submit", MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /me ──────────────────────────────────────────────────────────────

    @Test
    void getMyVendorRecord_returns200() throws Exception {
        when(vendorOnboardingService.getMyVendorRecord(MARKETPLACE_ID, USER_ID))
                .thenReturn(makeVendorResponse("APPROVED"));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/me", MARKETPLACE_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getMyVendorRecord_notFound_returns404() throws Exception {
        when(vendorOnboardingService.getMyVendorRecord(any(), any()))
                .thenThrow(new ResourceNotFoundException("no record"));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/me", MARKETPLACE_ID))
                .andExpect(status().isNotFound());
    }

    // ─── GET / (list vendors) ─────────────────────────────────────────────────

    @Test
    void listVendors_returns200() throws Exception {
        when(vendorOnboardingService.listVendors(eq(MARKETPLACE_ID), isNull(), eq(0), eq(20), eq(USER_ID)))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(makeVendorResponse("APPROVED")))));

        mockMvc.perform(get("/marketplaces/{mId}/vendors", MARKETPLACE_ID))
                .andExpect(status().isOk());
    }

    @Test
    void listVendors_withStatusFilter_passedToService() throws Exception {
        when(vendorOnboardingService.listVendors(eq(MARKETPLACE_ID), eq(VendorStatus.APPLIED), eq(0), eq(20), eq(USER_ID)))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of())));

        mockMvc.perform(get("/marketplaces/{mId}/vendors", MARKETPLACE_ID)
                        .param("status", "APPLIED"))
                .andExpect(status().isOk());

        verify(vendorOnboardingService).listVendors(MARKETPLACE_ID, VendorStatus.APPLIED, 0, 20, USER_ID);
    }

    // ─── GET /{vendorId} ──────────────────────────────────────────────────────

    @Test
    void getVendor_returns200() throws Exception {
        when(vendorOnboardingService.getVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID))
                .thenReturn(makeVendorResponse("APPROVED"));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}", MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk());
    }

    @Test
    void getVendor_notFound_returns404() throws Exception {
        when(vendorOnboardingService.getVendor(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/marketplaces/{mId}/vendors/{vId}", MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isNotFound());
    }

    // ─── POST /{vendorId}/approve ─────────────────────────────────────────────

    @Test
    void approve_returns200() throws Exception {
        when(vendorOnboardingService.approveVendor(eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID), any()))
                .thenReturn(makeVendorResponse("APPROVED"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/approve", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VendorActionRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_forbidden_returns403() throws Exception {
        when(vendorOnboardingService.approveVendor(any(), any(), any(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/approve", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ─── POST /{vendorId}/reject ──────────────────────────────────────────────

    @Test
    void reject_returns200() throws Exception {
        when(vendorOnboardingService.rejectVendor(eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID), any()))
                .thenReturn(makeVendorResponse("REJECTED"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/reject", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void reject_noReason_returns400() throws Exception {
        when(vendorOnboardingService.rejectVendor(any(), any(), any(), any()))
                .thenThrow(new BadRequestException("reason required"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/reject", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /{vendorId}/suspend ─────────────────────────────────────────────

    @Test
    void suspend_returns200() throws Exception {
        when(vendorOnboardingService.suspendVendor(eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID), any()))
                .thenReturn(makeVendorResponse("SUSPENDED"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/suspend", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // ─── POST /{vendorId}/reinstate ───────────────────────────────────────────

    @Test
    void reinstate_returns200() throws Exception {
        when(vendorOnboardingService.reinstateVendor(MARKETPLACE_ID, VENDOR_ID, USER_ID))
                .thenReturn(makeVendorResponse("APPROVED"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/reinstate", MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isOk());
    }

    @Test
    void reinstate_notSuspended_returns400() throws Exception {
        when(vendorOnboardingService.reinstateVendor(any(), any(), any()))
                .thenThrow(new BadRequestException("not suspended"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/reinstate", MARKETPLACE_ID, VENDOR_ID))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /{vendorId}/needs-info ──────────────────────────────────────────

    @Test
    void requestMoreInfo_returns200() throws Exception {
        when(vendorOnboardingService.requestMoreInfo(eq(MARKETPLACE_ID), eq(VENDOR_ID), eq(USER_ID), any()))
                .thenReturn(makeVendorResponse("NEEDS_INFO"));

        mockMvc.perform(post("/marketplaces/{mId}/vendors/{vId}/needs-info", MARKETPLACE_ID, VENDOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private MarketplaceVendorResponse makeVendorResponse(String status) {
        return new MarketplaceVendorResponse(
                VENDOR_ID, MARKETPLACE_ID, "Test Marketplace",
                TestIds.uuid(4), "Vendor Co",
                status, "STANDARD", "PROFILE",
                null, null, false, false,
                Instant.now(), null, null, null,
                Instant.now(), Instant.now());
    }

    private VendorDocumentResponse makeDocumentResponse() {
        return new VendorDocumentResponse(
                TestIds.uuid(10), VENDOR_ID, "IDENTITY",
                "s3://bucket/doc.pdf", Instant.now(), null, null);
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
