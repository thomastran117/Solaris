package backend.controllers.impl.inventory;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.inventory.CreateTransferRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.InventoryTransferResponse;
import backend.models.enums.TransferStatus;
import backend.services.intf.SanitizationService;
import backend.services.intf.inventory.InventoryTransferService;
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
@RequestMapping("/companies/{companyId}/inventory/transfers")
@RequireAuth
public class InventoryTransferController {

    private final InventoryTransferService transferService;
    private final SanitizationService sanitizationService;

    public InventoryTransferController(InventoryTransferService transferService,
                                       SanitizationService sanitizationService) {
        this.transferService = transferService;
        this.sanitizationService = sanitizationService;
    }

    @PostMapping
    public ResponseEntity<InventoryTransferResponse> createTransfer(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateTransferRequest request) {
        sanitizationService.normalize(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.createTransfer(companyId, resolveUserId(), request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<InventoryTransferResponse>> listTransfers(
            @PathVariable UUID companyId,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(transferService.listTransfers(companyId, resolveUserId(), status, locationId, pageable));
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<InventoryTransferResponse> getTransfer(
            @PathVariable UUID companyId,
            @PathVariable UUID transferId) {
        return ResponseEntity.ok(transferService.getTransfer(companyId, transferId, resolveUserId()));
    }

    @PostMapping("/{transferId}/dispatch")
    public ResponseEntity<InventoryTransferResponse> dispatchTransfer(
            @PathVariable UUID companyId,
            @PathVariable UUID transferId) {
        return ResponseEntity.ok(transferService.markInTransit(companyId, transferId, resolveUserId()));
    }

    @PostMapping("/{transferId}/receive")
    public ResponseEntity<InventoryTransferResponse> receiveTransfer(
            @PathVariable UUID companyId,
            @PathVariable UUID transferId) {
        return ResponseEntity.ok(transferService.receiveTransfer(companyId, transferId, resolveUserId()));
    }

    @PostMapping("/{transferId}/cancel")
    public ResponseEntity<InventoryTransferResponse> cancelTransfer(
            @PathVariable UUID companyId,
            @PathVariable UUID transferId) {
        return ResponseEntity.ok(transferService.cancelTransfer(companyId, transferId, resolveUserId()));
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
