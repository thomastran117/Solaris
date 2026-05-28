package backend.controllers.impl.orders;

import java.util.List;
import java.util.UUID;
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
import backend.dtos.responses.order.OrderStatusHistoryResponse;
import backend.dtos.responses.order.TrackingEvent;
import backend.dtos.responses.return_.ReturnResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.InternalServerErrorException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.OrderStatus;
import backend.services.impl.orders.OrderSseService;
import backend.services.intf.CacheService;
import backend.services.intf.IdempotencyService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.orders.TrackingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.orders.ReplacementOrderService;
import backend.services.intf.returns.ReturnService;
import backend.services.intf.subscriptions.SubscriptionService;
import backend.services.intf.vendors.VendorOnboardingService;
import backend.services.intf.payments.VendorPayoutService;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/orders")
public class OrderController {

    /** Short window during which a concurrent retry with the same key is rejected as a duplicate. */
    private static final long IDEMPOTENCY_CLAIM_TTL_SECONDS = 60;

    /**
     * How long a successfully processed Stripe {@code event.id} remains in the dedup
     * cache. Stripe documents a 30-day retry window for at-least-once delivery, so
     * 31 days keeps us a margin above that.
     */
    private static final long STRIPE_EVENT_DEDUP_TTL_SECONDS = 31L * 24 * 60 * 60;

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ReturnService returnService;
    private final ReplacementOrderService replacementOrderService;
    private final SubscriptionService subscriptionService;
    private final VendorPayoutService vendorPayoutService;
    private final VendorOnboardingService vendorOnboardingService;
    private final IdempotencyService idempotencyService;
    private final CacheService cacheService;
    private final TrackingService trackingService;
    private final OrderSseService orderSseService;
    private final ObjectMapper objectMapper;

    private static final long TRACKING_CACHE_TTL_SECONDS = 60;

