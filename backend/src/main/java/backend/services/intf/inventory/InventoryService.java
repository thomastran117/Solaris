package backend.services.intf.inventory;

import java.util.UUID;
import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.ProductStatus;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.inventory.UpdateInventorySettingsRequest;
import backend.dtos.responses.general.CursorPagedResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.AdjustmentResponse;
import backend.dtos.responses.inventory.InventoryItemResponse;
import backend.dtos.responses.inventory.InventorySummaryResponse;
import backend.dtos.responses.inventory.ProductSalesMetricResponse;

import java.time.Instant;
import java.util.List;

public interface InventoryService {

    CursorPagedResponse<InventoryItemResponse> getInventory(
            UUID companyId, UUID ownerId,
            String stockStatus, String q,
            String category, String brand,
            ProductStatus status, Integer minStock, Integer maxStock,
            String cursor, int size);

    InventorySummaryResponse getSummary(UUID companyId, UUID ownerId);

    InventoryItemResponse getInventoryItem(UUID companyId, UUID productId, UUID ownerId);

    PagedResponse<AdjustmentResponse> getAdjustmentHistory(
            UUID companyId, UUID productId, UUID ownerId, int page, int size);

    InventoryItemResponse adjustStock(
            UUID companyId, UUID productId, UUID ownerId, AdjustStockRequest request);

    List<InventoryItemResponse> bulkAdjust(
            UUID companyId, UUID ownerId, BulkAdjustRequest request);

    InventoryItemResponse updateSettings(
            UUID companyId, UUID productId, UUID ownerId, UpdateInventorySettingsRequest request);

    List<ProductSalesMetricResponse> getTopPurchasedProducts(UUID companyId, UUID ownerId, int limit, Instant from, Instant to);

    List<ProductSalesMetricResponse> getTopRevenueProducts(UUID companyId, UUID ownerId, int limit, Instant from, Instant to);

    List<ProductSalesMetricResponse> getNeverSoldProducts(UUID companyId, UUID ownerId, int limit);

    InventoryItemResponse adjustVariantStock(
            UUID companyId, UUID productId, UUID variantId, UUID ownerId, AdjustStockRequest request);

    PagedResponse<AdjustmentResponse> getCompanyAdjustmentHistory(
            UUID companyId, UUID ownerId, AdjustmentReason reason,
            Instant from, Instant to, UUID productId, UUID userId, int page, int size);
}
