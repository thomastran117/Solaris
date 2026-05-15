package backend.controllers.impl.company;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.company.BatchGetCompaniesRequest;
import backend.dtos.requests.company.CreateCompanyRequest;
import backend.dtos.requests.company.UpdateCompanyRequest;
import backend.dtos.responses.company.CompanyResponse;
import backend.dtos.responses.company.PublicCompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.CompanyStatus;
import backend.services.intf.company.CompanyService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Public marketplace discovery. Returns a redacted {@link PublicCompanyResponse}
     * (no owner id, no contact email/phone, no tax id, no registration number, no
     * employee count) so anonymous scraping cannot harvest PII from the company table.
     * Authenticated callers needing full detail use {@code GET /companies/{id}} or
     * {@code GET /companies/mine}, which still return the full {@link CompanyResponse}.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<PublicCompanyResponse>> getCompanies(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) CompanyStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        try {
            return ResponseEntity.ok(companyService.searchPublicCompanies(q, industry, country, status, page, size, sort, direction));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/mine")
    @RequireAuth
    public ResponseEntity<CompanyResponse> getMyCompany() {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(companyService.getMyCompany(userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    @RequireAuth
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable long id) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(companyService.getCompany(id, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/batch")
    @RequireAuth
    public ResponseEntity<List<CompanyResponse>> getCompaniesByIds(@Valid @RequestBody BatchGetCompaniesRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(companyService.getCompaniesByIds(request.getIds(), userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("")
    @RequireAuth
    public ResponseEntity<CompanyResponse> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        try {
            long userId = resolveUserId();
            CompanyResponse response = companyService.createCompany(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}")
    @RequireAuth
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable long id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(companyService.updateCompany(id, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/logo/presign")
    @RequireAuth
    public ResponseEntity<PresignUploadResponse> presignLogoUpload(
            @PathVariable long id,
            @RequestParam String contentType) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(companyService.generateLogoUploadUrl(id, userId, contentType));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Void> deleteCompany(@PathVariable long id) {
        try {
            long userId = resolveUserId();
            companyService.deleteCompany(id, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
