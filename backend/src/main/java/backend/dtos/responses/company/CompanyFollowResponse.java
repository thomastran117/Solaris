package backend.dtos.responses.company;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CompanyFollowResponse {
    private UUID id;
    private UUID companyId;
    private Instant followedAt;
    private boolean notificationsEnabled;
}
