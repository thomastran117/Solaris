package backend.services.intf.products;

import backend.dtos.requests.product.CreateKitRequest;
import backend.dtos.requests.product.ImportFromCollectionRequest;
import backend.dtos.requests.product.UpdateKitRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.KitResponse;
import backend.models.enums.ProductStatus;

import java.util.UUID;

public interface KitService {

    // Owner-authenticated CRUD
    PagedResponse<KitResponse> listKits(UUID companyId, UUID ownerId, ProductStatus status, int page, int size);
    KitResponse createKit(UUID companyId, UUID ownerId, CreateKitRequest request);
    KitResponse getKit(UUID companyId, UUID kitId, UUID ownerId);
    KitResponse updateKit(UUID companyId, UUID kitId, UUID ownerId, UpdateKitRequest request);
    void deleteKit(UUID companyId, UUID kitId, UUID ownerId);
    KitResponse importChoicesFromCollection(UUID companyId, UUID kitId, UUID ownerId, ImportFromCollectionRequest request);

    // Public read (no ownership check)
    PagedResponse<KitResponse> listKits(UUID companyId, ProductStatus status, int page, int size);
    KitResponse getKit(UUID companyId, UUID kitId);
}
