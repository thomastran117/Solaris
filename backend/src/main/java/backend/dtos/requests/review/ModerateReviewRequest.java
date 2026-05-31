package backend.dtos.requests.review;

import backend.models.enums.ModerationAction;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModerateReviewRequest {

    @NotNull(message = "Action is required")
    private ModerationAction action;
}
