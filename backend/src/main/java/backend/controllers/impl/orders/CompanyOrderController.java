package backend.controllers.impl.orders;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.order.MarkSlotUnavailableRequest;
import backend.dtos.requests.order.ReturnOrderRequest;
import backend.dtos.requests.order.ShipOrderRequest;
import backend.dtos.requests.return_.MerchantInitiateReturnRequest;
import backend.dtos.requests.risk.RiskDecisionRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CompanyOrderResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.return_.ReturnResponse;
import backend.dtos.responses.risk.RiskAssessmentResponse;
import backend.dtos.responses.risk.RiskReviewResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.OrderStatus;
import backend.models.enums.RiskReviewStatus;
import backend.dtos.requests.order.AssignDriverRequest;
import backend.services.intf.orders.DeliveryService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.returns.ReturnService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/orders")
@RequireAuth
public class CompanyOrderController {

    private final OrderService orderService;
    private final ReturnService returnService;
    private final DeliveryService deliveryService;

    public CompanyOrderController(OrderService orderService, ReturnService returnService,
                                  DeliveryService deliveryService) {
        this.orderService = orderService;
        this.returnService = returnService;
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<CompanyOrderResponse>> getCompanyOrders(
            @PathVariable UUID companyId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate deliveryDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.getCompanyOrders(companyId, userId, status, deliveryDate, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<CompanyOrderResponse> getCompanyOrder(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.getCompanyOrder(companyId, orderId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/pack")
    public ResponseEntity<CompanyOrderResponse> markAsPacked(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.markAsPacked(companyId, orderId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/ship")
    public ResponseEntity<CompanyOrderResponse> markAsShipped(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId,
            @RequestBody @Valid ShipOrderRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.markAsShipped(companyId, orderId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/pickup-ready")
    public ResponseEntity<CompanyOrderResponse> markAsPickupReady(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.markAsPickupReady(companyId, orderId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<CompanyOrderResponse> markAsDelivered(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.markAsDelivered(companyId, orderId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{orderId}/delivery-slot/confirm")
    public ResponseEntity<CompanyOrderResponse> confirmDeliverySlot(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.confirmSlot(companyId, orderId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // The body is optional by design: MarkSlotUnavailableRequest carries only an optional
    // free-text reason, so a vendor may flag a slot unavailable without sending one. When a
    // body IS supplied, @Valid still enforces its @Size constraint; the service null-checks it.
    @PatchMapping("/{orderId}/delivery-slot/unavailable")
    public ResponseEntity<CompanyOrderResponse> markDeliverySlotUnavailable(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) @Valid MarkSlotUnavailableRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.markSlotUnavailable(companyId, orderId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CompanyOrderResponse> cancelOrder(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.cancelOrderByCompany(companyId, orderId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/return")
    public ResponseEntity<CompanyOrderResponse> initiateReturn(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId,
            @RequestBody @Valid ReturnOrderRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.initiateReturn(companyId, orderId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{orderId}/returns")
    public ResponseEntity<List<ReturnResponse>> getCompanyReturnsByOrder(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(returnService.getCompanyReturnsByOrder(orderId, companyId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/returns")
    public ResponseEntity<ReturnResponse> merchantInitiateReturn(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId,
            @RequestBody @Valid MerchantInitiateReturnRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(returnService.merchantInitiateReturn(orderId, companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // -------------------------------------------------------------------------
    // Risk review queue
    // -------------------------------------------------------------------------

    @GetMapping("/risk-review")
    public ResponseEntity<PagedResponse<RiskReviewResponse>> listRiskReviews(
            @PathVariable UUID companyId,
            @RequestParam(required = false) RiskReviewStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.listRiskReviews(companyId, userId, status, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{orderId}/risk")
    public ResponseEntity<RiskAssessmentResponse> getOrderRisk(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.getOrderRisk(companyId, orderId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/risk/approve")
    public ResponseEntity<OrderResponse> approveRiskReview(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) @Valid RiskDecisionRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.approveRiskReview(companyId, orderId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/risk/reject")
    public ResponseEntity<OrderResponse> rejectRiskReview(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) @Valid RiskDecisionRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.rejectRiskReview(companyId, orderId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{orderId}/assign-driver")
    public ResponseEntity<Void> assignDriver(
            @PathVariable UUID companyId,
            @PathVariable UUID orderId,
            @Valid @RequestBody AssignDriverRequest request) {
        try {
            UUID ownerId = resolveUserId();
            deliveryService.assignDriver(companyId, orderId, request.driverUserId(), ownerId);
            return ResponseEntity.ok().build();
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
