package backend.dtos.responses.report;

import backend.models.core.Report;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import lombok.Getter;

import java.util.List;

@Getter
public class ReportResponse {

    private final String id;
    private final ReportTargetType targetType;
    private final String targetId;
    private final String reporterId;
    private final ReportReason reason;
    private final String title;
    private final String description;
    private final ReportStatus status;
    private final List<ReportScreenshotResponse> screenshots;
    private final String createdAt;
    private final String resolvedAt;
    private final String resolvedBy;

    // Enriched fields populated by the service for admin views
    private final String targetName;
    private final String reporterName;

    public ReportResponse(Report report, String targetName, String reporterName) {
        this.id = report.getId().toString();
        this.targetType = report.getTargetType();
        this.targetId = report.getTargetId().toString();
        this.reporterId = report.getReporterId().toString();
        this.reason = report.getReason();
        this.title = report.getTitle();
        this.description = report.getDescription();
        this.status = report.getStatus();
        this.screenshots = report.getScreenshots().stream()
                .map(ReportScreenshotResponse::new)
                .toList();
        this.createdAt = report.getCreatedAt() != null ? report.getCreatedAt().toString() : null;
        this.resolvedAt = report.getResolvedAt() != null ? report.getResolvedAt().toString() : null;
        this.resolvedBy = report.getResolvedBy() != null ? report.getResolvedBy().toString() : null;
        this.targetName = targetName;
        this.reporterName = reporterName;
    }
}
