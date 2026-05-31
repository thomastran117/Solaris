package backend.services.intf.company;

import java.util.UUID;
import backend.dtos.requests.company.CreateCompanyRequest;
import backend.dtos.requests.company.UpdateCompanyRequest;
import backend.dtos.responses.company.CompanyResponse;
import backend.dtos.responses.company.PublicCompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.models.enums.CompanyStatus;

import java.util.List;

public interface CompanyService {
    PagedResponse<CompanyResponse> searchCompanies(String q, String industry, String country, CompanyStatus status, int page, int size, String sort, String direction);

    /**
     * Public-facing variant of {@link #searchCompanies}: same filtering, but the
     * paged result maps to {@link PublicCompanyResponse} so PII never leaks to
     * anonymous callers of {@code GET /companies}.
     */
    PagedResponse<PublicCompanyResponse> searchPublicCompanies(String q, String industry, String country, CompanyStatus status, int page, int size, String sort, String direction);

    CompanyResponse getCompany(UUID companyId, UUID ownerId);

    /**
     * Public, anonymous-safe single-company read. Returns 404 if the company is not
     * {@code ACTIVE} (rather than 403) so non-active companies do not leak existence.
     */
    PublicCompanyResponse getPublicCompany(UUID companyId);
    List<CompanyResponse> getCompaniesByIds(List<UUID> ids, UUID ownerId);
    CompanyResponse createCompany(UUID ownerId, CreateCompanyRequest request);
    CompanyResponse updateCompany(UUID companyId, UUID ownerId, UpdateCompanyRequest request);
    void deleteCompany(UUID companyId, UUID ownerId);
    PresignUploadResponse generateLogoUploadUrl(UUID companyId, UUID ownerId, String contentType);
    CompanyResponse getMyCompany(UUID ownerId);
}
