package backend.dtos.requests.loyalty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdjustPointsRequest {

    /** Positive to add points, negative to deduct. */
    @NotNull
    private Integer pointsDelta;

    @backend.annotations.safeText.SafeText
    @Size(max = 500)
    private String reason;
}
