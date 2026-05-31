package backend.dtos.requests.qa;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportQAContentRequest {

    @NotBlank(message = "Reason is required")
    @SafeText
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
