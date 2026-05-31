package backend.dtos.requests.issue;

import backend.annotations.safeText.SafeText;
import backend.models.enums.OrderIssueType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenIssueRequest(
        @NotNull OrderIssueType type,
        @SafeText @Size(max = 2000) String description,
        /** If true, automatically open a linked support ticket. */
        boolean openTicket
) {}
