package backend.services.intf.company;

import backend.dtos.requests.company.PatchFollowRequest;
import backend.dtos.responses.company.CompanyFollowResponse;
import backend.dtos.responses.company.FollowStatusResponse;
import backend.dtos.responses.company.FollowedCompanyResponse;
import backend.dtos.responses.general.PagedResponse;

import java.util.UUID;

public interface CompanyFollowService {

    CompanyFollowResponse follow(UUID userId, UUID companyId);

    void unfollow(UUID userId, UUID companyId);

    FollowStatusResponse getStatus(UUID userId, UUID companyId);

    CompanyFollowResponse setNotifications(UUID userId, UUID companyId, boolean enabled);

    PagedResponse<FollowedCompanyResponse> listFollowing(UUID userId, int page, int size);
}
