package backend.dtos.responses.company;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FollowStatusResponse {
    private boolean following;
    private boolean notificationsEnabled;
    private long followerCount;
}
