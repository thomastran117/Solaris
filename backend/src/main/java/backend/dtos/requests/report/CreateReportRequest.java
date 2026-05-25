package backend.dtos.requests.report;

import backend.annotations.safeText.SafeText;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateReportRequest {

    @NotNull
    private ReportTargetType targetType;

    @NotNull
    private UUID targetId;

    @NotNull
    private ReportReason reason;

    @NotBlank
    @Size(min = 5, max = 150)
    @SafeText
    private String title;

    @NotBlank
    @Size(min = 10, max = 2000)
    @SafeText
    private String description;

    @Size(max = 5)
    private List<String> screenshotUrls;
}
