package backend.dtos.requests.issue;

import backend.models.enums.OrderIssueState;
import jakarta.validation.constraints.NotNull;

public record TransitionIssueRequest(
        @NotNull OrderIssueState state
) {}
