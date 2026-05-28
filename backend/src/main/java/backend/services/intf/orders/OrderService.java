package backend.services.intf.orders;

import java.util.UUID;
import backend.dtos.requests.order.CreateOrderRequest;
import backend.dtos.requests.order.ReturnOrderRequest;
import backend.dtos.requests.order.ShipOrderRequest;
import backend.dtos.requests.risk.RiskDecisionRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CompanyOrderResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.order.OrderStatusHistoryResponse;
import backend.dtos.responses.risk.RiskAssessmentResponse;
import backend.dtos.responses.risk.RiskReviewResponse;
import backend.models.core.Order;
import backend.models.core.Subscription;
import backend.models.enums.OrderStatus;
import backend.models.enums.RiskReviewStatus;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(UUID userId, CreateOrderRequest request);
    OrderResponse getOrder(UUID orderId, UUID userId);
    OrderResponse getLatestOrder(UUID userId);
    PagedResponse<OrderResponse> getOrders(UUID userId, OrderStatus status, int page, int size, String sort, String direction);
    OrderResponse reorderOrder(UUID orderId, UUID userId);
    OrderResponse cancelOrder(UUID orderId, UUID userId);
    CompanyOrderResponse cancelOrderByCompany(UUID companyId, UUID orderId, UUID ownerId);
    void handlePaymentSuccess(String paymentIntentId);
    void handlePaymentFailure(String paymentIntentId);
    PagedResponse<CompanyOrderResponse> getCompanyOrders(UUID companyId, UUID ownerId, OrderStatus status, int page, int size);
    CompanyOrderResponse getCompanyOrder(UUID companyId, UUID orderId, UUID ownerId);

    void fulfillPendingBackorders(UUID productId, UUID variantId, int availableQty, UUID fulfillmentLocationId);

    // -------------------------------------------------------------------------
    // Merchant fulfillment transitions
    // -------------------------------------------------------------------------

    /** Transitions PAID order to PACKED — marks all PENDING items as PACKED. */
    CompanyOrderResponse markAsPacked(UUID companyId, UUID orderId, UUID ownerId);

    /** Transitions PACKED (or PARTIALLY_FULFILLED) order items to SHIPPED; records tracking info. */
    CompanyOrderResponse markAsShipped(UUID companyId, UUID orderId, UUID ownerId, ShipOrderRequest request);

    /**
     * PICKUP orders only — transitions PACKED/PENDING items to PICKUP_READY and records pickupReadyAt.
     * Order-level status stays PACKED; the customer is notified that their order is ready to collect.
     */
    CompanyOrderResponse markAsPickupReady(UUID companyId, UUID orderId, UUID ownerId);

    /** Transitions SHIPPED (or PARTIALLY_FULFILLED) order to DELIVERED. Also accepts PACKED for PICKUP orders. */
    CompanyOrderResponse markAsDelivered(UUID companyId, UUID orderId, UUID ownerId);

    /** Processes a return for a DELIVERED (or SHIPPED) order — optionally restocks and refunds. */
    CompanyOrderResponse initiateReturn(UUID companyId, UUID orderId, UUID ownerId, ReturnOrderRequest request);

    // -------------------------------------------------------------------------
    // Merchant risk-review queue
    // -------------------------------------------------------------------------

    /** Paginated list of risk-review rows for the merchant. Defaults to PENDING when status is null. */
    PagedResponse<RiskReviewResponse> listRiskReviews(UUID companyId, UUID ownerId,
                                                      RiskReviewStatus status, int page, int size);

    /** Returns the latest persisted risk assessment for an order, scoped to the merchant's company. */
    RiskAssessmentResponse getOrderRisk(UUID companyId, UUID orderId, UUID ownerId);

    /** Approves an UNDER_REVIEW order — triggers the skipped Stripe payment-intent creation. */
    OrderResponse approveRiskReview(UUID companyId, UUID orderId, UUID ownerId, RiskDecisionRequest req);

    /** Rejects an UNDER_REVIEW order — delegates to cancelOrder to release reservation and restore stock. */
    OrderResponse rejectRiskReview(UUID companyId, UUID orderId, UUID ownerId, RiskDecisionRequest req);

    /** Called by the Aftership webhook when tag == "Delivered". Looks up order by tracking number and transitions to DELIVERED. No-ops if not found or already delivered. */
    void autoMarkDeliveredByTracking(String trackingNumber);

    /** Returns the full status/event history for an order, ordered by occurredAt ASC. Validates that userId owns the order. */
    List<OrderStatusHistoryResponse> getOrderHistory(UUID orderId, UUID userId);

    // -------------------------------------------------------------------------
    // Subscription renewals
    // -------------------------------------------------------------------------

    /**
     * Creates a fulfillment Order from a paid subscription invoice. Idempotent on
     * {@code stripeInvoiceId} — returns the existing order if one already exists for
     * this invoice. Skips PaymentIntent creation (Stripe already charged the customer
     * via the subscription invoice) and starts the order at PAID. Inventory is
     * decremented; if stock is unavailable and backorder is disabled, the resulting
     * order item is marked BACKORDERED rather than failing the whole renewal.
     */
    Order createRenewalOrder(Subscription subscription, String stripeInvoiceId, long amountPaidCents);
}
