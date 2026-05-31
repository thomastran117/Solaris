package backend.services.intf.vendors;

import java.util.UUID;
import backend.dtos.requests.vendor.*;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.vendor.MarketplaceVendorResponse;
import backend.dtos.responses.vendor.StripeOnboardingLinkResponse;
import backend.dtos.responses.vendor.VendorDocumentResponse;
import backend.models.enums.VendorDocumentType;
import backend.models.enums.VendorStatus;

import java.util.List;

public interface VendorOnboardingService {

    /** Vendor applies to join a marketplace. Creates a MarketplaceVendor in DRAFT status. */
    MarketplaceVendorResponse applyToMarketplace(UUID marketplaceId, UUID requestingUserId, ApplyVendorRequest request);

    /** Vendor updates their profile (step 1 of onboarding). */
    MarketplaceVendorResponse updateProfile(UUID marketplaceId, UUID vendorId, UUID requestingUserId, UpdateVendorProfileRequest request);

    /** Vendor submits tax information (step 2 of onboarding). */
    MarketplaceVendorResponse submitTaxInfo(UUID marketplaceId, UUID vendorId, UUID requestingUserId, SubmitVendorTaxRequest request);

    /**
     * Creates or retrieves the vendor's Stripe Connect Express account and returns a
     * Stripe-hosted onboarding URL for the vendor to complete KYC / banking (step 3).
     */
    StripeOnboardingLinkResponse generateStripeOnboardingLink(
            UUID marketplaceId, UUID vendorId, UUID requestingUserId, GenerateStripeOnboardingLinkRequest request);

    /** Records a document upload (step 4). The actual file was uploaded directly to S3 by the client. */
    VendorDocumentResponse recordDocumentUpload(UUID marketplaceId, UUID vendorId, UUID requestingUserId,
                                                VendorDocumentType documentType, String s3Key);

    /** Vendor submits their application for review (moves from DRAFT/NEEDS_INFO → APPLIED). */
    MarketplaceVendorResponse submitForReview(UUID marketplaceId, UUID vendorId, UUID requestingUserId);

    // -------------------------------------------------------------------------
    // Operator actions
    // -------------------------------------------------------------------------

    MarketplaceVendorResponse approveVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId, VendorActionRequest request);

    MarketplaceVendorResponse rejectVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId, VendorActionRequest request);

    MarketplaceVendorResponse suspendVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId, VendorActionRequest request);

    MarketplaceVendorResponse reinstateVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId);

    MarketplaceVendorResponse requestMoreInfo(UUID marketplaceId, UUID vendorId, UUID operatorUserId, VendorActionRequest request);

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    MarketplaceVendorResponse getVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId);

    PagedResponse<MarketplaceVendorResponse> listVendors(UUID marketplaceId, VendorStatus status, int page, int size, UUID operatorUserId);

    /** Returns the vendor record for the authenticated user's company in the given marketplace. */
    MarketplaceVendorResponse getMyVendorRecord(UUID marketplaceId, UUID userId);

    List<VendorDocumentResponse> listDocuments(UUID marketplaceId, UUID vendorId, UUID requestingUserId);

    /** Syncs Stripe Connect account status into MarketplaceVendor (called from webhook or manually). */
    MarketplaceVendorResponse syncStripeConnectStatus(String stripeConnectAccountId);
}
