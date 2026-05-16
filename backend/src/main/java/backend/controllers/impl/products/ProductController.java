package backend.controllers.impl.products;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.BatchDeleteProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.ReorderProductImagesRequest;
import backend.dtos.requests.product.RevertProductChangesRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
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
import backend.models.enums.ProductStatus;
import backend.kafka.workers.ProductIndexingService;
import backend.services.intf.products.BundleService;
import backend.services.intf.products.ProductService;
import backend.services.intf.SanitizationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/products")
public class ProductController {

    private final ProductService productService;
    private final BundleService bundleService;
    private final ProductIndexingService productIndexingService;
    private final SanitizationService sanitizationService;

    public ProductController(ProductService productService,
                             BundleService bundleService,
                             ProductIndexingService productIndexingService,
                             SanitizationService sanitizationService) {
        this.productService = productService;
        this.bundleService = bundleService;
        this.productIndexingService = productIndexingService;
        this.sanitizationService = sanitizationService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getProducts(
            @PathVariable long companyId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Boolean listed,
            @RequestParam(required = false) String discountCategory,
            @RequestParam(required = false) Boolean hasDiscount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        try {
            return ResponseEntity.ok(productService.searchProducts(companyId, q, category, brand, minPrice, maxPrice, featured, status, listed, discountCategory, hasDiscount, page, size, sort, direction));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable long companyId,
            @PathVariable long id) {
        try {
            return ResponseEntity.ok(productService.getProduct(companyId, id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/batch-create")
    @RequireAuth
    public ResponseEntity<List<ProductResponse>> batchCreateProducts(
            @PathVariable long companyId,
            @Valid @RequestBody BatchCreateProductsRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @Valid @RequestBody BatchDeleteProductsRequest request) {
        try {
            long userId = resolveUserId();
            productService.batchDeleteProducts(companyId, userId, request);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/batch")
    @RequireAuth
    public ResponseEntity<List<ProductResponse>> getProductsByIds(
            @PathVariable long companyId,
            @RequestBody @jakarta.validation.constraints.Size(max = 100, message = "Cannot fetch more than 100 products at once") List<Long> ids) {
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
            @PathVariable long companyId,
            @Valid @RequestBody CreateProductRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateProductRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long id) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId) {
        try {
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody AddProductImageRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @PathVariable long imageId) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody ReorderProductImagesRequest request) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId) {
        try {
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody CreateProductOptionRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @PathVariable long optionId,
            @Valid @RequestBody UpdateProductOptionRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @PathVariable long optionId) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId) {
        try {
            return ResponseEntity.ok(productService.getProductVariants(companyId, productId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<ProductVariantResponse> getProductVariant(
            @PathVariable long companyId,
            @PathVariable long productId,
            @PathVariable long variantId) {
        try {
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody CreateProductVariantRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @PathVariable long variantId,
            @Valid @RequestBody UpdateProductVariantRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @PathVariable long variantId) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId) {
        try {
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody SetProductAttributesRequest request) {
        try {
            sanitizationService.normalize(request);
            long userId = resolveUserId();
            return ResponseEntity.ok(productService.setProductAttributes(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // --- Bundle discoverability endpoints (public read, under /products/bundles) ---

    @GetMapping("/bundles")
    public ResponseEntity<PagedResponse<BundleResponse>> listBundles(
            @PathVariable long companyId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(bundleService.listBundles(companyId, status, page, size));
    }

    @GetMapping("/bundles/{bundleId}")
    public ResponseEntity<BundleResponse> getBundle(
            @PathVariable long companyId,
            @PathVariable long bundleId) {
        return ResponseEntity.ok(bundleService.getBundle(companyId, bundleId));
    }

    @PatchMapping("/{productId}/marketplace")
    @RequireAuth
    public ResponseEntity<ProductResponse> updateMarketplaceListing(
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody UpdateMarketplaceListingRequest request) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody UpdateProductMerchandisingRequest request) {
        try {
            long userId = resolveUserId();
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
            @PathVariable long companyId,
            @RequestParam @jakarta.validation.constraints.Size(max = 50, message = "Cannot compare more than 50 products at once") List<Long> ids) {
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
            @PathVariable long companyId,
            @PathVariable long productId,
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
            @PathVariable long companyId,
            @PathVariable long productId,
            @Valid @RequestBody RevertProductChangesRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(productService.revertProductChanges(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/reindex")
    @RequireAuth
    public ResponseEntity<Void> triggerReindex(@PathVariable long companyId) {
        try {
            productIndexingService.reindexCompany(companyId);
            return ResponseEntity.accepted().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
