package backend.dtos.responses.company;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class FollowedCompanyResponse {
    private UUID followId;
    private UUID companyId;
    private String companyName;
    private String companyLogoUrl;
    private String industry;
    private boolean notificationsEnabled;
    private Instant followedAt;
}
