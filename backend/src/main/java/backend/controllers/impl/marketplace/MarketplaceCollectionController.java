package backend.controllers.impl.marketplace;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import backend.dtos.responses.collection.CollectionProductResponse;
import backend.dtos.responses.collection.CollectionResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.collections.CollectionService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Public storefront endpoints for browsing featured collections and viewing collection detail.
 * Mirrors the {@code /marketplaces/{mId}/catalog/...} pattern used by {@code MarketplaceCatalogController}.
 */
@RestController
@RequestMapping("/marketplaces/{marketplaceId}/collections")
public class MarketplaceCollectionController {

    private final CollectionService collectionService;

    public MarketplaceCollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /** Featured collections, ordered by featuredRank. Used by marketplace home page. */
    @GetMapping("/featured")
    public ResponseEntity<List<CollectionResponse>> listFeatured(@PathVariable UUID marketplaceId) {
        try {
            return ResponseEntity.ok(collectionService.listFeaturedForMarketplace(marketplaceId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    /** Featured collections for a single vendor — used on the vendor storefront. */
    @GetMapping("/featured/vendor/{vendorId}")
    public ResponseEntity<List<CollectionResponse>> listFeaturedForVendor(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId) {
        try {
            return ResponseEntity.ok(collectionService.listFeaturedForVendor(marketplaceId, vendorId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CollectionResponse> getBySlug(
            @PathVariable UUID marketplaceId,
            @PathVariable String slug) {
        try {
            return ResponseEntity.ok(collectionService.getCollectionBySlug(marketplaceId, slug));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{slug}/products")
    public ResponseEntity<PagedResponse<CollectionProductResponse>> listProductsBySlug(
            @PathVariable UUID marketplaceId,
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(collectionService.listMarketplaceCollectionProducts(
                    marketplaceId, slug, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
