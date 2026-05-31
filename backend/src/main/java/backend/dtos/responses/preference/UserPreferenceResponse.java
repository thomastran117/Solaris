package backend.dtos.responses.preference;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPreferenceResponse {
    private boolean trackingOptOut;
}
