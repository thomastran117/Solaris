package backend.dtos.requests.loyalty;

import java.util.UUID;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IssueBonusRequest {

    @NotNull
    private UUID userId;

    @NotNull @Min(1)
    private Integer points;

    @backend.annotations.safeText.SafeText
    @Size(max = 500)
    private String reason;
}
