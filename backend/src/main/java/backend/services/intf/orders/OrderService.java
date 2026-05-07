package backend.services.intf.orders;

import backend.dtos.requests.order.CreateOrderRequest;
import backend.dtos.requests.order.ReturnOrderRequest;
import backend.dtos.requests.order.ShipOrderRequest;
import backend.dtos.requests.risk.RiskDecisionRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CompanyOrderResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.risk.RiskAssessmentResponse;
import backend.dtos.responses.risk.RiskReviewResponse;
import backend.models.core.Order;
import backend.models.core.Subscription;
import backend.models.enums.OrderStatus;
import backend.models.enums.RiskReviewStatus;

public interface OrderService {
    OrderResponse createOrder(long userId, CreateOrderRequest request);
    OrderResponse getOrder(long orderId, long userId);
    OrderResponse getLatestOrder(long userId);
    PagedResponse<OrderResponse> getOrders(long userId, OrderStatus status, int page, int size, String sort, String direction);
    OrderResponse reorderOrder(long orderId, long userId);
    OrderResponse cancelOrder(long orderId, long userId);
    void handlePaymentSuccess(String paymentIntentId);
    void handlePaymentFailure(String paymentIntentId);
    PagedResponse<CompanyOrderResponse> getCompanyOrders(long companyId, long ownerId, OrderStatus status, int page, int size);
    CompanyOrderResponse getCompanyOrder(long companyId, long orderId, long ownerId);

    void fulfillPendingBackorders(long productId, Long variantId, int availableQty, Long fulfillmentLocationId);

    // -------------------------------------------------------------------------
    // Merchant fulfillment transitions
    // -------------------------------------------------------------------------

    /** Transitions PAID order to PACKED — marks all PENDING items as PACKED. */
    CompanyOrderResponse markAsPacked(long companyId, long orderId, long ownerId);

    /** Transitions PACKED (or PARTIALLY_FULFILLED) order items to SHIPPED; records tracking info. */
    CompanyOrderResponse markAsShipped(long companyId, long orderId, long ownerId, ShipOrderRequest request);

    /** Transitions SHIPPED (or PARTIALLY_FULFILLED) order to DELIVERED. */
    CompanyOrderResponse markAsDelivered(long companyId, long orderId, long ownerId);

    /** Processes a return for a DELIVERED (or SHIPPED) order — optionally restocks and refunds. */
    CompanyOrderResponse initiateReturn(long companyId, long orderId, long ownerId, ReturnOrderRequest request);

    // -------------------------------------------------------------------------
    // Merchant risk-review queue
    // -------------------------------------------------------------------------

    /** Paginated list of risk-review rows for the merchant. Defaults to PENDING when status is null. */
    PagedResponse<RiskReviewResponse> listRiskReviews(long companyId, long ownerId,
                                                      RiskReviewStatus status, int page, int size);

    /** Returns the latest persisted risk assessment for an order, scoped to the merchant's company. */
    RiskAssessmentResponse getOrderRisk(long companyId, long orderId, long ownerId);

    /** Approves an UNDER_REVIEW order — triggers the skipped Stripe payment-intent creation. */
    OrderResponse approveRiskReview(long companyId, long orderId, long ownerId, RiskDecisionRequest req);

    /** Rejects an UNDER_REVIEW order — delegates to cancelOrder to release reservation and restore stock. */
    OrderResponse rejectRiskReview(long companyId, long orderId, long ownerId, RiskDecisionRequest req);

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
