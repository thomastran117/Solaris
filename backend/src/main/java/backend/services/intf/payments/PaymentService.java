package backend.services.intf.payments;

import backend.models.enums.BillingInterval;

import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic payment service. All amounts are in the smallest currency unit (e.g. cents for USD).
 * Implementations are responsible for mapping provider-specific errors to {@link backend.exceptions.http.PaymentException}
 * or other {@link backend.exceptions.http.AppHttpException} subtypes.
 */
public interface PaymentService {

    /**
     * A created or retrieved payment intent.
     *
     * @param id           provider payment intent ID
     * @param clientSecret secret passed to the client-side SDK to confirm payment
     * @param amountInCents amount in the smallest currency unit
     * @param currency     ISO 4217 lowercase currency code (e.g. "usd")
     * @param status       provider status string (e.g. "requires_payment_method", "succeeded")
     * @param customerId   optional provider customer ID attached to this intent
     */
    record PaymentIntentResult(
            String id,
            String clientSecret,
            long amountInCents,
            String currency,
            String status,
            String customerId
    ) {}

    /**
     * A created refund.
     *
     * @param id              provider refund ID
     * @param amountInCents   amount refunded in smallest currency unit
     * @param currency        ISO 4217 lowercase currency code
     * @param status          provider status string (e.g. "succeeded", "pending")
     * @param paymentIntentId the payment intent that was refunded
     */
    record RefundResult(
            String id,
            long amountInCents,
            String currency,
            String status,
            String paymentIntentId
    ) {}

    /**
     * A created or retrieved customer.
     *
     * @param id    provider customer ID
     * @param email customer email
     * @param name  customer display name
     */
    record CustomerResult(
            String id,
            String email,
            String name
    ) {}

    /**
     * A parsed and validated inbound webhook event.
     *
     * @param type       provider event type (e.g. "payment_intent.succeeded")
     * @param objectId   ID of the object the event relates to
     * @param objectType provider object type (e.g. "payment_intent", "charge", "refund")
     * @param metadata   optional extra fields extracted from the event payload.
     *                   For {@code charge.refunded}: {@code refundId}, {@code refundStatus}, {@code refundAmountCents}.
     *                   For {@code refund.updated}: same fields (objectId is the refund ID).
     *                   Empty map for all other event types.
     */
    /**
     * @param eventId provider-issued unique id for this event delivery ({@code evt_xxx}
     *                in Stripe). Used by the controller's idempotency guard so the same
     *                event delivered twice cannot drive side-effects twice.
     */
    record WebhookEvent(
            String eventId,
            String type,
            String objectId,
            String objectType,
            Map<String, String> metadata
    ) {}

    /**
     * Creates a new payment intent.
     *
     * @param amountInCents amount to charge in smallest currency unit
     * @param currency      ISO 4217 lowercase currency code
     * @param customerId    optional provider customer ID to attach
     * @param metadata      optional key-value metadata to attach to the intent
     */
    PaymentIntentResult createPaymentIntent(long amountInCents, String currency, String customerId, Map<String, String> metadata);

    /**
     * Retrieves a payment intent by its provider ID.
     */
    PaymentIntentResult retrievePaymentIntent(String paymentIntentId);

    /**
     * Cancels a payment intent that has not yet been captured.
     */
    PaymentIntentResult cancelPaymentIntent(String paymentIntentId);

    /**
     * Issues a refund against a captured payment intent.
     *
     * @param paymentIntentId the intent to refund
     * @param amountInCents   amount to refund; pass {@code null} for a full refund
     */
    RefundResult refundPayment(String paymentIntentId, Long amountInCents);

    /**
     * Creates a customer record in the payment provider.
     *
     * @param email    customer email address
     * @param name     customer display name
     * @param metadata optional key-value metadata
     */
    CustomerResult createCustomer(String email, String name, Map<String, String> metadata);

    /**
     * Validates the webhook signature and parses the event payload.
     * Throws {@link backend.exceptions.http.BadRequestException} if the signature is invalid.
     *
     * @param payload   raw request body string
     * @param sigHeader value of the provider's signature header
     */
    WebhookEvent constructWebhookEvent(String payload, String sigHeader);

    // -------------------------------------------------------------------------
    // Subscriptions / saved payment methods
    // -------------------------------------------------------------------------

