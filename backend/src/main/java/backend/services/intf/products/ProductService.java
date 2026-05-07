package backend.services.intf.products;

import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.BatchDeleteProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.ReorderProductImagesRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateMarketplaceListingRequest;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.ProductAttributeResponse;
import backend.dtos.responses.product.ProductImageResponse;
import backend.dtos.responses.product.ProductOptionResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.dtos.responses.product.ProductVariantResponse;
import backend.dtos.responses.product.VendorStorefrontResponse;
import backend.models.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {
    PagedResponse<ProductResponse> searchProducts(long companyId, String q, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice, Boolean featured, ProductStatus status, Boolean listed, String discountCategory, Boolean hasDiscount, int page, int size, String sort, String direction);
    ProductResponse getProduct(long companyId, long productId);
    List<ProductResponse> getProductsByIds(long companyId, List<Long> ids);
    ProductResponse createProduct(long companyId, long ownerId, CreateProductRequest request);
    ProductResponse updateProduct(long companyId, long productId, long ownerId, UpdateProductRequest request);
    void deleteProduct(long companyId, long productId, long ownerId);
    List<ProductResponse> batchCreateProducts(long companyId, long ownerId, BatchCreateProductsRequest request);
    void batchDeleteProducts(long companyId, long ownerId, BatchDeleteProductsRequest request);

    // Images
    List<ProductImageResponse> getProductImages(long companyId, long productId);
    ProductImageResponse addProductImage(long companyId, long productId, long ownerId, AddProductImageRequest request);
    void deleteProductImage(long companyId, long productId, long imageId, long ownerId);
    List<ProductImageResponse> reorderProductImages(long companyId, long productId, long ownerId, ReorderProductImagesRequest request);

    // Options
    List<ProductOptionResponse> getProductOptions(long companyId, long productId);
    ProductOptionResponse addProductOption(long companyId, long productId, long ownerId, CreateProductOptionRequest request);
    ProductOptionResponse updateProductOption(long companyId, long productId, long optionId, long ownerId, UpdateProductOptionRequest request);
    void deleteProductOption(long companyId, long productId, long optionId, long ownerId);

    // Variants
    List<ProductVariantResponse> getProductVariants(long companyId, long productId);
    ProductVariantResponse getProductVariant(long companyId, long productId, long variantId);
    ProductVariantResponse createProductVariant(long companyId, long productId, long ownerId, CreateProductVariantRequest request);
    ProductVariantResponse updateProductVariant(long companyId, long productId, long variantId, long ownerId, UpdateProductVariantRequest request);
    void deleteProductVariant(long companyId, long productId, long variantId, long ownerId);

    // Attributes
    List<ProductAttributeResponse> getProductAttributes(long companyId, long productId);
    List<ProductAttributeResponse> setProductAttributes(long companyId, long productId, long ownerId, SetProductAttributesRequest request);

    // -------------------------------------------------------------------------
    // Marketplace catalog
    // -------------------------------------------------------------------------

    /** Cross-vendor product search for a marketplace storefront. Only returns ACTIVE + marketplaceListed=true products. */
    PagedResponse<MarketplaceCatalogProductResponse> searchMarketplaceCatalog(
            long marketplaceId, String q, String category, String brand,
            BigDecimal minPrice, BigDecimal maxPrice, Boolean featured, Long vendorId,
            int page, int size, String sort, String direction);

    /** Returns a single marketplace product detail including vendor information. */
    MarketplaceCatalogProductResponse getMarketplaceProduct(long marketplaceId, long productId);

    /** Returns a vendor's public storefront (profile + featured products). */
    VendorStorefrontResponse getVendorStorefront(long marketplaceId, long vendorId);

    /** Vendor lists or unlists one of their products on a marketplace. */
    ProductResponse updateMarketplaceListing(long companyId, long productId, long ownerId, UpdateMarketplaceListingRequest request);

    /** Returns 2–4 products enriched with rating data for side-by-side comparison. */
    List<ProductResponse> compareProducts(long companyId, List<Long> ids);
}
