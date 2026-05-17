package backend.services.impl.company;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.company.CreateCompanyRequest;
import backend.dtos.requests.company.UpdateCompanyRequest;
import backend.dtos.responses.company.CompanyResponse;
import backend.dtos.responses.company.PublicCompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.User;
import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.UploadFolder;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.UserRepository;
import backend.repositories.specifications.CompanySpecification;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.company.CompanyService;
import backend.services.intf.StorageService;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final CompanyAccessService companyAccessService;

    public CompanyServiceImpl(
            CompanyRepository companyRepository,
            CompanyMembershipRepository membershipRepository,
            UserRepository userRepository,
            StorageService storageService,
            CompanyAccessService companyAccessService) {
        this.companyRepository = companyRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.companyAccessService = companyAccessService;
    }

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "createdAt", "foundedYear", "employeeCount");

    @Override
    public PagedResponse<CompanyResponse> searchCompanies(
            String q,
            String industry,
            String country,
            CompanyStatus status,
            int page,
            int size,
            String sort,
            String direction) {

        if (size > 50) size = 50;

        String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortField));

        return new PagedResponse<>(
                companyRepository
                        .findAll(CompanySpecification.withFilters(q, industry, country, status), pageable)
                        .map(this::toResponse)
        );
    }

    @Override
    public PagedResponse<PublicCompanyResponse> searchPublicCompanies(
            String q, String industry, String country, CompanyStatus status,
            int page, int size, String sort, String direction) {

        if (size > 50) size = 50;
        String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortField));

        return new PagedResponse<>(
                companyRepository
                        .findAll(CompanySpecification.withFilters(q, industry, country, status), pageable)
                        .map(this::toPublicResponse)
        );
    }

    private PublicCompanyResponse toPublicResponse(Company c) {
        return new PublicCompanyResponse(
                c.getId(),
                c.getName(),
                c.getCity(),
                c.getCountry(),
                c.getLogoUrl(),
                c.getWebsite(),
                c.getDescription(),
                c.getIndustry(),
                c.getFoundedYear());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyResponse> getCompaniesByIds(List<Long> ids, long userId) {
        return companyAccessService.listAccessibleCompanies(userId)
                .stream()
                .filter(c -> ids.contains(c.getId()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getCompany(long companyId, long userId) {
        Company company = companyAccessService.requireAnyAccess(companyId, userId);
        return toResponse(company);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicCompanyResponse getPublicCompany(long companyId) {
        Company company = companyRepository.findById(companyId)
                .filter(c -> c.getStatus() == CompanyStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        return toPublicResponse(company);
    }

    @Override
    @Transactional
    public CompanyResponse createCompany(long ownerId, CreateCompanyRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + ownerId));

        if (companyRepository.existsByNameAndOwnerId(request.getName(), ownerId)) {
            throw new ConflictException("A company with this name already exists");
        }

        Company company = new Company();
        company.setOwner(owner);
        company.setName(request.getName());
        company.setAddress(request.getAddress());
        company.setCity(request.getCity());
        company.setCountry(request.getCountry());
        company.setPostalCode(request.getPostalCode());
        company.setPhoneNumber(request.getPhoneNumber());
        company.setLogoUrl(request.getLogoUrl());
        company.setEmail(request.getEmail());
        company.setWebsite(request.getWebsite());
        company.setDescription(request.getDescription());
        company.setIndustry(request.getIndustry());
        company.setRegistrationNumber(request.getRegistrationNumber());
        company.setTaxId(request.getTaxId());
        company.setFoundedYear(request.getFoundedYear());
        company.setEmployeeCount(request.getEmployeeCount());

        Company saved = companyRepository.save(company);

        CompanyMembership ownerMembership = new CompanyMembership();
        ownerMembership.setCompany(saved);
        ownerMembership.setUser(owner);
        ownerMembership.setRole(CompanyRole.OWNER);
        ownerMembership.setStatus(CompanyMembershipStatus.ACTIVE);
        ownerMembership.setAcceptedAt(Instant.now());
        membershipRepository.save(ownerMembership);

        companyAccessService.invalidate(saved.getId(), ownerId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CompanyResponse updateCompany(long companyId, long userId, UpdateCompanyRequest request) {
        Company company = companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);

        if (request.getName() != null && !request.getName().equals(company.getName())) {
            if (companyRepository.existsByNameAndOwnerId(request.getName(), company.getOwner().getId())) {
                throw new ConflictException("A company with this name already exists");
            }
            company.setName(request.getName());
        }

        if (request.getAddress() != null) company.setAddress(request.getAddress());
        if (request.getCity() != null) company.setCity(request.getCity());
        if (request.getCountry() != null) company.setCountry(request.getCountry());
        if (request.getPostalCode() != null) company.setPostalCode(request.getPostalCode());
        if (request.getPhoneNumber() != null) company.setPhoneNumber(request.getPhoneNumber());
        if (request.getLogoUrl() != null) company.setLogoUrl(request.getLogoUrl());
        if (request.getEmail() != null) company.setEmail(request.getEmail());
        if (request.getWebsite() != null) company.setWebsite(request.getWebsite());
        if (request.getDescription() != null) company.setDescription(request.getDescription());
        if (request.getIndustry() != null) company.setIndustry(request.getIndustry());
        if (request.getRegistrationNumber() != null) company.setRegistrationNumber(request.getRegistrationNumber());
        if (request.getTaxId() != null) company.setTaxId(request.getTaxId());
        if (request.getFoundedYear() != null) company.setFoundedYear(request.getFoundedYear());
        if (request.getEmployeeCount() != null) company.setEmployeeCount(request.getEmployeeCount());

        return toResponse(companyRepository.save(company));
    }

    @Override
    @Transactional
    public void deleteCompany(long companyId, long userId) {
        Company company = companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);
        companyRepository.delete(company);
        companyAccessService.invalidate(companyId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PresignUploadResponse generateLogoUploadUrl(long companyId, long userId, String contentType) {
        companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_COMPANY);
        return storageService.generatePresignedUrl(UploadFolder.COMPANY_LOGO, userId, contentType);
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getMyCompany(long userId) {
        return companyAccessService.listAccessibleCompanies(userId)
                .stream()
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No company found for this user"));
    }

    private CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getOwner().getId(),
                company.getName(),
                company.getAddress(),
                company.getCity(),
                company.getCountry(),
                company.getPostalCode(),
                company.getPhoneNumber(),
                company.getLogoUrl(),
                company.getEmail(),
                company.getWebsite(),
                company.getDescription(),
                company.getIndustry(),
                company.getRegistrationNumber(),
                company.getTaxId(),
                company.getFoundedYear(),
                company.getEmployeeCount(),
                company.getStatus().name(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }
}
