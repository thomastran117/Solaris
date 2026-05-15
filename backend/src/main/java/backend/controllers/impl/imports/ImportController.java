package backend.controllers.impl.imports;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.imports.AttachImagesRequest;
import backend.dtos.requests.imports.CreateImportJobRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.imports.ImportDownloadResponse;
import backend.dtos.responses.imports.ImportJobResponse;
import backend.dtos.responses.imports.ImportJobRowResponse;
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

/**
 * Per-controller {@code catch(Exception) -> InternalServerErrorException()} blocks
 * have been removed deliberately: {@code GlobalExceptionHandler} already catches
 * {@code Exception} (and {@code AppHttpException} subclasses), logs the cause, and
 * returns the right status to the client. Catching here was hiding the real cause
 * type — for example, an authorization failure was being reported as a 500 instead
 * of bubbling up through its dedicated handler.
 */
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
        long userId = resolveUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(importService.createJob(companyId, userId, request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ImportJobResponse>> listJobs(
            @PathVariable long companyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        long userId = resolveUserId();
        return ResponseEntity.ok(importService.listJobs(companyId, userId, page, size));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ImportJobResponse> getJob(
            @PathVariable long companyId,
            @PathVariable long jobId) {
        long userId = resolveUserId();
        return ResponseEntity.ok(importService.getJob(companyId, userId, jobId));
    }

    @GetMapping("/{jobId}/errors")
    public ResponseEntity<PagedResponse<ImportJobRowResponse>> listErrors(
            @PathVariable long companyId,
            @PathVariable long jobId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        long userId = resolveUserId();
        return ResponseEntity.ok(importService.listErrors(companyId, userId, jobId, page, size));
    }

    @GetMapping("/{jobId}/error-report")
    public ResponseEntity<ImportDownloadResponse> getErrorReport(
            @PathVariable long companyId,
            @PathVariable long jobId) {
        long userId = resolveUserId();
        return ResponseEntity.ok(importService.getErrorReport(companyId, userId, jobId));
    }

    @PostMapping("/export")
    public ResponseEntity<ImportDownloadResponse> exportCatalogue(@PathVariable long companyId) {
        long userId = resolveUserId();
        return ResponseEntity.ok(importService.exportCatalogue(companyId, userId));
    }

    @PostMapping("/attach-images")
    public ResponseEntity<Map<String, Integer>> attachImages(
            @PathVariable long companyId,
            @Valid @RequestBody AttachImagesRequest request) {
        long userId = resolveUserId();
        int attached = importService.attachImages(companyId, userId, request);
        return ResponseEntity.ok(Map.of("attached", attached));
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
