package backend.dtos.requests.review;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateReviewRequest {

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @SafeText
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @SafeText
    @Size(max = 5000, message = "Review body must not exceed 5000 characters")
    private String body;
}
