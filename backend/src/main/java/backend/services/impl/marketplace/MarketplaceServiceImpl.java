package backend.services.impl.marketplace;

import java.util.UUID;
import backend.dtos.requests.marketplace.CreateMarketplaceRequest;
import backend.dtos.requests.marketplace.UpdateMarketplaceRequest;
import backend.dtos.responses.marketplace.MarketplaceProfileResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.enums.CompanyCapability;
import backend.repositories.MarketplaceProfileRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.marketplace.MarketplaceService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketplaceServiceImpl implements MarketplaceService {

    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final CompanyAccessService companyAccessService;

    public MarketplaceServiceImpl(
            MarketplaceProfileRepository marketplaceProfileRepository,
            CompanyAccessService companyAccessService) {
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.companyAccessService = companyAccessService;
    }

    @Override
    @Transactional
    public MarketplaceProfileResponse createMarketplace(UUID ownerId, UUID companyId, CreateMarketplaceRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        if (marketplaceProfileRepository.existsByCompanyId(companyId)) {
            throw new ConflictException("This company already has a marketplace profile");
        }

        if (marketplaceProfileRepository.existsBySlug(request.getSlug())) {
            throw new ConflictException("Slug '" + request.getSlug() + "' is already taken");
        }

        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(company);
        profile.setSlug(request.getSlug());
        profile.setPayoutSchedule(request.getPayoutSchedule());
        profile.setHoldPeriodDays(request.getHoldPeriodDays());
        profile.setDefaultCurrency(request.getDefaultCurrency() != null ? request.getDefaultCurrency() : "USD");
        profile.setAcceptingApplications(request.isAcceptingApplications());

        return toResponse(marketplaceProfileRepository.save(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public MarketplaceProfileResponse getMarketplace(UUID marketplaceId) {
        MarketplaceProfile profile = marketplaceProfileRepository.findById(marketplaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Marketplace not found"));
        return toResponse(profile);
    }

    @Override
    @Transactional
    public MarketplaceProfileResponse updateMarketplace(UUID marketplaceId, UUID ownerId, UpdateMarketplaceRequest request) {
        MarketplaceProfile profile = marketplaceProfileRepository.findById(marketplaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Marketplace not found"));

        companyAccessService.require(profile.getCompany().getId(), ownerId, CompanyCapability.MANAGE_PRODUCTS);

        if (request.getPayoutSchedule() != null) {
            profile.setPayoutSchedule(request.getPayoutSchedule());
        }
        if (request.getHoldPeriodDays() != null) {
            profile.setHoldPeriodDays(request.getHoldPeriodDays());
        }
        if (request.getAcceptingApplications() != null) {
            profile.setAcceptingApplications(request.getAcceptingApplications());
        }

        return toResponse(marketplaceProfileRepository.save(profile));
    }

    private MarketplaceProfileResponse toResponse(MarketplaceProfile p) {
        return new MarketplaceProfileResponse(
                p.getId(),
                p.getCompany().getId(),
                p.getCompany().getName(),
                p.getSlug(),
                p.getDefaultCommissionPolicyId(),
                p.getPayoutSchedule().name(),
                p.getHoldPeriodDays(),
                p.getDefaultCurrency(),
                p.isAcceptingApplications(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
