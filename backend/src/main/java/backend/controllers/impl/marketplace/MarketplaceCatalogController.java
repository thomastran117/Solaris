package backend.controllers.impl.marketplace;

import backend.dtos.responses.product.CatalogSearchResponse;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.VendorStorefrontResponse;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.ActivityEventPublisher;
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

@Validated
@RestController
@RequestMapping("/marketplaces/{marketplaceId}/catalog")
public class MarketplaceCatalogController {

    private final ProductService productService;
    private final ActivityEventPublisher activityEventPublisher;

    public MarketplaceCatalogController(ProductService productService, ActivityEventPublisher activityEventPublisher) {
        this.productService = productService;
        this.activityEventPublisher = activityEventPublisher;
    }

    public record TrackViewRequest(String sessionId) {}

    @PostMapping("/products/{productId}/view")
    public ResponseEntity<Void> trackView(
            @PathVariable long marketplaceId,
            @PathVariable long productId,
            @RequestBody(required = false) TrackViewRequest body,
            HttpServletRequest request) {
        if ("1".equals(request.getHeader("DNT"))) {
            return ResponseEntity.noContent().build();
        }
        Long userId = resolveUserIdOrNull();
        String sessionId = (body != null) ? body.sessionId() : null;
        activityEventPublisher.publish(new UserActivityEvent(
                userId, sessionId, productId, marketplaceId, ActivityType.VIEW, Instant.now()));
        return ResponseEntity.noContent().build();
    }

    private Long resolveUserIdOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            Object principal = auth.getPrincipal();
            if (principal instanceof Number) return ((Number) principal).longValue();
        } catch (Exception ignored) {}
        return null;
    }

    @GetMapping("/products")
    public ResponseEntity<CatalogSearchResponse> searchCatalog(
            @PathVariable long marketplaceId,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Long vendorId,
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

    @GetMapping("/products/{productId}")
    public ResponseEntity<MarketplaceCatalogProductResponse> getProduct(
            @PathVariable long marketplaceId,
            @PathVariable long productId) {
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
            @PathVariable long marketplaceId,
            @PathVariable long vendorId) {
        try {
            return ResponseEntity.ok(productService.getVendorStorefront(marketplaceId, vendorId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