    public OrderController(OrderService orderService, PaymentService paymentService,
                           ReturnService returnService, ReplacementOrderService replacementOrderService,
                           SubscriptionService subscriptionService,
                           VendorPayoutService vendorPayoutService,
                           VendorOnboardingService vendorOnboardingService,
                           IdempotencyService idempotencyService,
                           CacheService cacheService,
                           TrackingService trackingService,
                           OrderSseService orderSseService,
                           ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.returnService = returnService;
        this.replacementOrderService = replacementOrderService;
        this.subscriptionService = subscriptionService;
        this.vendorPayoutService = vendorPayoutService;
        this.vendorOnboardingService = vendorOnboardingService;
        this.idempotencyService = idempotencyService;
        this.cacheService = cacheService;
        this.trackingService = trackingService;
        this.orderSseService = orderSseService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        try {
            UUID userId = resolveUserId();

            // Idempotency: if the caller already created an order under this key, return
            // it directly so a retried POST doesn't charge the customer twice. We only
            // engage when a key is provided (back-compat: existing clients still work).
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<UUID> existing = idempotencyService.lookup("order:create", userId, idempotencyKey);
                if (existing.isPresent()) {
                    OrderResponse prior = orderService.getOrder(existing.get(), userId);
                    return ResponseEntity.status(HttpStatus.OK).body(prior);
                }
                // Claim the key so a near-simultaneous duplicate retry doesn't slip
                // through while the first request is still creating the order.
                boolean claimed = idempotencyService.claim("order:create", userId, idempotencyKey,
                        IDEMPOTENCY_CLAIM_TTL_SECONDS);
                if (!claimed) {
                    throw new ConflictException("A request with this Idempotency-Key is already in flight");
                }
            }

            OrderResponse created = orderService.createOrder(userId, request);

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.store("order:create", userId, idempotencyKey, created.getId());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
            @RequestParam(defaultValue = "createdAt") @jakarta.validation.constraints.Pattern(regexp = "^[a-zA-Z.]+$", message = "Invalid sort field") String sort,
            @RequestParam(defaultValue = "desc") @jakarta.validation.constraints.Pattern(regexp = "^(?i)(asc|desc)$", message = "Direction must be asc or desc") String direction) {
        try {
            UUID userId = resolveUserId();
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
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.getLatestOrder(userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    @RequireAuth
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.getOrder(id, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}/history")
    @RequireAuth
    public ResponseEntity<List<OrderStatusHistoryResponse>> getOrderHistory(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.getOrderHistory(id, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/cancel")
    @RequireAuth
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(orderService.cancelOrder(id, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/reorder")
    @RequireAuth
    public ResponseEntity<OrderResponse> reorderOrder(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
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
        // No try/catch wrapping the whole body: GlobalExceptionHandler differentiates
        // AppHttpException (4xx, e.g. bad signature) from Exception (500). A blanket
        // catch here was turning malformed payloads into 500s, which makes Stripe
        // retry forever instead of giving up after a 4xx.
        PaymentService.WebhookEvent event = paymentService.constructWebhookEvent(payload, sigHeader);

        // R2-C3: Stripe at-least-once delivery means the same event id can arrive twice.
        // SETNX-style claim — if another delivery (or another replica) already started
        // processing this event id, we return 200 OK so Stripe stops retrying without
        // running the side-effects a second time.
        if (event.eventId() != null && !event.eventId().isBlank()) {
            String dedupKey = "stripe:event:" + event.eventId();
            boolean firstDelivery = cacheService.tryLock(dedupKey, "1", STRIPE_EVENT_DEDUP_TTL_SECONDS);
            if (!firstDelivery) {
                return ResponseEntity.ok().build();
            }
        }

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
    }

    @PostMapping("/support/orders/{orderId}/partial-refund")
    @RequireAuth(roles = {"SUPPORT", "MODERATOR", "ADMIN"})
    public ResponseEntity<ReturnResponse> issuePartialRefund(
            @PathVariable UUID orderId,
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
            @PathVariable UUID orderId,
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

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireAuth
    public SseEmitter streamOrder(@PathVariable UUID id) {
        UUID userId = resolveUserId();
        OrderResponse order = orderService.getOrder(id, userId);

        SseEmitter emitter = new SseEmitter(300_000L);
        orderSseService.register(id, emitter);
        emitter.onCompletion(() -> orderSseService.deregister(id, emitter));
        emitter.onTimeout(() -> orderSseService.deregister(id, emitter));
        emitter.onError(t -> orderSseService.deregister(id, emitter));

        try {
            emitter.send(SseEmitter.event().name("snapshot").data(objectMapper.writeValueAsString(order)));
        } catch (Exception e) {
            orderSseService.deregister(id, emitter);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @GetMapping("/{id}/driver-location")
    @RequireAuth
    public ResponseEntity<Object> getDriverLocation(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
            orderService.getOrder(id, userId);
            String cached = cacheService.get("delivery:location:" + id);
            if (cached == null) {
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.ok(objectMapper.readValue(cached, Object.class));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}/tracking")
    @RequireAuth
    public ResponseEntity<List<TrackingEvent>> getTracking(@PathVariable UUID id) {
        try {
            UUID userId = resolveUserId();
            OrderResponse order = orderService.getOrder(id, userId);
            if (order.getTrackingNumber() == null) {
                throw new ResourceNotFoundException("No tracking information available for this order yet");
            }
            String cacheKey = "order:tracking:" + order.getTrackingNumber();
            String cached = cacheService.get(cacheKey);
            if (cached != null) {
                try {
                    List<TrackingEvent> events = objectMapper.readValue(cached,
                            new TypeReference<>() {});
                    return ResponseEntity.ok(events);
                } catch (Exception ignored) {}
            }
            List<TrackingEvent> events = trackingService.getTrackingEvents(
                    order.getTrackingNumber(), order.getCarrier());
            try {
                cacheService.set(cacheKey, objectMapper.writeValueAsString(events),
                        TRACKING_CACHE_TTL_SECONDS);
            } catch (Exception ignored) {}
            return ResponseEntity.ok(events);
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
