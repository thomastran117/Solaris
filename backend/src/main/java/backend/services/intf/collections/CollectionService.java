package backend.services.intf.collections;

import java.util.UUID;
import backend.dtos.requests.collection.AddCollectionProductRequest;
import backend.dtos.requests.collection.CreateCollectionRequest;
import backend.dtos.requests.collection.UpdateCollectionProductRequest;
import backend.dtos.requests.collection.UpdateCollectionRequest;
import backend.dtos.responses.collection.CollectionProductResponse;
import backend.dtos.responses.collection.CollectionResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;

import java.util.List;

public interface CollectionService {

    PagedResponse<CollectionResponse> listCollections(
            UUID companyId,
            CollectionType type,
            CollectionStatus status,
            Boolean featured,
            int page,
            int size);

    CollectionResponse getCollection(UUID companyId, UUID collectionId);

    CollectionResponse createCollection(UUID companyId, UUID ownerId, CreateCollectionRequest request);

    CollectionResponse updateCollection(UUID companyId, UUID collectionId, UUID ownerId,
                                        UpdateCollectionRequest request);

    void deleteCollection(UUID companyId, UUID collectionId, UUID ownerId);

    PagedResponse<CollectionProductResponse> listCollectionProducts(UUID companyId, UUID collectionId,
                                                                    int page, int size);

    CollectionProductResponse addCollectionProduct(UUID companyId, UUID collectionId, UUID ownerId,
                                                   AddCollectionProductRequest request);

    CollectionProductResponse updateCollectionProduct(UUID companyId, UUID collectionId, UUID productId,
                                                      UUID ownerId, UpdateCollectionProductRequest request);

    void removeCollectionProduct(UUID companyId, UUID collectionId, UUID productId, UUID ownerId);

    /** Force a re-materialisation of a DYNAMIC collection. No-op for STATIC. */
    CollectionResponse refreshCollection(UUID companyId, UUID collectionId, UUID ownerId);

    // -------------------------------------------------------------------------
    // Marketplace-facing reads
    // -------------------------------------------------------------------------

    List<CollectionResponse> listFeaturedForMarketplace(UUID marketplaceId);

    List<CollectionResponse> listFeaturedForVendor(UUID marketplaceId, UUID vendorId);

    CollectionResponse getCollectionBySlug(UUID marketplaceId, String slug);

    PagedResponse<CollectionProductResponse> listMarketplaceCollectionProducts(
            UUID marketplaceId, String slug, int page, int size);
}
