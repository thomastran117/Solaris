package backend.controllers.impl.marketplace;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.CatalogSearchResponse;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.ProductComparisonResponse;
import backend.dtos.responses.product.VendorStorefrontResponse;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.products.ProductComparisonService;
import backend.services.intf.products.ProductFeedService;
import backend.services.intf.products.ProductService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/marketplaces/{marketplaceId}/catalog")
public class MarketplaceCatalogController {

    private final ProductService productService;
    private final ProductFeedService productFeedService;
    private final ProductComparisonService productComparisonService;
    private final ActivityEventPublisher activityEventPublisher;

    public MarketplaceCatalogController(
            ProductService productService,
            ProductFeedService productFeedService,
            ProductComparisonService productComparisonService,
            ActivityEventPublisher activityEventPublisher) {
        this.productService = productService;
        this.productFeedService = productFeedService;
        this.productComparisonService = productComparisonService;
        this.activityEventPublisher = activityEventPublisher;
    }

    public record TrackViewRequest(String sessionId) {}

    @PostMapping("/products/{productId}/view")
    public ResponseEntity<Void> trackView(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID productId,
            @RequestBody(required = false) TrackViewRequest body,
            HttpServletRequest request) {
        if ("1".equals(request.getHeader("DNT"))) {
            return ResponseEntity.noContent().build();
        }
        UUID userId = resolveUserIdOrNull();
        String sessionId = (body != null) ? body.sessionId() : null;
        activityEventPublisher.publish(new UserActivityEvent(
                userId, sessionId, productId, null, ActivityType.VIEW, Instant.now()));
        return ResponseEntity.noContent().build();
    }

    @RequireAuth
    @GetMapping("/feed")
    public ResponseEntity<PagedResponse<MarketplaceCatalogProductResponse>> getFeed(
            @PathVariable UUID marketplaceId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        // resolveUserId() throws AccessDeniedException when unauthenticated — must be called
        // before the catch-all block so GlobalExceptionHandler can map it to 401.
        UUID userId = resolveUserId();
        try {
            return ResponseEntity.ok(productFeedService.getFeed(marketplaceId, userId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID uuid))
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        return uuid;
    }

    private UUID resolveUserIdOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            Object principal = auth.getPrincipal();
            if (principal instanceof UUID uuid) return uuid;
        } catch (Exception ignored) {}
        return null;
    }

    @GetMapping("/products")
    public ResponseEntity<CatalogSearchResponse> searchCatalog(
            @PathVariable UUID marketplaceId,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") @Pattern(regexp = "^[a-zA-Z.]+$", message = "Invalid sort field") String sort,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "^(?i)(asc|desc)$", message = "Direction must be asc or desc") String direction) {
        try {
            return ResponseEntity.ok(productService.searchMarketplaceCatalog(
                    marketplaceId, q, category, brand, minPrice, maxPrice,
                    featured, vendorId, page, size, sort, direction));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/products/compare")
    public ResponseEntity<ProductComparisonResponse> compareProducts(
            @PathVariable UUID marketplaceId,
            @RequestParam @Size(min = 2, max = 4, message = "Comparison requires between 2 and 4 product IDs") List<UUID> ids) {
        try {
            return ResponseEntity.ok(productComparisonService.compare(marketplaceId, ids));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<MarketplaceCatalogProductResponse> getProduct(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID productId) {
        try {
            return ResponseEntity.ok(productService.getMarketplaceProduct(marketplaceId, productId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/vendors/{vendorId}/storefront")
    public ResponseEntity<VendorStorefrontResponse> getVendorStorefront(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId) {
        try {
            return ResponseEntity.ok(productService.getVendorStorefront(marketplaceId, vendorId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
