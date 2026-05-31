package backend.services.intf;

import backend.dtos.requests.company.CreateCompanyRequest;
import backend.dtos.requests.company.UpdateCompanyRequest;
import backend.dtos.responses.company.CompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.models.enums.CompanyStatus;

import java.util.List;

public interface CompanyService {
    PagedResponse<CompanyResponse> searchCompanies(String q, String industry, String country, CompanyStatus status, int page, int size, String sort, String direction);
    CompanyResponse getCompany(long companyId, long ownerId);
    List<CompanyResponse> getCompaniesByIds(List<Long> ids, long ownerId);
    CompanyResponse createCompany(long ownerId, CreateCompanyRequest request);
    CompanyResponse updateCompany(long companyId, long ownerId, UpdateCompanyRequest request);
    void deleteCompany(long companyId, long ownerId);
}
