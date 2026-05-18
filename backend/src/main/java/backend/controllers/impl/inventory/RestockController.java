package backend.controllers.impl.inventory;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.inventory.CreateRestockRequest;
import backend.dtos.requests.inventory.UpdateRestockRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.RestockRequestResponse;
import backend.models.enums.RestockStatus;
import backend.services.intf.inventory.RestockService;
import backend.services.intf.SanitizationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/companies/{companyId}/inventory/restock")
@RequireAuth
public class RestockController {

    private final RestockService restockService;
    private final SanitizationService sanitizationService;

    public RestockController(RestockService restockService, SanitizationService sanitizationService) {
        this.restockService = restockService;
        this.sanitizationService = sanitizationService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<RestockRequestResponse>> listRestockRequests(
            @PathVariable UUID companyId,
            @RequestParam(required = false) RestockStatus status,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(
                restockService.listRestockRequests(companyId, resolveUserId(), status, productId, page, size));
    }

    @PostMapping
    public ResponseEntity<RestockRequestResponse> createRestockRequest(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateRestockRequest request) {
        sanitizationService.normalize(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restockService.createRestockRequest(companyId, resolveUserId(), request));
    }

    @GetMapping("/{restockId}")
    public ResponseEntity<RestockRequestResponse> getRestockRequest(
            @PathVariable UUID companyId,
            @PathVariable UUID restockId) {
        return ResponseEntity.ok(
                restockService.getRestockRequest(companyId, restockId, resolveUserId()));
    }

    @PatchMapping("/{restockId}")
    public ResponseEntity<RestockRequestResponse> updateRestockRequest(
            @PathVariable UUID companyId,
            @PathVariable UUID restockId,
            @Valid @RequestBody UpdateRestockRequest request) {
        sanitizationService.normalize(request);
        return ResponseEntity.ok(
                restockService.updateRestockRequest(companyId, restockId, resolveUserId(), request));
    }

    @DeleteMapping("/{restockId}")
    public ResponseEntity<Void> deleteRestockRequest(
            @PathVariable UUID companyId,
            @PathVariable UUID restockId) {
        restockService.deleteRestockRequest(companyId, restockId, resolveUserId());
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
