package backend.services.intf;

import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.report.ReportResponse;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;

import java.time.Instant;

public interface ReportSearchService {

    PagedResponse<ReportResponse> search(
            String q,
            ReportTargetType targetType,
            ReportStatus status,
            ReportReason reason,
            Instant createdAfter,
            Instant createdBefore,
            String sort,
            int page,
            int size
    );
}
