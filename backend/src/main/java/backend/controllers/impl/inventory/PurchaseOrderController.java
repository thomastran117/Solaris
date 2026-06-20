package backend.controllers.impl.inventory;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.inventory.CreatePORequest;
import backend.dtos.requests.inventory.ReceiveItemsRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.PurchaseOrderResponse;
import backend.models.enums.POStatus;
import backend.services.intf.inventory.PurchaseOrderService;
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
@RequestMapping("/companies/{companyId}/purchase-orders")
@RequireAuth
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final SanitizationService sanitizationService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService,
                                   SanitizationService sanitizationService) {
        this.purchaseOrderService = purchaseOrderService;
        this.sanitizationService = sanitizationService;
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> createPO(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreatePORequest request) {
        sanitizationService.normalize(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseOrderService.createPO(companyId, resolveUserId(), request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<PurchaseOrderResponse>> listPOs(
            @PathVariable UUID companyId,
            @RequestParam(required = false) POStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(purchaseOrderService.listPOs(companyId, resolveUserId(), status, pageable));
    }

    @GetMapping("/{poId}")
    public ResponseEntity<PurchaseOrderResponse> getPO(
            @PathVariable UUID companyId,
            @PathVariable UUID poId) {
        return ResponseEntity.ok(purchaseOrderService.getPO(companyId, poId, resolveUserId()));
    }

    @PostMapping("/{poId}/send")
    public ResponseEntity<PurchaseOrderResponse> sendPO(
            @PathVariable UUID companyId,
            @PathVariable UUID poId) {
        return ResponseEntity.ok(purchaseOrderService.sendPO(companyId, poId, resolveUserId()));
    }

    @PostMapping("/{poId}/confirm")
    public ResponseEntity<PurchaseOrderResponse> confirmPO(
            @PathVariable UUID companyId,
            @PathVariable UUID poId) {
        return ResponseEntity.ok(purchaseOrderService.confirmPO(companyId, poId, resolveUserId()));
    }

    @PostMapping("/{poId}/receive")
    public ResponseEntity<PurchaseOrderResponse> receiveItems(
            @PathVariable UUID companyId,
            @PathVariable UUID poId,
            @Valid @RequestBody ReceiveItemsRequest request) {
        return ResponseEntity.ok(purchaseOrderService.receiveItems(companyId, poId, resolveUserId(), request));
    }

    @PostMapping("/{poId}/cancel")
    public ResponseEntity<PurchaseOrderResponse> cancelPO(
            @PathVariable UUID companyId,
            @PathVariable UUID poId) {
        return ResponseEntity.ok(purchaseOrderService.cancelPO(companyId, poId, resolveUserId()));
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
