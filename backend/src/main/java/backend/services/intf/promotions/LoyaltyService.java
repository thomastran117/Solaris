package backend.services.intf.promotions;

import java.util.UUID;
import backend.dtos.requests.loyalty.AdjustPointsRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyPolicyRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyTierRequest;
import backend.dtos.requests.loyalty.IssueBonusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.loyalty.LoyaltyAccountResponse;
import backend.dtos.responses.loyalty.LoyaltyExpiryWarningResponse;
import backend.dtos.responses.loyalty.LoyaltyPolicyResponse;
import backend.dtos.responses.loyalty.LoyaltyRedemptionQuoteResponse;
import backend.dtos.responses.loyalty.LoyaltyReferralResponse;
import backend.dtos.responses.loyalty.LoyaltyTierResponse;
import backend.dtos.responses.loyalty.LoyaltyTransactionResponse;
import backend.models.core.Order;

import java.util.List;

public interface LoyaltyService {

    // -------------------------------------------------------------------------
    // Customer self-service
    // -------------------------------------------------------------------------

    LoyaltyAccountResponse getAccount(UUID userId, UUID companyId);

    LoyaltyExpiryWarningResponse getExpiryWarning(UUID userId, UUID companyId, int days);

    LoyaltyReferralResponse getReferralInfo(UUID userId, UUID companyId);

    void applyReferralCode(UUID userId, UUID companyId, String code);

    PagedResponse<LoyaltyTransactionResponse> getTransactions(UUID userId, UUID companyId, int page, int size);

    LoyaltyRedemptionQuoteResponse getRedemptionQuote(UUID userId, UUID companyId, int pointsToRedeem);

    // -------------------------------------------------------------------------
    // Order integration (called from OrderServiceImpl)
    // -------------------------------------------------------------------------

    /**
     * Deducts pointsToRedeem from the customer's account and returns the monetary
     * discount in cents. Called during createOrder, before PaymentIntent creation.
     * Caller must call restoreRedeemedPoints on failure.
     */
    long applyRedemption(UUID userId, UUID companyId, UUID orderId, int pointsToRedeem);

    /**
     * Awards points (and optionally cashback credits) for a completed order.
     * Called from handlePaymentSuccess and createRenewalOrder paths.
     */
    void recordOrderEarn(Order order, UUID companyId);

    /**
     * Reverses a REDEEM_ORDER transaction for the given order.
     * Safe to call even if no redemption exists (idempotent).
     */
    void restoreRedeemedPoints(UUID orderId);

    /**
     * Claws back loyalty points earned from a now-refunded order. Proportional to the
     * refund ratio ({@code refundedAmountCents / orderTotalCents}). Safe to call
     * multiple times — incremental partial refunds reverse only the new delta beyond
     * what's already been reversed. No-op if the order never earned points.
     */
    void clawbackEarnedPoints(UUID orderId, long refundedAmountCents, long orderTotalCents);

    // -------------------------------------------------------------------------
    // Operator actions
    // -------------------------------------------------------------------------

    LoyaltyTransactionResponse issueBonus(UUID companyId, UUID ownerId, IssueBonusRequest request);

    LoyaltyTransactionResponse adjustPoints(UUID accountId, UUID companyId, UUID ownerId, AdjustPointsRequest request);

    // -------------------------------------------------------------------------
    // Policy & tier management (operator)
    // -------------------------------------------------------------------------

    LoyaltyPolicyResponse createOrUpdatePolicy(UUID companyId, UUID ownerId, CreateLoyaltyPolicyRequest request);

    LoyaltyPolicyResponse getPolicy(UUID companyId);

    LoyaltyTierResponse createTier(UUID companyId, UUID ownerId, CreateLoyaltyTierRequest request);

    LoyaltyTierResponse updateTier(UUID tierId, UUID companyId, UUID ownerId, CreateLoyaltyTierRequest request);

    List<LoyaltyTierResponse> listTiers(UUID companyId);
}
