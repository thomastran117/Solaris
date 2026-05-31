package backend.controllers.impl.company;

import java.util.UUID;
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
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
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 100) String industry,
            @RequestParam(required = false) @Size(max = 100) String country,
            @RequestParam(required = false) CompanyStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "^[a-zA-Z.]+$", message = "Invalid sort field") String sort,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "^(?i)(asc|desc)$", message = "Direction must be asc or desc") String direction) {
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
            UUID userId = resolveUserId();
            return ResponseEntity.ok(companyService.getMyCompany(userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    /**
     * Public, anonymous-safe single-company read for the storefront page at
     * {@code /c/{id}}. Returns 404 for any non-ACTIVE company so existence is not leaked.
     */
    @GetMapping("/{id}/public")
    public ResponseEntity<PublicCompanyResponse> getPublicCompany(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(companyService.getPublicCompany(id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    @RequireAuth
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
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
            UUID userId = resolveUserId();
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
            UUID userId = resolveUserId();
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
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        try {
            UUID userId = resolveUserId();
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
            @PathVariable UUID id,
            @RequestParam String contentType) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(companyService.generateLogoUploadUrl(id, userId, contentType));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Void> deleteCompany(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
            companyService.deleteCompany(id, userId);
            return ResponseEntity.noContent().build();
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
