package backend.services.intf.returns;

import java.util.UUID;
import backend.dtos.requests.return_.BuyerInitiateReturnRequest;
import backend.dtos.requests.return_.InspectReturnRequest;
import backend.dtos.requests.return_.MerchantApproveReturnRequest;
import backend.dtos.requests.return_.MerchantInitiateReturnRequest;
import backend.dtos.requests.return_.MerchantRejectReturnRequest;
import backend.dtos.responses.return_.ReturnResponse;

import java.util.List;

public interface ReturnService {

    // -------------------------------------------------------------------------
    // Buyer-facing
    // -------------------------------------------------------------------------

    /**
     * Buyer submits a return request. Order must be DELIVERED.
     * Return is created in REQUESTED status for merchant review.
     */
    ReturnResponse requestReturn(UUID orderId, UUID buyerUserId, BuyerInitiateReturnRequest request);

    /** Buyer views all return requests for a specific order. */
    List<ReturnResponse> getReturnsByOrder(UUID orderId, UUID buyerUserId);

    // -------------------------------------------------------------------------
    // Merchant-facing
    // -------------------------------------------------------------------------

    /** Merchant views all return requests for a specific order scoped to their company's items. */
    List<ReturnResponse> getCompanyReturnsByOrder(UUID orderId, UUID companyId, UUID ownerId);

    /** Merchant retrieves a single return scoped to their company. */
    ReturnResponse getCompanyReturn(UUID returnId, UUID companyId, UUID ownerId);

    /**
     * Merchant approves a REQUESTED return: marks items RETURNED and issues refund.
     * Transitions Return to APPROVED (awaiting physical inspection). Stock is NOT
     * restored here — that happens in inspectReturn() once items are received.
     * Transitions to FAILED if the Stripe refund call errors.
     */
    ReturnResponse approveReturn(UUID returnId, UUID companyId, UUID ownerId, MerchantApproveReturnRequest request);

    /**
     * Merchant records per-item condition after physical inspection of returned goods
     * and decides whether to restock each item. Transitions Return from APPROVED to COMPLETED.
     */
    ReturnResponse inspectReturn(UUID returnId, UUID companyId, UUID ownerId, InspectReturnRequest request);

    /**
     * Merchant rejects a REQUESTED return. Transitions to REJECTED.
     * No stock or refund changes are made.
     */
    ReturnResponse rejectReturn(UUID returnId, UUID companyId, UUID ownerId, MerchantRejectReturnRequest request);

    /**
     * Merchant directly initiates a return without a prior buyer request.
     * Creates the Return entity and immediately processes stock + refund in a single transaction.
     * Valid for orders in DELIVERED or SHIPPED status.
     */
    ReturnResponse merchantInitiateReturn(UUID orderId, UUID companyId, UUID ownerId, MerchantInitiateReturnRequest request);

    // -------------------------------------------------------------------------
    // Support operations
    // -------------------------------------------------------------------------

    /**
     * Staff-initiated partial refund without requiring item-level granularity.
     * Creates a merchant-initiated Return for the specified amount and immediately
     * issues the Stripe refund. Records the staff actor in merchantNote.
     *
     * @param orderId       the order to refund against
     * @param amountCents   refund amount in cents; must be > 0
     * @param reason        optional staff-supplied reason (stored in merchantNote)
     * @param actorUserId   staff member authorising the refund
     * @return the created Return record
     */
    ReturnResponse issuePartialRefund(UUID orderId, long amountCents, String reason, UUID actorUserId);

    // -------------------------------------------------------------------------
    // Webhook
    // -------------------------------------------------------------------------

    /**
     * Called when Stripe fires a charge.refunded or refund.updated event.
     * Looks up the Return by stripeRefundId, updates RefundStatus, syncs
     * order.refundedAmountCents, and recomputes OrderStatus (may tip to REFUNDED).
     */
    void handleRefundWebhookEvent(String stripeRefundId, String stripeStatus, long amountCents);
}
