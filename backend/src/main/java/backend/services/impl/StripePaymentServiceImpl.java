package backend.services.impl;

import com.stripe.Stripe;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.exceptions.http.PaymentException;
import backend.exceptions.http.TooManyRequestException;
import backend.services.intf.PaymentService;

import java.util.Map;

@Service
public class StripePaymentServiceImpl implements PaymentService {

    private final EnvironmentSetting environmentSetting;
    private final RetryTemplate retryTemplate;

    public StripePaymentServiceImpl(
            EnvironmentSetting environmentSetting,
            @Qualifier("stripeRetryTemplate") RetryTemplate retryTemplate) {
        this.environmentSetting = environmentSetting;
        this.retryTemplate = retryTemplate;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = environmentSetting.getStripe().getSecretKey();
    }

    @Override
    public PaymentIntentResult createPaymentIntent(
            long amountInCents,
            String currency,
            String customerId,
            Map<String, String> metadata) {
        return executeWithRetry(() -> {
            PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency.toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    );

            if (customerId != null && !customerId.isBlank()) {
                params.setCustomer(customerId);
            }

            if (metadata != null && !metadata.isEmpty()) {
                params.putAllMetadata(metadata);
            }

            return toPaymentIntentResult(PaymentIntent.create(params.build()));
        });
    }

    @Override
    public PaymentIntentResult retrievePaymentIntent(String paymentIntentId) {
        return executeWithRetry(() -> toPaymentIntentResult(PaymentIntent.retrieve(paymentIntentId)));
    }

    @Override
    public PaymentIntentResult cancelPaymentIntent(String paymentIntentId) {
        return executeWithRetry(() -> {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            return toPaymentIntentResult(intent.cancel(PaymentIntentCancelParams.builder().build()));
        });
    }

    @Override
    public RefundResult refundPayment(String paymentIntentId, Long amountInCents) {
        return executeWithRetry(() -> {
            RefundCreateParams.Builder params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId);

            if (amountInCents != null) {
                params.setAmount(amountInCents);
            }

            Refund refund = Refund.create(params.build());
            return new RefundResult(
                    refund.getId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    refund.getStatus(),
                    refund.getPaymentIntent()
            );
        });
    }

    @Override
    public CustomerResult createCustomer(String email, String name, Map<String, String> metadata) {
        return executeWithRetry(() -> {
            CustomerCreateParams.Builder params = CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName(name);

            if (metadata != null && !metadata.isEmpty()) {
                params.putAllMetadata(metadata);
            }

            Customer customer = Customer.create(params.build());
            return new CustomerResult(customer.getId(), customer.getEmail(), customer.getName());
        });
    }

    @Override
    public WebhookEvent constructWebhookEvent(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    environmentSetting.getStripe().getWebhookSecret()
            );

            StripeObject stripeObject = event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);

            String objectId = stripeObject != null ? extractId(stripeObject) : null;
            String eventType = event.getType();
            String objectType = eventType != null && eventType.contains(".")
                    ? eventType.substring(0, eventType.indexOf('.'))
                    : eventType;

            return new WebhookEvent(event.getType(), objectId, objectType);
        } catch (SignatureVerificationException e) {
            throw new BadRequestException("Invalid webhook signature");
        } catch (Exception e) {
            throw new BadRequestException("Malformed webhook payload");
        }
    }

    /**
     * Wraps a Stripe API call in the retry template. Transient errors (connection,
     * 5xx, rate-limit) are retried with exponential backoff. Non-retryable errors
     * (card decline, auth, invalid request) are thrown immediately.
     */
    private <T> T executeWithRetry(StripeOperation<T> operation) {
        try {
            return retryTemplate.execute(context -> {
                try {
                    return operation.execute();
                } catch (StripeException e) {
                    throw e;
                }
            });
        } catch (StripeException e) {
            throw mapStripeException(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new InternalServerErrorException();
        }
    }

    @FunctionalInterface
    private interface StripeOperation<T> {
        T execute() throws StripeException;
    }

    private PaymentIntentResult toPaymentIntentResult(PaymentIntent intent) {
        return new PaymentIntentResult(
                intent.getId(),
                intent.getClientSecret(),
                intent.getAmount(),
                intent.getCurrency(),
                intent.getStatus(),
                intent.getCustomer()
        );
    }

    private String extractId(StripeObject obj) {
        try {
            return (String) obj.getClass().getMethod("getId").invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private RuntimeException mapStripeException(StripeException e) {
        if (e instanceof CardException cardEx) {
            return new PaymentException(cardEx.getUserMessage() != null ? cardEx.getUserMessage() : "Your card was declined", cardEx.getMessage());
        }
        if (e instanceof RateLimitException) {
            return new TooManyRequestException();
        }
        if (e instanceof AuthenticationException) {
            return new InternalServerErrorException();
        }
        return new InternalServerErrorException();
    }
}
