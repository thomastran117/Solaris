package backend.services.intf.products;

import java.util.UUID;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.AddProductRelationshipRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.BatchDeleteProductsRequest;
import backend.dtos.requests.product.BatchUpdateProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.ReorderProductImagesRequest;
import backend.dtos.requests.product.RevertProductChangesRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateMarketplaceListingRequest;
import backend.dtos.requests.product.UpdateProductMerchandisingRequest;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.CatalogSearchResponse;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.ProductAttributeResponse;
import backend.dtos.responses.product.ProductRelationshipResponse;
import backend.dtos.responses.product.SimilarProductResponse;
import backend.models.enums.ProductRelationshipType;
import backend.dtos.responses.product.ProductHistoryEntryResponse;
import backend.dtos.responses.product.ProductImageResponse;
import backend.dtos.responses.product.ProductOptionResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.dtos.responses.product.ProductVariantResponse;
import backend.dtos.responses.product.VendorStorefrontResponse;
import backend.models.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {
    PagedResponse<ProductResponse> searchProducts(UUID companyId, String q, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice, Boolean featured, ProductStatus status, Boolean listed, String discountCategory, Boolean hasDiscount, int page, int size, String sort, String direction);
    ProductResponse getProduct(UUID companyId, UUID productId);
    List<ProductResponse> getProductsByIds(UUID companyId, List<UUID> ids);
    ProductResponse createProduct(UUID companyId, UUID ownerId, CreateProductRequest request);
    ProductResponse updateProduct(UUID companyId, UUID productId, UUID ownerId, UpdateProductRequest request);
    void deleteProduct(UUID companyId, UUID productId, UUID ownerId);
    List<ProductResponse> batchCreateProducts(UUID companyId, UUID ownerId, BatchCreateProductsRequest request);
    void batchDeleteProducts(UUID companyId, UUID ownerId, BatchDeleteProductsRequest request);
    List<ProductResponse> batchUpdateProducts(UUID companyId, UUID ownerId, BatchUpdateProductsRequest request);
    ProductResponse duplicateProduct(UUID companyId, UUID productId, UUID ownerId);

    // Images
    List<ProductImageResponse> getProductImages(UUID companyId, UUID productId);
    ProductImageResponse addProductImage(UUID companyId, UUID productId, UUID ownerId, AddProductImageRequest request);
    void deleteProductImage(UUID companyId, UUID productId, UUID imageId, UUID ownerId);
    List<ProductImageResponse> reorderProductImages(UUID companyId, UUID productId, UUID ownerId, ReorderProductImagesRequest request);

    // Options
    List<ProductOptionResponse> getProductOptions(UUID companyId, UUID productId);
    ProductOptionResponse addProductOption(UUID companyId, UUID productId, UUID ownerId, CreateProductOptionRequest request);
    ProductOptionResponse updateProductOption(UUID companyId, UUID productId, UUID optionId, UUID ownerId, UpdateProductOptionRequest request);
    void deleteProductOption(UUID companyId, UUID productId, UUID optionId, UUID ownerId);

    // Variants
    List<ProductVariantResponse> getProductVariants(UUID companyId, UUID productId);
    ProductVariantResponse getProductVariant(UUID companyId, UUID productId, UUID variantId);
    ProductVariantResponse createProductVariant(UUID companyId, UUID productId, UUID ownerId, CreateProductVariantRequest request);
    ProductVariantResponse updateProductVariant(UUID companyId, UUID productId, UUID variantId, UUID ownerId, UpdateProductVariantRequest request);
    void deleteProductVariant(UUID companyId, UUID productId, UUID variantId, UUID ownerId);

    // Attributes
    List<ProductAttributeResponse> getProductAttributes(UUID companyId, UUID productId);
    List<ProductAttributeResponse> setProductAttributes(UUID companyId, UUID productId, UUID ownerId, SetProductAttributesRequest request);

    // Relationships
    List<ProductRelationshipResponse> getProductRelationships(UUID companyId, UUID productId, ProductRelationshipType type);
    ProductRelationshipResponse addProductRelationship(UUID companyId, UUID productId, UUID ownerId, AddProductRelationshipRequest request);
    void removeProductRelationship(UUID companyId, UUID productId, UUID targetProductId, ProductRelationshipType type, UUID ownerId);
    List<SimilarProductResponse> getSimilarProducts(UUID companyId, UUID productId, int limit);

    // -------------------------------------------------------------------------
    // Marketplace catalog
    // -------------------------------------------------------------------------

    /** Cross-vendor product search for a marketplace storefront. Only returns ACTIVE + marketplaceListed=true products. */
    CatalogSearchResponse searchMarketplaceCatalog(
            UUID marketplaceId, String q, String category, String brand,
            BigDecimal minPrice, BigDecimal maxPrice, Boolean featured, UUID vendorId,
            int page, int size, String sort, String direction);

    /**
     * Single-company storefront search. Same ES query shape as
     * {@link #searchMarketplaceCatalog} but filtered by {@code companyId} only and
     * restricted to ACTIVE products — used by the public {@code /c/{id}} page.
     */
    CatalogSearchResponse searchCompanyCatalog(
            UUID companyId, String q, String category, String brand,
            BigDecimal minPrice, BigDecimal maxPrice,
            int page, int size, String sort, String direction);

    /** Returns a single marketplace product detail including vendor information. */
    MarketplaceCatalogProductResponse getMarketplaceProduct(UUID marketplaceId, UUID productId);

    /** Returns a vendor's public storefront (profile + featured products). */
    VendorStorefrontResponse getVendorStorefront(UUID marketplaceId, UUID vendorId);

    /** Vendor lists or unlists one of their products on a marketplace. */
    ProductResponse updateMarketplaceListing(UUID companyId, UUID productId, UUID ownerId, UpdateMarketplaceListingRequest request);

    /**
     * Updates the merchandising signals (boostWeight / pinnedUntil / pinnedRank) for a product.
     * Triggers a reindex so the new values reach the storefront search ranking.
     */
    ProductResponse updateProductMerchandising(UUID companyId, UUID productId, UUID ownerId,
                                                UpdateProductMerchandisingRequest request);

    /** Returns 2–4 products enriched with rating data for side-by-side comparison. */
    List<ProductResponse> compareProducts(UUID companyId, List<UUID> ids);

    // -------------------------------------------------------------------------
    // Versioning / change history
    // -------------------------------------------------------------------------

    /**
     * Unified timeline merging {@code ProductChangeLog} (catalog field edits) and
     * {@code InventoryAdjustment} (stock movements), ordered by occurrence desc.
     */
    PagedResponse<ProductHistoryEntryResponse> getProductHistory(
            UUID companyId, UUID productId, int page, int size);

    /**
     * Reverts one or more field-change log entries by id. Stock-related entries are
     * rejected (managed via the inventory adjustment flow). Generates new log rows
     * with {@code source = REVERT}.
     */
    ProductResponse revertProductChanges(
            UUID companyId, UUID productId, UUID ownerId, RevertProductChangesRequest request);
}
