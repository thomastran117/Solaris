package backend.controllers.impl.orders;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.issue.ResolveWithReplacementRequest;
import backend.dtos.requests.order.CreateOrderRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.return_.ReturnResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.OrderStatus;
import backend.services.intf.orders.OrderService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.orders.ReplacementOrderService;
import backend.services.intf.returns.ReturnService;
import backend.services.intf.subscriptions.SubscriptionService;
import backend.services.intf.vendors.VendorOnboardingService;
import backend.services.intf.payments.VendorPayoutService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ReturnService returnService;
    private final ReplacementOrderService replacementOrderService;
    private final SubscriptionService subscriptionService;
    private final VendorPayoutService vendorPayoutService;
    private final VendorOnboardingService vendorOnboardingService;

    public OrderController(OrderService orderService, PaymentService paymentService,
                           ReturnService returnService, ReplacementOrderService replacementOrderService,
                           SubscriptionService subscriptionService,
                           VendorPayoutService vendorPayoutService,
                           VendorOnboardingService vendorOnboardingService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.returnService = returnService;
        this.replacementOrderService = replacementOrderService;
        this.subscriptionService = subscriptionService;
        this.vendorPayoutService = vendorPayoutService;
        this.vendorOnboardingService = vendorOnboardingService;
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<PagedResponse<OrderResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(orderService.getOrders(userId, status, page, size, sort, direction));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/latest")
    @RequireAuth
    public ResponseEntity<OrderResponse> getLatestOrder() {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(orderService.getLatestOrder(userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    @RequireAuth
    public ResponseEntity<OrderResponse> getOrder(@PathVariable long id) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(orderService.getOrder(id, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/cancel")
    @RequireAuth
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable long id) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(orderService.cancelOrder(id, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/reorder")
    @RequireAuth
    public ResponseEntity<OrderResponse> reorderOrder(@PathVariable long id) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED).body(orderService.reorderOrder(id, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/webhook/stripe")
    public ResponseEntity<Void> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            PaymentService.WebhookEvent event = paymentService.constructWebhookEvent(payload, sigHeader);

            switch (event.type()) {
                case "payment_intent.succeeded"      -> orderService.handlePaymentSuccess(event.objectId());
                case "payment_intent.payment_failed" -> orderService.handlePaymentFailure(event.objectId());
                case "charge.refunded" -> {
                    String refundId = event.metadata().get("refundId");
                    if (refundId != null && !refundId.isBlank()) {
                        returnService.handleRefundWebhookEvent(
                                refundId,
                                event.metadata().get("refundStatus"),
                                Long.parseLong(event.metadata().getOrDefault("refundAmountCents", "0")));
                    }
                }
                case "refund.updated" -> {
                    if (event.objectId() != null) {
                        returnService.handleRefundWebhookEvent(
                                event.objectId(),
                                event.metadata().getOrDefault("refundStatus", "pending"),
                                Long.parseLong(event.metadata().getOrDefault("refundAmountCents", "0")));
                    }
                }
                case "customer.subscription.updated", "customer.subscription.deleted" -> {
                    if (event.objectId() != null) {
                        subscriptionService.handleSubscriptionUpdated(event.objectId());
                    }
                }
                case "invoice.paid" -> {
                    String subId = event.metadata().get("subscriptionId");
                    long amountPaid = Long.parseLong(event.metadata().getOrDefault("amountPaidCents", "0"));
                    if (subId != null && event.objectId() != null) {
                        subscriptionService.handleInvoicePaid(event.objectId(), subId, amountPaid);
                    }
                }
                case "invoice.payment_failed" -> {
                    String subId = event.metadata().get("subscriptionId");
                    if (subId != null && event.objectId() != null) {
                        subscriptionService.handleInvoicePaymentFailed(event.objectId(), subId);
                    }
                }
                case "setup_intent.succeeded" -> {
                    String customerId = event.metadata().get("customerId");
                    String paymentMethodId = event.metadata().get("paymentMethodId");
                    if (customerId != null && paymentMethodId != null) {
                        subscriptionService.handleSetupIntentSucceeded(customerId, paymentMethodId);
                    }
                }
                case "account.updated" -> {
                    if (event.objectId() != null) {
                        vendorOnboardingService.syncStripeConnectStatus(event.objectId());
                    }
                }
                case "transfer.paid" -> {
                    if (event.objectId() != null) {
                        vendorPayoutService.handleTransferPaid(event.objectId());
                    }
                }
                case "transfer.failed" -> {
                    if (event.objectId() != null) {
                        String reason = event.metadata().getOrDefault("failureReason", "Transfer failed");
                        vendorPayoutService.handleTransferFailed(event.objectId(), reason);
                    }
                }
                default -> { }
            }

            return ResponseEntity.ok().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/orders/{orderId}/partial-refund")
    @RequireAuth(roles = {"SUPPORT", "MODERATOR", "ADMIN"})
    public ResponseEntity<ReturnResponse> issuePartialRefund(
            @PathVariable long orderId,
            @RequestParam long amountCents,
            @RequestParam(required = false) String reason) {
        try {
            return ResponseEntity.ok(returnService.issuePartialRefund(orderId, amountCents, reason, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/support/orders/{orderId}/replacement")
    @RequireAuth(roles = {"SUPPORT", "MODERATOR", "ADMIN"})
    public ResponseEntity<OrderResponse> createReplacement(
            @PathVariable long orderId,
            @Valid @RequestBody ResolveWithReplacementRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(replacementOrderService.createReplacement(orderId, request, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
