package backend.services.intf.collections;

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
            long companyId,
            CollectionType type,
            CollectionStatus status,
            Boolean featured,
            int page,
            int size);

    CollectionResponse getCollection(long companyId, long collectionId);

    CollectionResponse createCollection(long companyId, long ownerId, CreateCollectionRequest request);

    CollectionResponse updateCollection(long companyId, long collectionId, long ownerId,
                                        UpdateCollectionRequest request);

    void deleteCollection(long companyId, long collectionId, long ownerId);

    PagedResponse<CollectionProductResponse> listCollectionProducts(long companyId, long collectionId,
                                                                    int page, int size);

    CollectionProductResponse addCollectionProduct(long companyId, long collectionId, long ownerId,
                                                   AddCollectionProductRequest request);

    CollectionProductResponse updateCollectionProduct(long companyId, long collectionId, long productId,
                                                      long ownerId, UpdateCollectionProductRequest request);

    void removeCollectionProduct(long companyId, long collectionId, long productId, long ownerId);

    /** Force a re-materialisation of a DYNAMIC collection. No-op for STATIC. */
    CollectionResponse refreshCollection(long companyId, long collectionId, long ownerId);

    // -------------------------------------------------------------------------
    // Marketplace-facing reads
    // -------------------------------------------------------------------------

    List<CollectionResponse> listFeaturedForMarketplace(long marketplaceId);

    List<CollectionResponse> listFeaturedForVendor(long marketplaceId, long vendorId);

    CollectionResponse getCollectionBySlug(long marketplaceId, String slug);

    PagedResponse<CollectionProductResponse> listMarketplaceCollectionProducts(
            long marketplaceId, String slug, int page, int size);
}
