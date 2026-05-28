package backend.dtos.requests.company;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PatchFollowRequest {

    @NotNull
    private Boolean notificationsEnabled;
}
