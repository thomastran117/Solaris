package backend.dtos.requests.qa;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AskQuestionRequest {

    @NotBlank(message = "Question text is required")
    @SafeText
    @Size(max = 500, message = "Question must not exceed 500 characters")
    private String questionText;
}
