package backend.services.intf.subscriptions;

import java.util.UUID;
import backend.dtos.requests.subscription.CreateSubscriptionRequest;
import backend.dtos.requests.subscription.UpdateSubscriptionRequest;
import backend.dtos.responses.subscription.SavedPaymentMethodResponse;
import backend.dtos.responses.subscription.SetupIntentResponse;
import backend.dtos.responses.subscription.SubscriptionResponse;

import java.util.List;

/**
 * Customer-facing recurring-order lifecycle. Stripe is the source of truth for
 * billing; this service mirrors local state and translates customer actions
 * (pause / skip / cancel / change) into Stripe API calls.
 */
public interface SubscriptionService {

    // -------------------------------------------------------------------------
    // Saved payment methods (cards reusable across subscription renewals)
    // -------------------------------------------------------------------------

    /** Creates a SetupIntent the client uses to save a card for off-session reuse. */
    SetupIntentResponse createSetupIntent(UUID userId);

    List<SavedPaymentMethodResponse> listPaymentMethods(UUID userId);

    void detachPaymentMethod(UUID userId, UUID savedPaymentMethodId);

    // -------------------------------------------------------------------------
    // Subscription lifecycle
    // -------------------------------------------------------------------------

    SubscriptionResponse create(UUID userId, CreateSubscriptionRequest request);

    SubscriptionResponse get(UUID userId, UUID subscriptionId);

    List<SubscriptionResponse> listForUser(UUID userId);

    /** Edits quantity, interval, or swaps the product. Any non-null field is applied. */
    SubscriptionResponse update(UUID userId, UUID subscriptionId, UpdateSubscriptionRequest request);

    SubscriptionResponse pause(UUID userId, UUID subscriptionId);

    SubscriptionResponse resume(UUID userId, UUID subscriptionId);

    /** Skips the next scheduled charge by advancing the billing anchor by one cycle. */
    SubscriptionResponse skipNext(UUID userId, UUID subscriptionId);

    /** Cancels the subscription. {@code atPeriodEnd=true} keeps service through the current cycle. */
    SubscriptionResponse cancel(UUID userId, UUID subscriptionId, boolean atPeriodEnd);

    // -------------------------------------------------------------------------
    // Stripe webhook handlers
    // -------------------------------------------------------------------------

    /** Re-syncs status, period, and cancellation flags from Stripe. */
    void handleSubscriptionUpdated(String stripeSubscriptionId);

    /** Spawns a fulfillment Order for a paid invoice (idempotent on invoice ID). */
    void handleInvoicePaid(String stripeInvoiceId, String stripeSubscriptionId, long amountPaidCents);

    /** Marks the subscription PAST_DUE and notifies the customer. */
    void handleInvoicePaymentFailed(String stripeInvoiceId, String stripeSubscriptionId);

    /** Persists a saved payment method when a SetupIntent succeeds. */
    void handleSetupIntentSucceeded(String stripeCustomerId, String stripePaymentMethodId);
}
