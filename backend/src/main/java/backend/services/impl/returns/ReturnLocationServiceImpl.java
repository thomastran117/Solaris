package backend.services.impl.returns;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.return_.CreateReturnLocationRequest;
import backend.dtos.requests.return_.UpdateReturnLocationRequest;
import backend.dtos.responses.return_.ReturnLocationResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyReturnLocation;
import backend.models.enums.CompanyCapability;
import backend.repositories.CompanyReturnLocationRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.returns.ReturnLocationService;

import java.util.List;

@Service
public class ReturnLocationServiceImpl implements ReturnLocationService {

    private final CompanyReturnLocationRepository locationRepository;
    private final CompanyAccessService companyAccessService;

    public ReturnLocationServiceImpl(
            CompanyReturnLocationRepository locationRepository,
            CompanyAccessService companyAccessService) {
        this.locationRepository = locationRepository;
        this.companyAccessService = companyAccessService;
    }

    @Override
    @Transactional
    public ReturnLocationResponse createReturnLocation(UUID companyId, UUID ownerId, CreateReturnLocationRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        if (request.primary()) {
            locationRepository.clearPrimaryForCompany(companyId);
        }

        CompanyReturnLocation loc = new CompanyReturnLocation();
        loc.setCompany(company);
        loc.setName(request.name());
        loc.setAddress(request.address());
        loc.setCity(request.city());
        loc.setCountry(request.country());
        loc.setPostalCode(request.postalCode());
        loc.setPrimary(request.primary());

        return toResponse(locationRepository.save(loc));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReturnLocationResponse> getReturnLocations(UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);
        return locationRepository.findAllByCompanyId(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReturnLocationResponse updateReturnLocation(UUID locationId, UUID companyId, UUID ownerId, UpdateReturnLocationRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        CompanyReturnLocation loc = locationRepository.findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Return location not found with id: " + locationId));

        if (request.address() != null) loc.setAddress(request.address());
        if (request.city() != null)    loc.setCity(request.city());
        if (request.country() != null) loc.setCountry(request.country());
        if (request.postalCode() != null) loc.setPostalCode(request.postalCode());
        if (request.name() != null)    loc.setName(request.name());

        if (Boolean.TRUE.equals(request.primary()) && !loc.isPrimary()) {
            locationRepository.clearPrimaryForCompany(companyId);
            loc.setPrimary(true);
        } else if (Boolean.FALSE.equals(request.primary())) {
            loc.setPrimary(false);
        }

        return toResponse(locationRepository.save(loc));
    }

    @Override
    @Transactional
    public void deleteReturnLocation(UUID locationId, UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        CompanyReturnLocation loc = locationRepository.findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Return location not found with id: " + locationId));

        if (locationRepository.countByCompanyId(companyId) <= 1) {
            throw new ConflictException("Cannot delete the last return location — at least one must remain configured");
        }

        locationRepository.delete(loc);
    }

    private ReturnLocationResponse toResponse(CompanyReturnLocation loc) {
        return new ReturnLocationResponse(
                loc.getId(),
                loc.getCompany().getId(),
                loc.getName(),
                loc.getAddress(),
                loc.getCity(),
                loc.getCountry(),
                loc.getPostalCode(),
                loc.isPrimary(),
                loc.getCreatedAt(),
                loc.getUpdatedAt()
        );
    }
}
