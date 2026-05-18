package backend.controllers.impl.inventory;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.SetLocationStockRequest;
import backend.dtos.requests.inventory.UpdateLocationRequest;
import backend.dtos.responses.inventory.LocationResponse;
import backend.dtos.responses.inventory.LocationStockResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.inventory.LocationInventoryService;
import backend.services.intf.SanitizationService;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/inventory/locations")
public class LocationInventoryController {

    private final LocationInventoryService locationInventoryService;
    private final SanitizationService sanitizationService;

    public LocationInventoryController(LocationInventoryService locationInventoryService,
                                       SanitizationService sanitizationService) {
        this.locationInventoryService = locationInventoryService;
        this.sanitizationService = sanitizationService;
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<LocationResponse>> getLocations(@PathVariable UUID companyId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(locationInventoryService.getLocations(companyId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<LocationResponse> createLocation(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateLocationRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(locationInventoryService.createLocation(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{locationId}")
    @RequireAuth
    public ResponseEntity<LocationResponse> getLocation(
            @PathVariable UUID companyId,
            @PathVariable UUID locationId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(locationInventoryService.getLocation(companyId, locationId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{locationId}")
    @RequireAuth
    public ResponseEntity<LocationResponse> updateLocation(
            @PathVariable UUID companyId,
            @PathVariable UUID locationId,
            @Valid @RequestBody UpdateLocationRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(locationInventoryService.updateLocation(companyId, locationId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{locationId}")
    @RequireAuth
    public ResponseEntity<Void> deleteLocation(
            @PathVariable UUID companyId,
            @PathVariable UUID locationId) {
        try {
            UUID userId = resolveUserId();
            locationInventoryService.deleteLocation(companyId, locationId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{locationId}/stock")
    @RequireAuth
    public ResponseEntity<List<LocationStockResponse>> getLocationStock(
            @PathVariable UUID companyId,
            @PathVariable UUID locationId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(locationInventoryService.getLocationStock(companyId, locationId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PutMapping("/{locationId}/stock/{productId}")
    @RequireAuth
    public ResponseEntity<LocationStockResponse> setLocationStock(
            @PathVariable UUID companyId,
            @PathVariable UUID locationId,
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID variantId,
            @Valid @RequestBody SetLocationStockRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(locationInventoryService.setLocationStock(
                    companyId, locationId, productId, userId, request, variantId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{locationId}/stock/{productId}/adjust")
    @RequireAuth
    public ResponseEntity<LocationStockResponse> adjustLocationStock(
            @PathVariable UUID companyId,
            @PathVariable UUID locationId,
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID variantId,
            @Valid @RequestBody AdjustStockRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(locationInventoryService.adjustLocationStock(
                    companyId, locationId, productId, userId, request, variantId));
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
