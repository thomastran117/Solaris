package backend.controllers.impl.marketplace;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.marketplace.CreateMarketplaceRequest;
import backend.dtos.requests.marketplace.UpdateMarketplaceRequest;
import backend.dtos.responses.marketplace.MarketplaceProfileResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.marketplace.MarketplaceService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/marketplaces")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    public MarketplaceController(MarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    @PostMapping("/companies/{companyId}")
    @RequireAuth
    public ResponseEntity<MarketplaceProfileResponse> createMarketplace(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateMarketplaceRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(marketplaceService.createMarketplace(userId, companyId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{marketplaceId}")
    public ResponseEntity<MarketplaceProfileResponse> getMarketplace(@PathVariable UUID marketplaceId) {
        try {
            return ResponseEntity.ok(marketplaceService.getMarketplace(marketplaceId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{marketplaceId}")
    @RequireAuth
    public ResponseEntity<MarketplaceProfileResponse> updateMarketplace(
            @PathVariable UUID marketplaceId,
            @Valid @RequestBody UpdateMarketplaceRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(marketplaceService.updateMarketplace(marketplaceId, userId, request));
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
}
