package backend.dtos.requests.feedback;

import backend.annotations.safeText.SafeText;
import backend.models.enums.FeedbackCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitFeedbackRequest(

        @NotNull(message = "Category is required")
        FeedbackCategory category,

        @NotBlank(message = "Message is required")
        @SafeText
        @Size(min = 10, max = 5000, message = "Message must be between 10 and 5000 characters")
        String message,

        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @Size(max = 500, message = "Page context must not exceed 500 characters")
        String pageContext
) {}
