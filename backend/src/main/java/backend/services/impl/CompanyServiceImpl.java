package backend.services.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import backend.dtos.requests.company.CreateCompanyRequest;
import backend.dtos.requests.company.UpdateCompanyRequest;
import backend.dtos.responses.company.CompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.UserRepository;
import backend.repositories.specifications.CompanySpecification;
import backend.services.intf.CompanyService;

import java.util.List;
import java.util.Set;

@Service
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CompanyServiceImpl(CompanyRepository companyRepository, UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
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
    public List<CompanyResponse> getCompaniesByIds(List<Long> ids, long ownerId) {
        return companyRepository.findAllByIdInAndOwnerId(ids, ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CompanyResponse getCompany(long companyId, long ownerId) {
        Company company = companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + companyId));
        return toResponse(company);
    }

    @Override
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

        return toResponse(companyRepository.save(company));
    }

    @Override
    public CompanyResponse updateCompany(long companyId, long ownerId, UpdateCompanyRequest request) {
        Company company = companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + companyId));

        if (request.getName() != null && !request.getName().equals(company.getName())) {
            if (companyRepository.existsByNameAndOwnerId(request.getName(), ownerId)) {
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
    public void deleteCompany(long companyId, long ownerId) {
        Company company = companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + companyId));
        companyRepository.delete(company);
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
