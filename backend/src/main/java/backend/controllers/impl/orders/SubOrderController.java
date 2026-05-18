package backend.controllers.impl.orders;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.order.CancelSubOrderRequest;
import backend.dtos.requests.order.ShipSubOrderRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CommissionRecordResponse;
import backend.dtos.responses.order.SubOrderResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.SubOrderStatus;
import backend.services.intf.orders.SubOrderService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vendors/{vendorId}/sub-orders")
@RequireAuth
public class SubOrderController {

    private final SubOrderService subOrderService;

    public SubOrderController(SubOrderService subOrderService) {
        this.subOrderService = subOrderService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<SubOrderResponse>> list(
            @PathVariable UUID vendorId,
            @RequestParam(required = false) SubOrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(subOrderService.listVendorSubOrders(vendorId, status, page, size, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{subOrderId}")
    public ResponseEntity<SubOrderResponse> get(
            @PathVariable UUID vendorId,
            @PathVariable UUID subOrderId) {
        try {
            return ResponseEntity.ok(subOrderService.getSubOrder(subOrderId, vendorId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{subOrderId}/pack")
    public ResponseEntity<SubOrderResponse> pack(
            @PathVariable UUID vendorId,
            @PathVariable UUID subOrderId) {
        try {
            return ResponseEntity.ok(subOrderService.markPacked(subOrderId, vendorId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{subOrderId}/ship")
    public ResponseEntity<SubOrderResponse> ship(
            @PathVariable UUID vendorId,
            @PathVariable UUID subOrderId,
            @Valid @RequestBody ShipSubOrderRequest request) {
        try {
            return ResponseEntity.ok(subOrderService.markShipped(subOrderId, vendorId, request, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{subOrderId}/deliver")
    public ResponseEntity<SubOrderResponse> deliver(
            @PathVariable UUID vendorId,
            @PathVariable UUID subOrderId) {
        try {
            return ResponseEntity.ok(subOrderService.markDelivered(subOrderId, vendorId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{subOrderId}/cancel")
    public ResponseEntity<SubOrderResponse> cancel(
            @PathVariable UUID vendorId,
            @PathVariable UUID subOrderId,
            @Valid @RequestBody CancelSubOrderRequest request) {
        try {
            return ResponseEntity.ok(subOrderService.cancelSubOrder(subOrderId, vendorId, request, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{subOrderId}/commission")
    public ResponseEntity<CommissionRecordResponse> commission(
            @PathVariable UUID vendorId,
            @PathVariable UUID subOrderId) {
        try {
            return ResponseEntity.ok(subOrderService.getCommissionRecord(subOrderId, vendorId, resolveUserId()));
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
