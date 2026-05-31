package backend.dtos.requests.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachReviewMediaRequest {

    @NotBlank(message = "Media URL is required")
    @Size(max = 1024, message = "Media URL must be 1024 characters or fewer")
    private String url;
}
