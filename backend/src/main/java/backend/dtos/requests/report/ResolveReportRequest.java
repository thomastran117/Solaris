package backend.dtos.requests.report;

import backend.models.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveReportRequest {

    @NotNull
    private ReportStatus resolution;
}
