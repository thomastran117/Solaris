package backend.services.intf.promotions;

import backend.dtos.requests.loyalty.AdjustPointsRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyPolicyRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyTierRequest;
import backend.dtos.requests.loyalty.IssueBonusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.loyalty.LoyaltyAccountResponse;
import backend.dtos.responses.loyalty.LoyaltyPolicyResponse;
import backend.dtos.responses.loyalty.LoyaltyRedemptionQuoteResponse;
import backend.dtos.responses.loyalty.LoyaltyTierResponse;
import backend.dtos.responses.loyalty.LoyaltyTransactionResponse;
import backend.models.core.Order;

import java.util.List;

public interface LoyaltyService {

    // -------------------------------------------------------------------------
    // Customer self-service
    // -------------------------------------------------------------------------

    LoyaltyAccountResponse getAccount(long userId, long companyId);

    PagedResponse<LoyaltyTransactionResponse> getTransactions(long userId, long companyId, int page, int size);

    LoyaltyRedemptionQuoteResponse getRedemptionQuote(long userId, long companyId, int pointsToRedeem);

    // -------------------------------------------------------------------------
    // Order integration (called from OrderServiceImpl)
    // -------------------------------------------------------------------------

    /**
     * Deducts pointsToRedeem from the customer's account and returns the monetary
     * discount in cents. Called during createOrder, before PaymentIntent creation.
     * Caller must call restoreRedeemedPoints on failure.
     */
    long applyRedemption(long userId, long companyId, long orderId, int pointsToRedeem);

    /**
     * Awards points (and optionally cashback credits) for a completed order.
     * Called from handlePaymentSuccess and createRenewalOrder paths.
     */
    void recordOrderEarn(Order order, long companyId);

    /**
     * Reverses a REDEEM_ORDER transaction for the given order.
     * Safe to call even if no redemption exists (idempotent).
     */
    void restoreRedeemedPoints(long orderId);

    /**
     * Claws back loyalty points earned from a now-refunded order. Proportional to the
     * refund ratio ({@code refundedAmountCents / orderTotalCents}). Safe to call
     * multiple times — incremental partial refunds reverse only the new delta beyond
     * what's already been reversed. No-op if the order never earned points.
     */
    void clawbackEarnedPoints(long orderId, long refundedAmountCents, long orderTotalCents);

    // -------------------------------------------------------------------------
    // Operator actions
    // -------------------------------------------------------------------------

    LoyaltyTransactionResponse issueBonus(long companyId, long ownerId, IssueBonusRequest request);

    LoyaltyTransactionResponse adjustPoints(long accountId, long companyId, long ownerId, AdjustPointsRequest request);

    // -------------------------------------------------------------------------
    // Policy & tier management (operator)
    // -------------------------------------------------------------------------

    LoyaltyPolicyResponse createOrUpdatePolicy(long companyId, long ownerId, CreateLoyaltyPolicyRequest request);

    LoyaltyPolicyResponse getPolicy(long companyId);

    LoyaltyTierResponse createTier(long companyId, long ownerId, CreateLoyaltyTierRequest request);

    LoyaltyTierResponse updateTier(long tierId, long companyId, long ownerId, CreateLoyaltyTierRequest request);

    List<LoyaltyTierResponse> listTiers(long companyId);
}