    /**
     * @param id           provider SetupIntent ID
     * @param clientSecret secret passed to the client-side SDK to confirm card setup
     * @param customerId   Stripe customer the SetupIntent is attached to
     */
    record SetupIntentResult(String id, String clientSecret, String customerId) {}

    /**
     * Snapshot of a Stripe PaymentMethod relevant for display + selection.
     */
    record PaymentMethodInfo(
            String id,
            String customerId,
            String brand,
            String last4,
            Integer expMonth,
            Integer expYear
    ) {}

    /**
     * @param id Stripe Price ID for use as a recurring subscription line item.
     */
    record PriceResult(String id, long unitAmountCents, String currency) {}

    /**
     * Snapshot of a Stripe Subscription mirrored locally.
     */
    record SubscriptionResult(
            String id,
            String customerId,
            String status,
            String latestInvoiceId,
            java.time.Instant currentPeriodStart,
            java.time.Instant currentPeriodEnd,
            String defaultPaymentMethodId,
            String firstSubscriptionItemId
    ) {}

    /**
     * Creates a SetupIntent so the customer can save a card for future off-session use
     * (subscription renewals). Caller must provide an existing Stripe customer ID.
     */
    SetupIntentResult createSetupIntent(String customerId);

    /**
     * Lists all card-type PaymentMethods attached to the given Stripe customer.
     */
    List<PaymentMethodInfo> listPaymentMethods(String customerId);

    /**
     * Retrieves a single PaymentMethod by ID.
     */
    PaymentMethodInfo retrievePaymentMethod(String paymentMethodId);

    /**
     * Detaches a PaymentMethod from its Stripe customer. Idempotent — already-detached
     * methods are silently ignored.
     */
    void detachPaymentMethod(String paymentMethodId);

    /**
     * Creates a recurring (subscription-eligible) Price in Stripe. The returned Price
     * is consumable as a subscription_item.price.
     *
     * @param productName display name for the inline Stripe Product created alongside the Price
     */
    PriceResult createRecurringPrice(long unitAmountCents, String currency,
                                     BillingInterval interval, int intervalCount,
                                     String productName, Map<String, String> metadata);

    /**
     * Creates a Stripe Subscription that will charge the given Price every cycle.
     *
     * @param defaultPaymentMethodId saved Stripe PaymentMethod to charge off-session
     */
    SubscriptionResult createSubscription(String customerId, String priceId, int quantity,
                                          String defaultPaymentMethodId,
                                          Map<String, String> metadata);

    /**
     * Updates a subscription's quantity on its first item. Uses no proration so the
     * change applies cleanly on the next cycle without surprise mid-cycle charges.
     */
    SubscriptionResult updateSubscriptionQuantity(String stripeSubscriptionId,
                                                  String stripeSubscriptionItemId,
                                                  int newQuantity);

    /**
     * Swaps the price (and optionally quantity) on a subscription's first item. Use this
     * when the customer changes interval, swaps product, or both. Uses no proration.
     */
    SubscriptionResult swapSubscriptionPrice(String stripeSubscriptionId,
                                             String stripeSubscriptionItemId,
                                             String newPriceId, int newQuantity);

    /**
     * Cancels a subscription. When {@code atPeriodEnd} is true, billing continues
     * through the current cycle and stops at the period end; otherwise cancels
     * immediately (no refund — caller decides whether to issue one).
     */
    SubscriptionResult cancelSubscription(String stripeSubscriptionId, boolean atPeriodEnd);

    /**
     * Pauses collection on a subscription. Stripe stops generating invoices until
     * {@link #resumeSubscription(String)} is called.
     */
    SubscriptionResult pauseSubscription(String stripeSubscriptionId);

    /**
     * Resumes a paused subscription. Stripe resumes the regular billing cycle.
     */
    SubscriptionResult resumeSubscription(String stripeSubscriptionId);

    /**
     * Advances the subscription's billing anchor by one cycle so the next invoice
     * is skipped. Used to implement customer "skip next shipment".
     */
    SubscriptionResult skipNextCycle(String stripeSubscriptionId,
                                     BillingInterval interval, int intervalCount);

    /**
     * Retrieves the current state of a Stripe subscription. Used by webhook handlers
     * to re-sync local state from Stripe (the source of truth for billing).
     */
    SubscriptionResult retrieveSubscription(String stripeSubscriptionId);

