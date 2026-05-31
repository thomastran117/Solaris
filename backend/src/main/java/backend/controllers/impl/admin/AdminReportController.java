package backend.controllers.impl.admin;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.report.ResolveReportRequest;
import backend.dtos.responses.general.MessageResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.report.ReportResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.kafka.workers.IndexVersionManager;
import backend.kafka.workers.ReportIndexingService;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import backend.services.intf.ReportSearchService;
import backend.services.intf.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/admin/reports")
@RequireAuth(roles = {"ADMIN"})
public class AdminReportController {

    private final ReportService reportService;
    private final ReportSearchService reportSearchService;
    private final ReportIndexingService reportIndexingService;
    private final IndexVersionManager indexVersionManager;

    public AdminReportController(
            ReportService reportService,
            ReportSearchService reportSearchService,
            ReportIndexingService reportIndexingService,
            IndexVersionManager indexVersionManager) {
        this.reportService = reportService;
        this.reportSearchService = reportSearchService;
        this.reportIndexingService = reportIndexingService;
        this.indexVersionManager = indexVersionManager;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ReportResponse>> listReports(
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(reportService.listReports(targetType, status, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID reportId) {
        try {
            return ResponseEntity.ok(reportService.getReport(reportId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{reportId}")
    public ResponseEntity<Void> resolve(
            @PathVariable UUID reportId,
            @Valid @RequestBody ResolveReportRequest request) {
        try {
            reportService.resolve(reportId, resolveUserId(), request);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<ReportResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant after,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(reportSearchService.search(q, targetType, status, reason, after, before, sort, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/reindex")
    public ResponseEntity<MessageResponse> reindex() {
        try {
            indexVersionManager.rolloverIndex("reports");
            reportIndexingService.reindexAll();
            return ResponseEntity.ok(new MessageResponse("Report index rollover and full reindex triggered"));
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
