package backend.controllers.impl.imports;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.imports.AttachImagesRequest;
import backend.dtos.requests.imports.CreateImportJobRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.imports.ImportDownloadResponse;
import backend.dtos.responses.imports.ImportJobResponse;
import backend.dtos.responses.imports.ImportJobRowResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.imports.ImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/companies/{companyId}/imports")
@RequireAuth
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public ResponseEntity<ImportJobResponse> createJob(
            @PathVariable long companyId,
            @Valid @RequestBody CreateImportJobRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED).body(importService.createJob(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ImportJobResponse>> listJobs(
            @PathVariable long companyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(importService.listJobs(companyId, userId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ImportJobResponse> getJob(
            @PathVariable long companyId,
            @PathVariable long jobId) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(importService.getJob(companyId, userId, jobId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{jobId}/errors")
    public ResponseEntity<PagedResponse<ImportJobRowResponse>> listErrors(
            @PathVariable long companyId,
            @PathVariable long jobId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(importService.listErrors(companyId, userId, jobId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{jobId}/error-report")
    public ResponseEntity<ImportDownloadResponse> getErrorReport(
            @PathVariable long companyId,
            @PathVariable long jobId) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(importService.getErrorReport(companyId, userId, jobId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/export")
    public ResponseEntity<ImportDownloadResponse> exportCatalogue(@PathVariable long companyId) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(importService.exportCatalogue(companyId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/attach-images")
    public ResponseEntity<Map<String, Integer>> attachImages(
            @PathVariable long companyId,
            @Valid @RequestBody AttachImagesRequest request) {
        try {
            long userId = resolveUserId();
            int attached = importService.attachImages(companyId, userId, request);
            return ResponseEntity.ok(Map.of("attached", attached));
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