    // -------------------------------------------------------------------------
    // Stripe Checkout + Customer Portal (premium membership)
    // -------------------------------------------------------------------------

    /**
     * Creates a Stripe Checkout Session for a one-time or subscription purchase.
     *
     * @param customerId Stripe Customer ID to pre-fill; must not be null
     * @param priceId    Stripe Price ID to charge
     * @param successUrl URL to redirect to after successful payment
     * @param cancelUrl  URL to redirect to if the user cancels
     * @return hosted Checkout Session URL
     */
    String createCheckoutSession(String customerId, String priceId, String successUrl, String cancelUrl);

    /**
     * Creates a Stripe Customer Portal Session so the user can manage their subscription.
     *
     * @param customerId Stripe Customer ID
     * @param returnUrl  URL to redirect to when the user exits the portal
     * @return hosted Customer Portal URL
     */
    String createPortalSession(String customerId, String returnUrl);

    /**
     * Validates the webhook signature and parses a premium-endpoint webhook event using the
     * premium webhook secret (separate from the general webhook secret).
     */
    WebhookEvent constructPremiumWebhookEvent(String payload, String sigHeader);

    // -------------------------------------------------------------------------
    // Stripe Connect Express (marketplace vendor payouts)
    // -------------------------------------------------------------------------

    /**
     * A newly created or retrieved Stripe Connect Express account.
     *
     * @param accountId            Stripe Connect account ID (acct_...)
     * @param chargesEnabled       whether the account can accept charges
     * @param payoutsEnabled       whether the account can receive payouts
     * @param detailsSubmitted     whether the vendor has submitted their Stripe KYC details
     */
    record ConnectAccountResult(
            String accountId,
            boolean chargesEnabled,
            boolean payoutsEnabled,
            boolean detailsSubmitted
    ) {}

    /**
     * A Stripe-hosted AccountLink URL for the vendor to complete Connect Express onboarding.
     *
     * @param url       redirect the vendor to this URL to complete KYC / banking setup
     * @param expiresAt when the link expires (typically ~5 minutes)
     */
    record ConnectOnboardingLinkResult(String url, java.time.Instant expiresAt) {}

    /**
     * Creates a Stripe Connect Express account for a vendor.
     *
     * @param email       vendor business email
     * @param companyName vendor legal company name
     * @param metadata    optional key-value metadata to attach
     */
    ConnectAccountResult createConnectAccount(String email, String companyName, Map<String, String> metadata);

    /**
     * Generates a Stripe-hosted onboarding link for the vendor to complete KYC / banking.
     *
     * @param stripeConnectAccountId the vendor's Stripe Connect account ID
     * @param returnUrl              URL to redirect to after the vendor completes / exits onboarding
     * @param refreshUrl             URL to redirect to if the link expires before submission
     */
    ConnectOnboardingLinkResult generateConnectOnboardingLink(
            String stripeConnectAccountId, String returnUrl, String refreshUrl);

    /**
     * Fetches the current status of a Stripe Connect account. Use this to sync
     * {@code chargesEnabled} / {@code payoutsEnabled} from Stripe after {@code account.updated} webhooks.
     */
    ConnectAccountResult getConnectAccountStatus(String stripeConnectAccountId);

    // -------------------------------------------------------------------------
    // Stripe Connect transfers (vendor payouts)
    // -------------------------------------------------------------------------

    /**
     * Result of a Stripe transfer to a connected account.
     *
     * @param transferId    Stripe transfer ID (tr_...)
     * @param amountCents   amount transferred in smallest currency unit
     * @param currency      ISO 4217 lowercase currency code
     * @param status        Stripe transfer status (e.g. "pending", "paid")
     */
    record TransferResult(String transferId, long amountCents, String currency, String status) {}

    /**
     * Creates a Stripe transfer from the platform account to a connected vendor account.
     *
     * @param destinationAccountId  the vendor's Stripe Connect account ID (acct_...)
     * @param amountCents           amount to transfer in smallest currency unit
     * @param currency              ISO 4217 lowercase currency code
     * @param transferGroup         groups related charges and transfers (e.g. "order_123")
     * @param metadata              optional key-value metadata
     */
    TransferResult createTransfer(
            String destinationAccountId,
            long amountCents,
            String currency,
            String transferGroup,
            Map<String, String> metadata);
}
