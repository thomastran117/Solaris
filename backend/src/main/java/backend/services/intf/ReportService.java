package backend.services.intf;

import backend.dtos.requests.report.CreateReportRequest;
import backend.dtos.requests.report.ResolveReportRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.report.ReportResponse;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;

import java.util.UUID;

public interface ReportService {

    ReportResponse create(UUID reporterId, CreateReportRequest request);

    PagedResponse<ReportResponse> listReports(ReportTargetType targetType, ReportStatus status, int page, int size);

    ReportResponse getReport(UUID reportId);

    void resolve(UUID reportId, UUID moderatorId, ResolveReportRequest request);
}
