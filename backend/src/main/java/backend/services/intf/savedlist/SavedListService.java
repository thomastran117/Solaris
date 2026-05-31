package backend.services.intf.savedlist;

import java.util.UUID;
import backend.dtos.requests.savedlist.AddSavedListItemRequest;
import backend.dtos.requests.savedlist.CreateSavedListRequest;
import backend.dtos.requests.savedlist.UpdateSavedListItemRequest;
import backend.dtos.requests.savedlist.UpdateSavedListRequest;
import backend.dtos.responses.savedlist.PublicSavedListResponse;
import backend.dtos.responses.savedlist.SavedListItemResponse;
import backend.dtos.responses.savedlist.SavedListResponse;
import backend.dtos.responses.savedlist.SavedListSummaryResponse;
import backend.models.core.SavedListType;

import java.util.List;

public interface SavedListService {

    List<SavedListSummaryResponse> listSavedLists(UUID userId, SavedListType typeFilter);

    SavedListResponse getSavedList(UUID userId, UUID listId);

    PublicSavedListResponse getPublicSavedList(String shareSlug);

    SavedListResponse createSavedList(UUID userId, CreateSavedListRequest request);

    SavedListResponse updateSavedList(UUID userId, UUID listId, UpdateSavedListRequest request);

    void deleteSavedList(UUID userId, UUID listId);

    SavedListItemResponse addItem(UUID userId, UUID listId, AddSavedListItemRequest request);

    SavedListItemResponse updateItem(UUID userId, UUID listId, UUID itemId, UpdateSavedListItemRequest request);

    void removeItem(UUID userId, UUID listId, UUID itemId);
}
