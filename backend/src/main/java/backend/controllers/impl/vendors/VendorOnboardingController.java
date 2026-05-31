package backend.controllers.impl.vendors;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.vendor.*;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.vendor.MarketplaceVendorResponse;
import backend.dtos.responses.vendor.StripeOnboardingLinkResponse;
import backend.dtos.responses.vendor.VendorDocumentResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.VendorDocumentType;
import backend.models.enums.VendorStatus;
import backend.services.intf.vendors.VendorOnboardingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/marketplaces/{marketplaceId}/vendors")
public class VendorOnboardingController {

    private final VendorOnboardingService vendorOnboardingService;

    public VendorOnboardingController(VendorOnboardingService vendorOnboardingService) {
        this.vendorOnboardingService = vendorOnboardingService;
    }

    // -------------------------------------------------------------------------
    // Vendor self-service
    // -------------------------------------------------------------------------

    @PostMapping("/apply")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> apply(
            @PathVariable UUID marketplaceId,
            @Valid @RequestBody ApplyVendorRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(vendorOnboardingService.applyToMarketplace(marketplaceId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{vendorId}/onboarding/profile")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> updateProfile(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody UpdateVendorProfileRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.updateProfile(marketplaceId, vendorId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/onboarding/tax")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> submitTax(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody SubmitVendorTaxRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.submitTaxInfo(marketplaceId, vendorId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/onboarding/stripe-link")
    @RequireAuth
    public ResponseEntity<StripeOnboardingLinkResponse> getStripeOnboardingLink(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody GenerateStripeOnboardingLinkRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(
                    vendorOnboardingService.generateStripeOnboardingLink(marketplaceId, vendorId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/onboarding/documents")
    @RequireAuth
    public ResponseEntity<VendorDocumentResponse> recordDocument(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @RequestParam VendorDocumentType documentType,
            @RequestParam String s3Key) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(vendorOnboardingService.recordDocumentUpload(
                            marketplaceId, vendorId, userId, documentType, s3Key));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{vendorId}/onboarding/documents")
    @RequireAuth
    public ResponseEntity<List<VendorDocumentResponse>> listDocuments(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.listDocuments(marketplaceId, vendorId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/submit")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> submitForReview(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.submitForReview(marketplaceId, vendorId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/me")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> getMyVendorRecord(@PathVariable UUID marketplaceId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.getMyVendorRecord(marketplaceId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // -------------------------------------------------------------------------
    // Operator actions
    // -------------------------------------------------------------------------

    @GetMapping
    @RequireAuth
    public ResponseEntity<PagedResponse<MarketplaceVendorResponse>> listVendors(
            @PathVariable UUID marketplaceId,
            @RequestParam(required = false) VendorStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(vendorOnboardingService.listVendors(marketplaceId, status, page, size, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{vendorId}")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> getVendor(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId) {
        try {
            return ResponseEntity.ok(vendorOnboardingService.getVendor(marketplaceId, vendorId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/approve")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> approve(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody VendorActionRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.approveVendor(marketplaceId, vendorId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/reject")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> reject(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody VendorActionRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.rejectVendor(marketplaceId, vendorId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/suspend")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> suspend(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody VendorActionRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.suspendVendor(marketplaceId, vendorId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/reinstate")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> reinstate(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.reinstateVendor(marketplaceId, vendorId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{vendorId}/needs-info")
    @RequireAuth
    public ResponseEntity<MarketplaceVendorResponse> requestMoreInfo(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @Valid @RequestBody VendorActionRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorOnboardingService.requestMoreInfo(marketplaceId, vendorId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
