package backend.services.intf.inventory;

import java.util.UUID;
import backend.dtos.requests.inventory.CreateRestockRequest;
import backend.dtos.requests.inventory.UpdateRestockRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.RestockRequestResponse;
import backend.models.enums.RestockStatus;

public interface RestockService {
    PagedResponse<RestockRequestResponse> listRestockRequests(UUID companyId, UUID ownerId, RestockStatus status, UUID productId, int page, int size);
    RestockRequestResponse createRestockRequest(UUID companyId, UUID ownerId, CreateRestockRequest request);
    RestockRequestResponse getRestockRequest(UUID companyId, UUID restockId, UUID ownerId);
    RestockRequestResponse updateRestockRequest(UUID companyId, UUID restockId, UUID ownerId, UpdateRestockRequest request);
    void deleteRestockRequest(UUID companyId, UUID restockId, UUID ownerId);
}
