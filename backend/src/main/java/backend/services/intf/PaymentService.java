package backend.services.intf;

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
     * @param objectType provider object type (e.g. "payment_intent", "charge")
     */
    record WebhookEvent(
            String type,
            String objectId,
            String objectType
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
}
