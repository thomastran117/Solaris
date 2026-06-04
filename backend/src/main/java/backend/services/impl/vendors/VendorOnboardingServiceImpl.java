package backend.services.impl.vendors;

import java.util.UUID;
import backend.dtos.requests.vendor.*;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.vendor.MarketplaceVendorResponse;
import backend.dtos.responses.vendor.StripeOnboardingLinkResponse;
import backend.dtos.responses.vendor.VendorDocumentResponse;
import org.springframework.dao.DataIntegrityViolationException;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.*;
import backend.models.enums.*;
import backend.repositories.*;
import backend.services.intf.CacheService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.vendors.VendorOnboardingService;
import backend.models.enums.CompanyCapability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class VendorOnboardingServiceImpl implements VendorOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(VendorOnboardingServiceImpl.class);

    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final MarketplaceVendorRepository marketplaceVendorRepository;
    private final VendorOnboardingDocumentRepository documentRepository;
    private final VendorAuditLogRepository auditLogRepository;
    private final CompanyAccessService companyAccessService;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final CacheService cacheService;

    /** Max document uploads per vendor per hour to prevent S3 exhaustion. */
    private static final int  DOC_UPLOAD_RATE_LIMIT   = 10;
    private static final long DOC_UPLOAD_WINDOW_S     = 3600L;

    public VendorOnboardingServiceImpl(
            MarketplaceProfileRepository marketplaceProfileRepository,
            MarketplaceVendorRepository marketplaceVendorRepository,
            VendorOnboardingDocumentRepository documentRepository,
            VendorAuditLogRepository auditLogRepository,
            CompanyAccessService companyAccessService,
            UserRepository userRepository,
            PaymentService paymentService,
            CacheService cacheService) {
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.companyAccessService = companyAccessService;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.cacheService = cacheService;
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse applyToMarketplace(UUID marketplaceId, UUID requestingUserId, ApplyVendorRequest request) {
        MarketplaceProfile marketplace = resolveMarketplace(marketplaceId);

        if (!marketplace.isAcceptingApplications()) {
            throw new BadRequestException("This marketplace is not currently accepting vendor applications");
        }

        Company vendorCompany = companyAccessService.require(request.getVendorCompanyId(), requestingUserId, CompanyCapability.MANAGE_COMPANY);

        if (marketplaceVendorRepository.existsByMarketplaceIdAndVendorCompanyId(marketplaceId, vendorCompany.getId())) {
            throw new ConflictException("This company already has an application for this marketplace");
        }

        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setMarketplace(marketplace.getCompany());
        vendor.setVendorCompany(vendorCompany);
        vendor.setStatus(VendorStatus.DRAFT);
        vendor.setTier(VendorTier.STANDARD);
        vendor.setOnboardingStep(OnboardingStep.PROFILE);
        vendor.setAppliedAt(Instant.now());

        MarketplaceVendor saved;
        try {
            saved = marketplaceVendorRepository.save(vendor);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This company already has an application for this marketplace");
        }
        audit(saved, requestingUserId, VendorAuditAction.APPLIED, null);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse updateProfile(UUID marketplaceId, UUID vendorId, UUID requestingUserId,
                                                    UpdateVendorProfileRequest request) {
        MarketplaceVendor vendor = resolveVendorAsOwner(marketplaceId, vendorId, requestingUserId);
        requireOnboardingNotComplete(vendor);

        if (vendor.getOnboardingStep() == OnboardingStep.PROFILE) {
            vendor.setOnboardingStep(OnboardingStep.TAX);
        }

        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, requestingUserId, VendorAuditAction.PROFILE_UPDATED, null);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse submitTaxInfo(UUID marketplaceId, UUID vendorId, UUID requestingUserId,
                                                    SubmitVendorTaxRequest request) {
        MarketplaceVendor vendor = resolveVendorAsOwner(marketplaceId, vendorId, requestingUserId);
        requireOnboardingNotComplete(vendor);

        if (vendor.getOnboardingStep() == OnboardingStep.TAX) {
            vendor.setOnboardingStep(OnboardingStep.BANKING);
        }

        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, requestingUserId, VendorAuditAction.TAX_SUBMITTED, null);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public StripeOnboardingLinkResponse generateStripeOnboardingLink(
            UUID marketplaceId, UUID vendorId, UUID requestingUserId, GenerateStripeOnboardingLinkRequest request) {
        MarketplaceVendor vendor = resolveVendorAsOwner(marketplaceId, vendorId, requestingUserId);
        requireOnboardingNotComplete(vendor);

        String connectAccountId = vendor.getStripeConnectAccountId();

        if (connectAccountId == null || connectAccountId.isBlank()) {
            Company vendorCompany = vendor.getVendorCompany();
            Map<String, String> meta = Map.of(
                    "marketplaceId", String.valueOf(marketplaceId),
                    "vendorId",      String.valueOf(vendorId)
            );
            PaymentService.ConnectAccountResult account = paymentService.createConnectAccount(
                    vendorCompany.getEmail() != null ? vendorCompany.getEmail() : "",
                    vendorCompany.getName(),
                    meta
            );
            connectAccountId = account.accountId();
            vendor.setStripeConnectAccountId(connectAccountId);
            vendor.setStripeConnectStatus(StripeConnectStatus.PENDING);
            vendor.setChargesEnabled(account.chargesEnabled());
            vendor.setPayoutsEnabled(account.payoutsEnabled());
            marketplaceVendorRepository.save(vendor);
            audit(vendor, requestingUserId, VendorAuditAction.STRIPE_ACCOUNT_CREATED,
                    jsonEntry("stripeAccountId", connectAccountId));
        }

        if (vendor.getOnboardingStep() == OnboardingStep.BANKING) {
            vendor.setOnboardingStep(OnboardingStep.DOCUMENTS);
            marketplaceVendorRepository.save(vendor);
        }

        PaymentService.ConnectOnboardingLinkResult link = paymentService.generateConnectOnboardingLink(
                connectAccountId, request.getReturnUrl(), request.getRefreshUrl());
        return new StripeOnboardingLinkResponse(link.url(), connectAccountId);
    }

    @Override
    @Transactional
    public VendorDocumentResponse recordDocumentUpload(UUID marketplaceId, UUID vendorId, UUID requestingUserId,
                                                        VendorDocumentType documentType, String s3Key) {
        MarketplaceVendor vendor = resolveVendorAsOwner(marketplaceId, vendorId, requestingUserId);
        requireOnboardingNotComplete(vendor);

        // Rate-limit document uploads per vendor per hour to prevent S3 exhaustion.
        String countKey = "vendor:doc:uploads:" + vendorId;
        try {
            String raw = cacheService.get(countKey);
            int count = raw != null ? Integer.parseInt(raw) : 0;
            if (count >= DOC_UPLOAD_RATE_LIMIT) {
                throw new backend.exceptions.http.TooManyRequestException(
                        "Document upload limit (" + DOC_UPLOAD_RATE_LIMIT + "/hour) reached. Try again later.");
            }
            cacheService.set(countKey, String.valueOf(count + 1), DOC_UPLOAD_WINDOW_S);
        } catch (backend.exceptions.http.TooManyRequestException e) {
            throw e;
        } catch (org.springframework.dao.DataAccessException e) {
            // Redis is unavailable — fail closed to prevent S3 exhaustion.
            log.warn("[Vendor] Rate-limit check unavailable for vendor {} (Redis down), rejecting upload to protect S3", vendorId);
            throw new backend.exceptions.http.ServiceUnavaliableException("Document upload service temporarily unavailable. Please try again shortly.");
        } catch (Exception e) {
            log.warn("[Vendor] Rate-limit check failed for vendor {}: {}", vendorId, e.getMessage());
        }

        VendorOnboardingDocument doc = documentRepository
                .findByMarketplaceVendorIdAndDocumentType(vendorId, documentType)
                .orElseGet(VendorOnboardingDocument::new);

        doc.setMarketplaceVendor(vendor);
        doc.setDocumentType(documentType);
        doc.setS3Key(s3Key);
        doc.setVerifiedAt(null);
        doc.setRejectionNote(null);

        VendorOnboardingDocument saved = documentRepository.save(doc);
        audit(vendor, requestingUserId, VendorAuditAction.DOCUMENT_UPLOADED,
                jsonEntry("documentType", documentType));
        return toDocumentResponse(saved);
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse submitForReview(UUID marketplaceId, UUID vendorId, UUID requestingUserId) {
        MarketplaceVendor vendor = resolveVendorAsOwner(marketplaceId, vendorId, requestingUserId);

        if (vendor.getStatus() != VendorStatus.DRAFT && vendor.getStatus() != VendorStatus.NEEDS_INFO) {
            throw new BadRequestException("Vendor application is not in a state that allows submission");
        }

        vendor.setStatus(VendorStatus.APPLIED);
        vendor.setOnboardingStep(OnboardingStep.REVIEW);
        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, requestingUserId, VendorAuditAction.SUBMITTED_FOR_REVIEW, null);
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Operator actions
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public MarketplaceVendorResponse approveVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId,
                                                    VendorActionRequest request) {
        resolveMarketplaceAsOperator(marketplaceId, operatorUserId);
        MarketplaceVendor vendor = resolveVendor(marketplaceId, vendorId);

        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setApprovedAt(Instant.now());
        vendor.setOnboardingStep(OnboardingStep.COMPLETE);
        if (request.getTier() != null) {
            vendor.setTier(request.getTier());
        }

        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, operatorUserId, VendorAuditAction.APPROVED,
                request.getReason() != null ? jsonEntry("note", request.getReason()) : null);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse rejectVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId,
                                                   VendorActionRequest request) {
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Rejection reason is required");
        }
        resolveMarketplaceAsOperator(marketplaceId, operatorUserId);
        MarketplaceVendor vendor = resolveVendor(marketplaceId, vendorId);

        vendor.setStatus(VendorStatus.REJECTED);
        vendor.setRejectionReason(request.getReason());
        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, operatorUserId, VendorAuditAction.REJECTED,
                jsonEntry("reason", request.getReason()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse suspendVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId,
                                                    VendorActionRequest request) {
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Suspension reason is required");
        }
        resolveMarketplaceAsOperator(marketplaceId, operatorUserId);
        MarketplaceVendor vendor = resolveVendor(marketplaceId, vendorId);

        vendor.setStatus(VendorStatus.SUSPENDED);
        vendor.setSuspendedAt(Instant.now());
        vendor.setRejectionReason(request.getReason());
        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, operatorUserId, VendorAuditAction.SUSPENDED,
                jsonEntry("reason", request.getReason()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse reinstateVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId) {
        resolveMarketplaceAsOperator(marketplaceId, operatorUserId);
        MarketplaceVendor vendor = resolveVendor(marketplaceId, vendorId);

        if (vendor.getStatus() != VendorStatus.SUSPENDED) {
            throw new BadRequestException("Only suspended vendors can be reinstated");
        }

        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setSuspendedAt(null);
        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, operatorUserId, VendorAuditAction.REINSTATED, null);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse requestMoreInfo(UUID marketplaceId, UUID vendorId, UUID operatorUserId,
                                                      VendorActionRequest request) {
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("A note explaining what information is needed is required");
        }
        resolveMarketplaceAsOperator(marketplaceId, operatorUserId);
        MarketplaceVendor vendor = resolveVendor(marketplaceId, vendorId);

        vendor.setStatus(VendorStatus.NEEDS_INFO);
        vendor.setOnboardingStep(OnboardingStep.DOCUMENTS);
        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, operatorUserId, VendorAuditAction.NEEDS_INFO_REQUESTED,
                jsonEntry("note", request.getReason()));
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public MarketplaceVendorResponse getVendor(UUID marketplaceId, UUID vendorId, UUID operatorUserId) {
        resolveMarketplaceAsOperator(marketplaceId, operatorUserId);
        return toResponse(resolveVendor(marketplaceId, vendorId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<MarketplaceVendorResponse> listVendors(UUID marketplaceId, VendorStatus status, int page, int size, UUID operatorUserId) {
        resolveMarketplaceAsOperator(marketplaceId, operatorUserId);
        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MarketplaceVendor> vendors = status != null
                ? marketplaceVendorRepository.findByMarketplaceIdAndStatus(marketplaceId, status, pageable)
                : marketplaceVendorRepository.findByMarketplaceId(marketplaceId, pageable);
        return new PagedResponse<>(vendors.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public MarketplaceVendorResponse getMyVendorRecord(UUID marketplaceId, UUID userId) {
        List<Company> userCompanies = companyAccessService.listAccessibleCompanies(userId);
        return userCompanies.stream()
                .flatMap(c -> marketplaceVendorRepository
                        .findByMarketplaceIdAndVendorCompanyId(marketplaceId, c.getId()).stream())
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No vendor record found for your company in this marketplace"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorDocumentResponse> listDocuments(UUID marketplaceId, UUID vendorId, UUID requestingUserId) {
        resolveVendorAsOwner(marketplaceId, vendorId, requestingUserId);
        return documentRepository.findAllByMarketplaceVendorId(vendorId)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Override
    @Transactional
    public MarketplaceVendorResponse syncStripeConnectStatus(String stripeConnectAccountId) {
        MarketplaceVendor vendor = marketplaceVendorRepository.findByStripeConnectAccountId(stripeConnectAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("No vendor found for Stripe account " + stripeConnectAccountId));

        PaymentService.ConnectAccountResult status = paymentService.getConnectAccountStatus(stripeConnectAccountId);

        vendor.setChargesEnabled(status.chargesEnabled());
        vendor.setPayoutsEnabled(status.payoutsEnabled());
        vendor.setStripeConnectStatus(
                status.chargesEnabled() && status.payoutsEnabled()
                        ? StripeConnectStatus.ENABLED
                        : status.detailsSubmitted()
                                ? StripeConnectStatus.RESTRICTED
                                : StripeConnectStatus.PENDING
        );

        MarketplaceVendor saved = marketplaceVendorRepository.save(vendor);
        audit(saved, null, VendorAuditAction.STRIPE_ACCOUNT_UPDATED,
                String.format("{\"chargesEnabled\":%b,\"payoutsEnabled\":%b}",
                        status.chargesEnabled(), status.payoutsEnabled()));
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MarketplaceProfile resolveMarketplace(UUID marketplaceId) {
        return marketplaceProfileRepository.findByCompanyId(marketplaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Marketplace not found"));
    }

    private MarketplaceVendor resolveVendor(UUID marketplaceId, UUID vendorId) {
        return marketplaceVendorRepository.findByIdAndMarketplaceId(vendorId, marketplaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found in this marketplace"));
    }

    private MarketplaceVendor resolveVendorAsOwner(UUID marketplaceId, UUID vendorId, UUID userId) {
        MarketplaceVendor vendor = resolveVendor(marketplaceId, vendorId);
        companyAccessService.require(vendor.getVendorCompany().getId(), userId, CompanyCapability.MANAGE_COMPANY);
        return vendor;
    }

    private void resolveMarketplaceAsOperator(UUID marketplaceId, UUID userId) {
        MarketplaceProfile marketplace = resolveMarketplace(marketplaceId);
        companyAccessService.require(marketplace.getCompany().getId(), userId, CompanyCapability.MANAGE_COMPANY);
    }

    private void requireOnboardingNotComplete(MarketplaceVendor vendor) {
        if (vendor.getOnboardingStep() == OnboardingStep.COMPLETE
                && vendor.getStatus() == VendorStatus.APPROVED) {
            throw new BadRequestException("Vendor onboarding is already complete");
        }
    }

    /** Builds a simple single-key JSON string with proper value escaping. */
    private static String jsonEntry(String key, Object value) {
        String safe = value == null ? "null"
                : "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return "{\"" + key + "\":" + safe + "}";
    }

    private void audit(MarketplaceVendor vendor, UUID actorUserId, VendorAuditAction action, String metadataJson) {
        try {
            VendorAuditLog entry = new VendorAuditLog();
            entry.setMarketplaceVendor(vendor);
            entry.setActorUserId(actorUserId);
            entry.setAction(action);
            entry.setMetadataJson(metadataJson);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write vendor audit log for vendorId={} action={}", vendor.getId(), action, e);
        }
    }

    private MarketplaceVendorResponse toResponse(MarketplaceVendor v) {
        return new MarketplaceVendorResponse(
                v.getId(),
                v.getMarketplace().getId(),
                v.getMarketplace().getName(),
                v.getVendorCompany().getId(),
                v.getVendorCompany().getName(),
                v.getStatus().name(),
                v.getTier().name(),
                v.getOnboardingStep().name(),
                v.getCommissionPolicyId(),
                v.getStripeConnectStatus() != null ? v.getStripeConnectStatus().name() : null,
                v.isChargesEnabled(),
                v.isPayoutsEnabled(),
                v.getAppliedAt(),
                v.getApprovedAt(),
                v.getSuspendedAt(),
                v.getRejectionReason(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }

    private VendorDocumentResponse toDocumentResponse(VendorOnboardingDocument d) {
        return new VendorDocumentResponse(
                d.getId(),
                d.getMarketplaceVendor().getId(),
                d.getDocumentType().name(),
                d.getS3Key(),
                d.getUploadedAt(),
                d.getVerifiedAt(),
                d.getRejectionNote()
        );
    }
}
