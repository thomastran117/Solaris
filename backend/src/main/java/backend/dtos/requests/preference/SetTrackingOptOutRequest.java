package backend.dtos.requests.preference;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SetTrackingOptOutRequest {

    @NotNull
    private Boolean optOut;
}
