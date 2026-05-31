package backend.dtos.requests.issue;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectIssueRequest(
        @NotBlank @SafeText @Size(max = 500) String reason
) {}
