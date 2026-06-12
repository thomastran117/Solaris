package backend.controllers.impl.products;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.BatchDeleteProductsRequest;
import backend.dtos.requests.product.BatchUpdateProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.ReorderProductImagesRequest;
import backend.dtos.requests.product.RevertProductChangesRequest;
import backend.dtos.requests.product.AddProductRelationshipRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.responses.product.ProductRelationshipResponse;
import backend.dtos.responses.product.SimilarProductResponse;
import backend.models.enums.ProductRelationshipType;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateMarketplaceListingRequest;
import backend.dtos.requests.product.UpdateProductMerchandisingRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.BundleResponse;
import backend.dtos.responses.product.ProductAttributeResponse;
import backend.dtos.responses.product.ProductHistoryEntryResponse;
import backend.dtos.responses.product.ProductImageResponse;
import backend.dtos.responses.product.ProductOptionResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.dtos.responses.product.ProductVariantResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.ProductStatus;
import backend.kafka.workers.ProductIndexingService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.products.BundleService;
import backend.services.intf.products.ProductService;
import backend.services.intf.SanitizationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/companies/{companyId}/products")
public class ProductController {

    private final ProductService productService;
    private final BundleService bundleService;
    private final ProductIndexingService productIndexingService;
    private final SanitizationService sanitizationService;
    private final CompanyAccessService companyAccessService;

