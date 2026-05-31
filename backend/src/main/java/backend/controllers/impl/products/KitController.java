package backend.controllers.impl.products;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.product.CreateKitRequest;
import backend.dtos.requests.product.ImportFromCollectionRequest;
import backend.dtos.requests.product.UpdateKitRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.KitResponse;
import backend.models.enums.ProductStatus;
import backend.services.intf.products.KitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/companies/{companyId}/kits")
public class KitController {

    private final KitService kitService;

    public KitController(KitService kitService) {
        this.kitService = kitService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<KitResponse>> listKits(
            @PathVariable UUID companyId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(kitService.listKits(companyId, status, page, size));
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<KitResponse> createKit(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateKitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kitService.createKit(companyId, resolveUserId(), request));
    }

    @GetMapping("/{kitId}")
    public ResponseEntity<KitResponse> getKit(
            @PathVariable UUID companyId,
            @PathVariable UUID kitId) {
        return ResponseEntity.ok(kitService.getKit(companyId, kitId));
    }

    @PatchMapping("/{kitId}")
    @RequireAuth
    public ResponseEntity<KitResponse> updateKit(
            @PathVariable UUID companyId,
            @PathVariable UUID kitId,
            @Valid @RequestBody UpdateKitRequest request) {
        return ResponseEntity.ok(kitService.updateKit(companyId, kitId, resolveUserId(), request));
    }

    @DeleteMapping("/{kitId}")
    @RequireAuth
    public ResponseEntity<Void> deleteKit(
            @PathVariable UUID companyId,
            @PathVariable UUID kitId) {
        kitService.deleteKit(companyId, kitId, resolveUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{kitId}/import-collection")
    @RequireAuth
    public ResponseEntity<KitResponse> importChoicesFromCollection(
            @PathVariable UUID companyId,
            @PathVariable UUID kitId,
            @Valid @RequestBody ImportFromCollectionRequest request) {
        return ResponseEntity.ok(kitService.importChoicesFromCollection(companyId, kitId, resolveUserId(), request));
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
