package backend.dtos.requests.review;

import backend.annotations.safeText.SafeText;
import backend.models.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportReviewRequest {

    @NotNull(message = "Reason is required")
    private ReportReason reason;

    @Size(max = 500, message = "Detail must be 500 characters or fewer")
    @SafeText
    private String detail;
}
