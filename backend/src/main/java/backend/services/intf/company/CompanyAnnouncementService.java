package backend.services.intf.company;

import backend.dtos.requests.company.CreateAnnouncementRequest;
import backend.dtos.requests.company.UpdateAnnouncementRequest;
import backend.dtos.responses.company.AnnouncementResponse;
import backend.dtos.responses.general.PagedResponse;

import java.util.UUID;

public interface CompanyAnnouncementService {

    AnnouncementResponse create(UUID companyId, UUID userId, CreateAnnouncementRequest request);

    PagedResponse<AnnouncementResponse> listPublished(UUID companyId, int page, int size);

    PagedResponse<AnnouncementResponse> listAll(UUID companyId, UUID userId, int page, int size);

    AnnouncementResponse update(UUID companyId, UUID announcementId, UUID userId, UpdateAnnouncementRequest request);

    void delete(UUID companyId, UUID announcementId, UUID userId);

    PagedResponse<AnnouncementResponse> getFollowingFeed(UUID userId, int page, int size);
}
