package backend.services.intf.inventory;

import java.util.UUID;
import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.SetLocationStockRequest;
import backend.dtos.requests.inventory.UpdateLocationRequest;
import backend.dtos.responses.inventory.LocationResponse;
import backend.dtos.responses.inventory.LocationStockResponse;
import backend.models.enums.AdjustmentReason;

import backend.dtos.responses.inventory.NearbyPickupLocationResponse;

import java.util.List;

public interface LocationInventoryService {

    List<LocationResponse> getLocations(UUID companyId, UUID ownerId);

    LocationResponse getLocation(UUID companyId, UUID locationId, UUID ownerId);

    LocationResponse createLocation(UUID companyId, UUID ownerId, CreateLocationRequest request);

    LocationResponse updateLocation(UUID companyId, UUID locationId, UUID ownerId, UpdateLocationRequest request);

    void deleteLocation(UUID companyId, UUID locationId, UUID ownerId);

    List<LocationStockResponse> getLocationStock(UUID companyId, UUID locationId, UUID ownerId);

    List<LocationStockResponse> getProductLocationStocks(UUID companyId, UUID productId, UUID ownerId);

    /** variantId=null means product-level stock. */
    LocationStockResponse setLocationStock(UUID companyId, UUID locationId, UUID productId,
                                           UUID ownerId, SetLocationStockRequest request, UUID variantId);

    /** variantId=null means product-level stock. Reuses AdjustStockRequest (delta, reason, note). */
    LocationStockResponse adjustLocationStock(UUID companyId, UUID locationId, UUID productId,
                                              UUID ownerId, AdjustStockRequest request, UUID variantId);

    /**
     * Applies one leg of an inventory transfer to a location's product-level stock, upserting the
     * {@link backend.models.core.LocationStock} row when it does not yet exist (the destination of a
     * first-time transfer) and writing a linked {@link backend.models.core.InventoryAdjustment} with
     * the given reason/note. A negative result throws {@code UnprocessableEntityException} (422).
     *
     * <p>Runs in the caller's transaction (no {@code REQUIRES_NEW}) so a transfer's two legs commit
     * or roll back together.
     */
    LocationStockResponse applyTransferStock(UUID companyId, UUID locationId, UUID productId,
                                             UUID actingUserId, int delta,
                                             AdjustmentReason reason, String note);

    /** Returns STORE and HYBRID locations for a company sorted by Haversine distance. No auth required. */
    List<NearbyPickupLocationResponse> getNearbyPickupLocations(UUID companyId, double lat, double lng, int limit);
}
