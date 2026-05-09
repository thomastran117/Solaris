package backend.services.intf.savedlist;

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

    List<SavedListSummaryResponse> listSavedLists(long userId, SavedListType typeFilter);

    SavedListResponse getSavedList(long userId, long listId);

    PublicSavedListResponse getPublicSavedList(String shareSlug);

    SavedListResponse createSavedList(long userId, CreateSavedListRequest request);

    SavedListResponse updateSavedList(long userId, long listId, UpdateSavedListRequest request);

    void deleteSavedList(long userId, long listId);

    SavedListItemResponse addItem(long userId, long listId, AddSavedListItemRequest request);

    SavedListItemResponse updateItem(long userId, long listId, long itemId, UpdateSavedListItemRequest request);

    void removeItem(long userId, long listId, long itemId);
}