    public ProductController(ProductService productService,
                             BundleService bundleService,
                             ProductIndexingService productIndexingService,
                             SanitizationService sanitizationService,
                             CompanyAccessService companyAccessService) {
        this.productService = productService;
        this.bundleService = bundleService;
        this.productIndexingService = productIndexingService;
        this.sanitizationService = sanitizationService;
        this.companyAccessService = companyAccessService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getProducts(
            @PathVariable UUID companyId,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Boolean listed,
            @RequestParam(required = false) @Size(max = 100) String discountCategory,
            @RequestParam(required = false) Boolean hasDiscount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "^[a-zA-Z.]+$", message = "Invalid sort field") String sort,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "^(?i)(asc|desc)$", message = "Direction must be asc or desc") String direction) {
        try {
            // Non-members can only browse ACTIVE *and* listed products; force both filters so an
            // omitted `listed` param cannot surface unlisted (hidden) active products.
            boolean member = isCompanyMember(companyId, resolveUserIdOptional());
            ProductStatus effectiveStatus = member ? status : ProductStatus.ACTIVE;
            Boolean effectiveListed = member ? listed : Boolean.TRUE;
            return ResponseEntity.ok(productService.searchProducts(companyId, q, category, brand, minPrice, maxPrice, featured, effectiveStatus, effectiveListed, discountCategory, hasDiscount, page, size, sort, direction));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID id) {
        try {
            ProductResponse product = productService.getProduct(companyId, id);
            // Non-members can only see ACTIVE *and* listed products; hide drafts/scheduled/archived
            // as well as unlisted (hidden) active products.
            if ((!"ACTIVE".equals(product.getStatus()) || !product.isListed())
                    && !isCompanyMember(companyId, resolveUserIdOptional())) {
                throw new ResourceNotFoundException("Product not found with id: " + id);
            }
            return ResponseEntity.ok(product);
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/batch-create")
    @RequireAuth
    public ResponseEntity<List<ProductResponse>> batchCreateProducts(
            @PathVariable UUID companyId,
            @Valid @RequestBody BatchCreateProductsRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED).body(productService.batchCreateProducts(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/batch-delete")
    @RequireAuth
    public ResponseEntity<Void> batchDeleteProducts(
            @PathVariable UUID companyId,
            @Valid @RequestBody BatchDeleteProductsRequest request) {
        try {
            UUID userId = resolveUserId();
            productService.batchDeleteProducts(companyId, userId, request);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/batch-update")
    @RequireAuth
    public ResponseEntity<List<ProductResponse>> batchUpdateProducts(
            @PathVariable UUID companyId,
            @Valid @RequestBody BatchUpdateProductsRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.batchUpdateProducts(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{productId}/duplicate")
    @RequireAuth
    public ResponseEntity<ProductResponse> duplicateProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED).body(productService.duplicateProduct(companyId, productId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/batch")
    @RequireAuth
    public ResponseEntity<List<ProductResponse>> getProductsByIds(
            @PathVariable UUID companyId,
            @RequestBody @jakarta.validation.constraints.Size(max = 100, message = "Cannot fetch more than 100 products at once") List<UUID> ids) {
        try {
            return ResponseEntity.ok(productService.getProductsByIds(companyId, ids));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<ProductResponse> createProduct(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateProductRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}")
    @RequireAuth
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.updateProduct(companyId, id, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Void> deleteProduct(
            @PathVariable UUID companyId,
            @PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
            productService.deleteProduct(companyId, id, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{productId}/images")
    public ResponseEntity<List<ProductImageResponse>> getProductImages(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            requirePublicChildReadAccess(companyId, productId);
            return ResponseEntity.ok(productService.getProductImages(companyId, productId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{productId}/images")
    @RequireAuth
    public ResponseEntity<ProductImageResponse> addProductImage(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody AddProductImageRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(productService.addProductImage(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @RequireAuth
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        try {
            UUID userId = resolveUserId();
            productService.deleteProductImage(companyId, productId, imageId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{productId}/images/reorder")
    @RequireAuth
    public ResponseEntity<List<ProductImageResponse>> reorderProductImages(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody ReorderProductImagesRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.reorderProductImages(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // --- Options ---

    @GetMapping("/{productId}/options")
    public ResponseEntity<List<ProductOptionResponse>> getProductOptions(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            requirePublicChildReadAccess(companyId, productId);
            return ResponseEntity.ok(productService.getProductOptions(companyId, productId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{productId}/options")
    @RequireAuth
    public ResponseEntity<ProductOptionResponse> addProductOption(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateProductOptionRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(productService.addProductOption(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{productId}/options/{optionId}")
    @RequireAuth
    public ResponseEntity<ProductOptionResponse> updateProductOption(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID optionId,
            @Valid @RequestBody UpdateProductOptionRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.updateProductOption(companyId, productId, optionId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{productId}/options/{optionId}")
    @RequireAuth
    public ResponseEntity<Void> deleteProductOption(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID optionId) {
        try {
            UUID userId = resolveUserId();
            productService.deleteProductOption(companyId, productId, optionId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // --- Variants ---

    @GetMapping("/{productId}/variants")
    public ResponseEntity<List<ProductVariantResponse>> getProductVariants(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            requirePublicChildReadAccess(companyId, productId);
            return ResponseEntity.ok(productService.getProductVariants(companyId, productId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<ProductVariantResponse> getProductVariant(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID variantId) {
        try {
            requirePublicChildReadAccess(companyId, productId);
            return ResponseEntity.ok(productService.getProductVariant(companyId, productId, variantId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{productId}/variants")
    @RequireAuth
    public ResponseEntity<ProductVariantResponse> createProductVariant(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateProductVariantRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(productService.createProductVariant(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{productId}/variants/{variantId}")
    @RequireAuth
    public ResponseEntity<ProductVariantResponse> updateProductVariant(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateProductVariantRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.updateProductVariant(companyId, productId, variantId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    @RequireAuth
    public ResponseEntity<Void> deleteProductVariant(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID variantId) {
        try {
            UUID userId = resolveUserId();
            productService.deleteProductVariant(companyId, productId, variantId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // --- Attributes ---

    @GetMapping("/{productId}/attributes")
    public ResponseEntity<List<ProductAttributeResponse>> getProductAttributes(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            requirePublicChildReadAccess(companyId, productId);
            return ResponseEntity.ok(productService.getProductAttributes(companyId, productId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PutMapping("/{productId}/attributes")
    @RequireAuth
    public ResponseEntity<List<ProductAttributeResponse>> setProductAttributes(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody SetProductAttributesRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.setProductAttributes(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // --- Relationship endpoints ---

    @GetMapping("/{productId}/relationships")
    public ResponseEntity<List<ProductRelationshipResponse>> getProductRelationships(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestParam(required = false) ProductRelationshipType type) {
        try {
            requirePublicChildReadAccess(companyId, productId);
            return ResponseEntity.ok(productService.getProductRelationships(companyId, productId, type));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{productId}/relationships")
    @RequireAuth
    public ResponseEntity<ProductRelationshipResponse> addProductRelationship(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody AddProductRelationshipRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(productService.addProductRelationship(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{productId}/relationships/{targetProductId}")
    @RequireAuth
    public ResponseEntity<Void> removeProductRelationship(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @PathVariable UUID targetProductId,
            @RequestParam ProductRelationshipType type) {
        try {
            UUID userId = resolveUserId();
            productService.removeProductRelationship(companyId, productId, targetProductId, type, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{productId}/similar")
    public ResponseEntity<List<SimilarProductResponse>> getSimilarProducts(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        try {
            return ResponseEntity.ok(productService.getSimilarProducts(companyId, productId, limit));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // --- Bundle discoverability endpoints (public read, under /products/bundles) ---

    @GetMapping("/bundles")
    public ResponseEntity<PagedResponse<BundleResponse>> listBundles(
            @PathVariable UUID companyId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(bundleService.listBundles(companyId, status, page, size));
    }

    @GetMapping("/bundles/{bundleId}")
    public ResponseEntity<BundleResponse> getBundle(
            @PathVariable UUID companyId,
            @PathVariable UUID bundleId) {
        return ResponseEntity.ok(bundleService.getBundle(companyId, bundleId));
    }

    @PatchMapping("/{productId}/marketplace")
    @RequireAuth
    public ResponseEntity<ProductResponse> updateMarketplaceListing(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateMarketplaceListingRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.updateMarketplaceListing(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{productId}/merchandising")
    @RequireAuth
    public ResponseEntity<ProductResponse> updateProductMerchandising(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductMerchandisingRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.updateProductMerchandising(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // --- Compare ---

    @GetMapping("/compare")
    public ResponseEntity<List<ProductResponse>> compareProducts(
            @PathVariable UUID companyId,
            @RequestParam @jakarta.validation.constraints.Size(max = 50, message = "Cannot compare more than 50 products at once") List<UUID> ids) {
        try {
            return ResponseEntity.ok(productService.compareProducts(companyId, ids));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{productId}/history")
    @RequireAuth
    public ResponseEntity<PagedResponse<ProductHistoryEntryResponse>> getProductHistory(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        try {
            return ResponseEntity.ok(productService.getProductHistory(companyId, productId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{productId}/revert")
    @RequireAuth
    public ResponseEntity<ProductResponse> revertProductChanges(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody RevertProductChangesRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(productService.revertProductChanges(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/reindex")
    @RequireAuth
    public ResponseEntity<Void> triggerReindex(@PathVariable UUID companyId) {
        try {
            companyAccessService.requireAnyAccess(companyId, resolveUserId());
            productIndexingService.reindexCompany(companyId);
            return ResponseEntity.accepted().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }

    private UUID resolveUserIdOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return (UUID) auth.getPrincipal();
    }

    private boolean isCompanyMember(UUID companyId, UUID userId) {
        if (userId == null) return false;
        return companyAccessService.resolveRole(companyId, userId).isPresent();
    }

    /**
     * Guards the public (unauthenticated) child-read endpoints: a non-member may only read a
     * product's images/options/variants/attributes/relationships when the product itself is
     * publicly visible (ACTIVE + listed). Members fall through to the service's company-scoped
     * checks. Prevents enumeration of draft/unlisted product metadata by guessing the UUID.
     */
    private void requirePublicChildReadAccess(UUID companyId, UUID productId) {
        if (!isCompanyMember(companyId, resolveUserIdOptional())
                && !productService.isPubliclyVisible(companyId, productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
    }
}
