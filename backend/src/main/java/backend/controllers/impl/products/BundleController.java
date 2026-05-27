package backend.controllers.impl.products;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.product.BatchDeleteBundlesRequest;
import backend.dtos.requests.product.BatchUpdateBundlesRequest;
import backend.dtos.requests.product.CreateBundleRequest;
import backend.dtos.requests.product.UpdateBundleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.BundleResponse;
import backend.models.enums.ProductStatus;
import backend.services.intf.products.BundleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/bundles")
public class BundleController {

    private final BundleService bundleService;

    public BundleController(BundleService bundleService) {
        this.bundleService = bundleService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<BundleResponse>> listBundles(
            @PathVariable UUID companyId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(bundleService.listBundles(companyId, status, page, size));
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<BundleResponse> createBundle(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateBundleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bundleService.createBundle(companyId, resolveUserId(), request));
    }

    @GetMapping("/{bundleId}")
    public ResponseEntity<BundleResponse> getBundle(
            @PathVariable UUID companyId,
            @PathVariable UUID bundleId) {
        return ResponseEntity.ok(bundleService.getBundle(companyId, bundleId));
    }

    @PatchMapping("/{bundleId}")
    @RequireAuth
    public ResponseEntity<BundleResponse> updateBundle(
            @PathVariable UUID companyId,
            @PathVariable UUID bundleId,
            @Valid @RequestBody UpdateBundleRequest request) {
        return ResponseEntity.ok(bundleService.updateBundle(companyId, bundleId, resolveUserId(), request));
    }

    @DeleteMapping("/{bundleId}")
    @RequireAuth
    public ResponseEntity<Void> deleteBundle(
            @PathVariable UUID companyId,
            @PathVariable UUID bundleId) {
        bundleService.deleteBundle(companyId, bundleId, resolveUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch-update")
    @RequireAuth
    public ResponseEntity<List<BundleResponse>> batchUpdateBundles(
            @PathVariable UUID companyId,
            @Valid @RequestBody BatchUpdateBundlesRequest request) {
        return ResponseEntity.ok(bundleService.batchUpdateBundles(companyId, resolveUserId(), request));
    }

    @PostMapping("/batch-delete")
    @RequireAuth
    public ResponseEntity<Void> batchDeleteBundles(
            @PathVariable UUID companyId,
            @Valid @RequestBody BatchDeleteBundlesRequest request) {
        bundleService.batchDeleteBundles(companyId, resolveUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/compare")
    public ResponseEntity<List<BundleResponse>> compareBundles(
            @PathVariable UUID companyId,
            @RequestParam @jakarta.validation.constraints.Size(max = 50, message = "Cannot compare more than 50 bundles at once") List<UUID> ids) {
        return ResponseEntity.ok(bundleService.compareBundles(companyId, ids));
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
