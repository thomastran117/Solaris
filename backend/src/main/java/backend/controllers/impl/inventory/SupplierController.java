package backend.controllers.impl.inventory;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.inventory.CreateSupplierRequest;
import backend.dtos.requests.inventory.UpdateSupplierRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.SupplierResponse;
import backend.services.intf.inventory.SupplierService;
import backend.services.intf.SanitizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/companies/{companyId}/suppliers")
@RequireAuth
public class SupplierController {

    private final SupplierService supplierService;
    private final SanitizationService sanitizationService;

    public SupplierController(SupplierService supplierService, SanitizationService sanitizationService) {
        this.supplierService = supplierService;
        this.sanitizationService = sanitizationService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<SupplierResponse>> listSuppliers(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return ResponseEntity.ok(supplierService.listSuppliers(companyId, resolveUserId(), pageable));
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateSupplierRequest request) {
        sanitizationService.normalize(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supplierService.createSupplier(companyId, resolveUserId(), request));
    }

    @PatchMapping("/{supplierId}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable UUID companyId,
            @PathVariable UUID supplierId,
            @Valid @RequestBody UpdateSupplierRequest request) {
        sanitizationService.normalize(request);
        return ResponseEntity.ok(supplierService.updateSupplier(companyId, supplierId, resolveUserId(), request));
    }

    @DeleteMapping("/{supplierId}")
    public ResponseEntity<Void> deleteSupplier(
            @PathVariable UUID companyId,
            @PathVariable UUID supplierId) {
        supplierService.deleteSupplier(companyId, supplierId, resolveUserId());
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
