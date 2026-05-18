package backend.services.intf.products;

import java.util.UUID;
import backend.dtos.requests.product.CreateBundleRequest;
import backend.dtos.requests.product.UpdateBundleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.BundleResponse;
import backend.models.enums.ProductStatus;

import java.util.List;

public interface BundleService {
    // Owner-authenticated CRUD
    PagedResponse<BundleResponse> listBundles(UUID companyId, UUID ownerId, ProductStatus status, int page, int size);
    BundleResponse createBundle(UUID companyId, UUID ownerId, CreateBundleRequest request);
    BundleResponse getBundle(UUID companyId, UUID bundleId, UUID ownerId);
    BundleResponse updateBundle(UUID companyId, UUID bundleId, UUID ownerId, UpdateBundleRequest request);
    void deleteBundle(UUID companyId, UUID bundleId, UUID ownerId);

    // Public read (no ownership check — for product-route discoverability)
    PagedResponse<BundleResponse> listBundles(UUID companyId, ProductStatus status, int page, int size);
    BundleResponse getBundle(UUID companyId, UUID bundleId);

    /** Returns 2–4 ACTIVE bundles for side-by-side comparison. */
    List<BundleResponse> compareBundles(UUID companyId, List<UUID> ids);
}
