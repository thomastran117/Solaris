package backend.dtos.requests.qa;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitAnswerRequest {

    @NotBlank(message = "Answer text is required")
    @SafeText
    @Size(max = 1000, message = "Answer must not exceed 1000 characters")
    private String answerText;
}
