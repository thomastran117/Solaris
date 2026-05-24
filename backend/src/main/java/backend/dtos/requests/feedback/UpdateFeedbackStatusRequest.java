package backend.dtos.requests.feedback;

import backend.models.enums.FeedbackStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFeedbackStatusRequest(

        @NotNull(message = "Status is required")
        FeedbackStatus status
) {}
