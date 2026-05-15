package backend.services.intf.company;

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

    CompanyResponse getCompany(long companyId, long ownerId);
    List<CompanyResponse> getCompaniesByIds(List<Long> ids, long ownerId);
    CompanyResponse createCompany(long ownerId, CreateCompanyRequest request);
    CompanyResponse updateCompany(long companyId, long ownerId, UpdateCompanyRequest request);
    void deleteCompany(long companyId, long ownerId);
    PresignUploadResponse generateLogoUploadUrl(long companyId, long ownerId, String contentType);
    CompanyResponse getMyCompany(long ownerId);
}
