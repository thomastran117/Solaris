package backend.services.impl.payments;

import com.stripe.Stripe;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Charge;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Price;
import com.stripe.model.Refund;
import com.stripe.model.SetupIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodDetachParams;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.SubscriptionResumeParams;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.exceptions.http.PaymentException;
import backend.exceptions.http.TooManyRequestException;
import backend.models.enums.BillingInterval;
import backend.services.intf.payments.PaymentService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

            String idempotencyKey = "refund:" + paymentIntentId + ":" + (amountInCents != null ? amountInCents : "full");
            RequestOptions opts = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();

            Refund refund = Refund.create(params.build(), opts);
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

            Map<String, String> metadata = extractMetadata(eventType, stripeObject);

            return new WebhookEvent(event.getId(), event.getType(), objectId, objectType, metadata);
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

    /**
     * Extracts event-type-specific metadata for downstream handlers. Returns an empty
     * map for event types that don't carry extra fields. Failures here are swallowed —
     * metadata is best-effort and must not break webhook processing.
     */
    private Map<String, String> extractMetadata(String eventType, StripeObject stripeObject) {
        if (stripeObject == null || eventType == null) return Map.of();

        try {
            if ("charge.refunded".equals(eventType) && stripeObject instanceof Charge charge) {
                var refunds = charge.getRefunds();
                if (refunds != null && !refunds.getData().isEmpty()) {
                    Refund r = refunds.getData().get(0);
                    return Map.of(
                            "refundId",         r.getId() != null ? r.getId() : "",
                            "refundStatus",     r.getStatus() != null ? r.getStatus() : "",
                            "refundAmountCents", r.getAmount() != null ? String.valueOf(r.getAmount()) : "0"
                    );
                }
            } else if ("refund.updated".equals(eventType) && stripeObject instanceof Refund r) {
                return Map.of(
                        "refundId",         r.getId() != null ? r.getId() : "",
                        "refundStatus",     r.getStatus() != null ? r.getStatus() : "",
                        "refundAmountCents", r.getAmount() != null ? String.valueOf(r.getAmount()) : "0"
                );
            } else if (eventType.startsWith("invoice.") && stripeObject instanceof Invoice inv) {
                Map<String, String> meta = new HashMap<>();
                if (inv.getSubscription() != null) meta.put("subscriptionId", inv.getSubscription());
                if (inv.getCustomer() != null)     meta.put("customerId", inv.getCustomer());
                if (inv.getAmountPaid() != null)   meta.put("amountPaidCents", String.valueOf(inv.getAmountPaid()));
                return meta;
            } else if (eventType.startsWith("customer.subscription.") && stripeObject instanceof Subscription sub) {
                Map<String, String> meta = new HashMap<>();
                meta.put("subscriptionId", sub.getId());
                if (sub.getStatus() != null) meta.put("subscriptionStatus", sub.getStatus());
                return meta;
            } else if ("setup_intent.succeeded".equals(eventType) && stripeObject instanceof SetupIntent si) {
                Map<String, String> meta = new HashMap<>();
                if (si.getCustomer() != null)      meta.put("customerId", si.getCustomer());
                if (si.getPaymentMethod() != null) meta.put("paymentMethodId", si.getPaymentMethod());
                return meta;
            } else if ("account.updated".equals(eventType) && stripeObject instanceof Account a) {
                Map<String, String> meta = new HashMap<>();
                meta.put("chargesEnabled",   String.valueOf(Boolean.TRUE.equals(a.getChargesEnabled())));
                meta.put("payoutsEnabled",   String.valueOf(Boolean.TRUE.equals(a.getPayoutsEnabled())));
                meta.put("detailsSubmitted", String.valueOf(Boolean.TRUE.equals(a.getDetailsSubmitted())));
                return meta;
            } else if (eventType.startsWith("transfer.") && stripeObject instanceof Transfer t) {
                Map<String, String> meta = new HashMap<>();
                if (t.getAmount()   != null) meta.put("amountCents", String.valueOf(t.getAmount()));
                if (t.getCurrency() != null) meta.put("currency", t.getCurrency());
                if (t.getTransferGroup() != null) meta.put("transferGroup", t.getTransferGroup());
                if (t.getMetadata() != null) {
                    t.getMetadata().forEach((k, v) -> meta.putIfAbsent(k, v));
                }
                return meta;
            }
        } catch (Exception e) {
            // Non-critical: metadata extraction failure should not break webhook processing
        }
        return Map.of();
    }

    private String extractId(StripeObject obj) {
        try {
            return (String) obj.getClass().getMethod("getId").invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Subscriptions / saved payment methods
    // -------------------------------------------------------------------------

    @Override
    public SetupIntentResult createSetupIntent(String customerId) {
        return executeWithRetry(() -> {
            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .addPaymentMethodType("card")
                    .build();
            SetupIntent si = SetupIntent.create(params);
            return new SetupIntentResult(si.getId(), si.getClientSecret(), si.getCustomer());
        });
    }

    @Override
    public List<PaymentMethodInfo> listPaymentMethods(String customerId) {
        return executeWithRetry(() -> {
            PaymentMethodListParams params = PaymentMethodListParams.builder()
                    .setCustomer(customerId)
                    .setType(PaymentMethodListParams.Type.CARD)
                    .build();
            var collection = PaymentMethod.list(params);
            List<PaymentMethodInfo> out = new ArrayList<>();
            for (PaymentMethod pm : collection.getData()) {
                out.add(toPaymentMethodInfo(pm));
            }
            return out;
        });
    }

    @Override
    public PaymentMethodInfo retrievePaymentMethod(String paymentMethodId) {
        return executeWithRetry(() -> toPaymentMethodInfo(PaymentMethod.retrieve(paymentMethodId)));
    }

    @Override
    public void detachPaymentMethod(String paymentMethodId) {
        executeWithRetry(() -> {
            try {
                PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);
                if (pm.getCustomer() != null) {
                    pm.detach(PaymentMethodDetachParams.builder().build());
                }
            } catch (StripeException e) {
                throw e;
            }
            return null;
        });
    }

    @Override
    public PriceResult createRecurringPrice(long unitAmountCents, String currency,
                                            BillingInterval interval, int intervalCount,
                                            String productName, Map<String, String> metadata) {
        return executeWithRetry(() -> {
            PriceCreateParams.Recurring recurring = PriceCreateParams.Recurring.builder()
                    .setInterval(toStripeInterval(interval))
                    .setIntervalCount((long) intervalCount)
                    .build();

            PriceCreateParams.Builder params = PriceCreateParams.builder()
                    .setUnitAmount(unitAmountCents)
                    .setCurrency(currency.toLowerCase())
                    .setRecurring(recurring)
                    .setProductData(
                            PriceCreateParams.ProductData.builder()
                                    .setName(productName != null ? productName : "Subscription product")
                                    .build()
                    );

            if (metadata != null && !metadata.isEmpty()) {
                params.putAllMetadata(metadata);
            }

            Price p = Price.create(params.build());
            return new PriceResult(p.getId(), p.getUnitAmount() != null ? p.getUnitAmount() : 0L, p.getCurrency());
        });
    }

    @Override
    public SubscriptionResult createSubscription(String customerId, String priceId, int quantity,
                                                 String defaultPaymentMethodId,
                                                 Map<String, String> metadata) {
        return executeWithRetry(() -> {
            SubscriptionCreateParams.Item item = SubscriptionCreateParams.Item.builder()
                    .setPrice(priceId)
                    .setQuantity((long) quantity)
                    .build();

            SubscriptionCreateParams.Builder params = SubscriptionCreateParams.builder()
                    .setCustomer(customerId)
                    .addItem(item)
                    .setOffSession(true)
                    .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                    .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.NONE);

            if (defaultPaymentMethodId != null && !defaultPaymentMethodId.isBlank()) {
                params.setDefaultPaymentMethod(defaultPaymentMethodId);
            }

            if (metadata != null && !metadata.isEmpty()) {
                params.putAllMetadata(metadata);
            }

            Subscription sub = Subscription.create(params.build());
            return toSubscriptionResult(sub);
        });
    }

    @Override
    public SubscriptionResult updateSubscriptionQuantity(String stripeSubscriptionId,
                                                         String stripeSubscriptionItemId,
                                                         int newQuantity) {
        return executeWithRetry(() -> {
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            SubscriptionUpdateParams.Item item = SubscriptionUpdateParams.Item.builder()
                    .setId(stripeSubscriptionItemId)
                    .setQuantity((long) newQuantity)
                    .build();
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(item)
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.NONE)
                    .build();
            return toSubscriptionResult(sub.update(params));
        });
    }

    @Override
    public SubscriptionResult swapSubscriptionPrice(String stripeSubscriptionId,
                                                    String stripeSubscriptionItemId,
                                                    String newPriceId, int newQuantity) {
        return executeWithRetry(() -> {
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            SubscriptionUpdateParams.Item item = SubscriptionUpdateParams.Item.builder()
                    .setId(stripeSubscriptionItemId)
                    .setPrice(newPriceId)
                    .setQuantity((long) newQuantity)
                    .build();
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(item)
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.NONE)
                    .build();
            return toSubscriptionResult(sub.update(params));
        });
    }

    @Override
    public SubscriptionResult cancelSubscription(String stripeSubscriptionId, boolean atPeriodEnd) {
        return executeWithRetry(() -> {
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            if (atPeriodEnd) {
                SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                        .setCancelAtPeriodEnd(true)
                        .build();
                return toSubscriptionResult(sub.update(params));
            }
            return toSubscriptionResult(sub.cancel(SubscriptionCancelParams.builder().build()));
        });
    }

    @Override
    public SubscriptionResult pauseSubscription(String stripeSubscriptionId) {
        return executeWithRetry(() -> {
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setPauseCollection(
                            SubscriptionUpdateParams.PauseCollection.builder()
                                    .setBehavior(SubscriptionUpdateParams.PauseCollection.Behavior.VOID)
                                    .build()
                    )
                    .build();
            return toSubscriptionResult(sub.update(params));
        });
    }

    @Override
    public SubscriptionResult resumeSubscription(String stripeSubscriptionId) {
        return executeWithRetry(() -> {
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            SubscriptionResumeParams params = SubscriptionResumeParams.builder()
                    .setProrationBehavior(SubscriptionResumeParams.ProrationBehavior.NONE)
                    .build();
            return toSubscriptionResult(sub.resume(params));
        });
    }

    @Override
    public SubscriptionResult skipNextCycle(String stripeSubscriptionId,
                                            BillingInterval interval, int intervalCount) {
        return executeWithRetry(() -> {
            Subscription sub = Subscription.retrieve(stripeSubscriptionId);
            long periodEndEpoch = sub.getCurrentPeriodEnd() != null ? sub.getCurrentPeriodEnd() : 0L;
            Instant next = Instant.ofEpochSecond(periodEndEpoch).plus(intervalToUnits(interval, intervalCount), intervalToChrono(interval));

            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setBillingCycleAnchor(SubscriptionUpdateParams.BillingCycleAnchor.NOW)
                    .setTrialEnd(next.getEpochSecond())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.NONE)
                    .build();
            return toSubscriptionResult(sub.update(params));
        });
    }

    @Override
    public SubscriptionResult retrieveSubscription(String stripeSubscriptionId) {
        return executeWithRetry(() -> toSubscriptionResult(Subscription.retrieve(stripeSubscriptionId)));
    }

    private PaymentMethodInfo toPaymentMethodInfo(PaymentMethod pm) {
        String brand = null, last4 = null;
        Integer expMonth = null, expYear = null;
        if (pm.getCard() != null) {
            brand = pm.getCard().getBrand();
            last4 = pm.getCard().getLast4();
            if (pm.getCard().getExpMonth() != null) expMonth = pm.getCard().getExpMonth().intValue();
            if (pm.getCard().getExpYear() != null)  expYear  = pm.getCard().getExpYear().intValue();
        }
        return new PaymentMethodInfo(pm.getId(), pm.getCustomer(), brand, last4, expMonth, expYear);
    }

    private SubscriptionResult toSubscriptionResult(Subscription sub) {
        Instant start = sub.getCurrentPeriodStart() != null ? Instant.ofEpochSecond(sub.getCurrentPeriodStart()) : null;
        Instant end   = sub.getCurrentPeriodEnd()   != null ? Instant.ofEpochSecond(sub.getCurrentPeriodEnd())   : null;
        String firstItemId = null;
        if (sub.getItems() != null && sub.getItems().getData() != null && !sub.getItems().getData().isEmpty()) {
            firstItemId = sub.getItems().getData().get(0).getId();
        }
        return new SubscriptionResult(
                sub.getId(),
                sub.getCustomer(),
                sub.getStatus(),
                sub.getLatestInvoice(),
                start,
                end,
                sub.getDefaultPaymentMethod(),
                firstItemId
        );
    }

    private PriceCreateParams.Recurring.Interval toStripeInterval(BillingInterval interval) {
        return switch (interval) {
            case DAY   -> PriceCreateParams.Recurring.Interval.DAY;
            case WEEK  -> PriceCreateParams.Recurring.Interval.WEEK;
            case MONTH -> PriceCreateParams.Recurring.Interval.MONTH;
            case YEAR  -> PriceCreateParams.Recurring.Interval.YEAR;
        };
    }

    private long intervalToUnits(BillingInterval interval, int count) {
        return (long) count;
    }

    private ChronoUnit intervalToChrono(BillingInterval interval) {
        return switch (interval) {
            case DAY   -> ChronoUnit.DAYS;
            case WEEK  -> ChronoUnit.WEEKS;
            case MONTH -> ChronoUnit.MONTHS;
            case YEAR  -> ChronoUnit.YEARS;
        };
    }

    // -------------------------------------------------------------------------
    // Stripe Checkout + Customer Portal
    // -------------------------------------------------------------------------

    @Override
    public String createCheckoutSession(String customerId, String priceId, String successUrl, String cancelUrl) {
        return executeWithRetry(() -> {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build()
                    )
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .build();
            com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(params);
            return session.getUrl();
        });
    }

    @Override
    public String createPortalSession(String customerId, String returnUrl) {
        return executeWithRetry(() -> {
            com.stripe.param.billingportal.SessionCreateParams params =
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(customerId)
                            .setReturnUrl(returnUrl)
                            .build();
            com.stripe.model.billingportal.Session session =
                    com.stripe.model.billingportal.Session.create(params);
            return session.getUrl();
        });
    }

    @Override
    public WebhookEvent constructPremiumWebhookEvent(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    environmentSetting.getStripe().getPremium().getWebhookSecret()
            );

            StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
            String objectId = stripeObject != null ? extractId(stripeObject) : null;
            String eventType = event.getType();
            String objectType = eventType != null && eventType.contains(".")
                    ? eventType.substring(0, eventType.indexOf('.'))
                    : eventType;

            Map<String, String> metadata = extractPremiumMetadata(eventType, stripeObject);
            return new WebhookEvent(event.getId(), eventType, objectId, objectType, metadata);
        } catch (SignatureVerificationException e) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        } catch (Exception e) {
            throw new BadRequestException("Malformed webhook payload");
        }
    }

    private Map<String, String> extractPremiumMetadata(String eventType, StripeObject stripeObject) {
        if (stripeObject == null || eventType == null) return Map.of();
        try {
            if ("checkout.session.completed".equals(eventType)
                    && stripeObject instanceof com.stripe.model.checkout.Session session) {
                Map<String, String> meta = new HashMap<>();
                if (session.getCustomer() != null)     meta.put("customerId",     session.getCustomer());
                if (session.getSubscription() != null) meta.put("subscriptionId", session.getSubscription());
                return meta;
            } else if (eventType.startsWith("customer.subscription.") && stripeObject instanceof Subscription sub) {
                Map<String, String> meta = new HashMap<>();
                meta.put("subscriptionId", sub.getId());
                if (sub.getStatus() != null)              meta.put("subscriptionStatus", sub.getStatus());
                if (sub.getCustomer() != null)            meta.put("customerId",         sub.getCustomer());
                if (sub.getCurrentPeriodEnd() != null)    meta.put("currentPeriodEnd",   String.valueOf(sub.getCurrentPeriodEnd()));
                return meta;
            } else if (eventType.startsWith("invoice.") && stripeObject instanceof Invoice inv) {
                Map<String, String> meta = new HashMap<>();
                if (inv.getSubscription() != null) meta.put("subscriptionId", inv.getSubscription());
                return meta;
            }
        } catch (Exception ignored) {
            // metadata extraction is best-effort — never break webhook processing
        }
        return Map.of();
    }

    // -------------------------------------------------------------------------
    // Stripe Connect Express
    // -------------------------------------------------------------------------

    @Override
    public ConnectAccountResult createConnectAccount(String email, String companyName, Map<String, String> metadata) {
        return executeWithRetry(() -> {
            AccountCreateParams.Builder params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setEmail(email)
                    .setBusinessProfile(
                            AccountCreateParams.BusinessProfile.builder()
                                    .setName(companyName)
                                    .build()
                    )
                    .setCapabilities(
                            AccountCreateParams.Capabilities.builder()
                                    .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                                    .build()
                    );

            if (metadata != null && !metadata.isEmpty()) {
                params.putAllMetadata(metadata);
            }

            Account account = Account.create(params.build());
            return toConnectAccountResult(account);
        });
    }

    @Override
    public ConnectOnboardingLinkResult generateConnectOnboardingLink(
            String stripeConnectAccountId, String returnUrl, String refreshUrl) {
        return executeWithRetry(() -> {
            AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                    .setAccount(stripeConnectAccountId)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .setReturnUrl(returnUrl)
                    .setRefreshUrl(refreshUrl)
                    .build();

            AccountLink link = AccountLink.create(params);
            java.time.Instant expiresAt = link.getExpiresAt() != null
                    ? java.time.Instant.ofEpochSecond(link.getExpiresAt())
                    : null;
            return new ConnectOnboardingLinkResult(link.getUrl(), expiresAt);
        });
    }

    @Override
    public ConnectAccountResult getConnectAccountStatus(String stripeConnectAccountId) {
        return executeWithRetry(() -> {
            Account account = Account.retrieve(stripeConnectAccountId);
            return toConnectAccountResult(account);
        });
    }

    @Override
    public TransferResult createTransfer(String destinationAccountId, long amountCents,
                                         String currency, String transferGroup,
                                         Map<String, String> metadata) {
        return executeWithRetry(() -> {
            TransferCreateParams.Builder params = TransferCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(currency.toLowerCase())
                    .setDestination(destinationAccountId)
                    .setTransferGroup(transferGroup);

            if (metadata != null && !metadata.isEmpty()) {
                params.putAllMetadata(metadata);
            }

            Transfer transfer = Transfer.create(params.build());
            return new TransferResult(
                    transfer.getId(),
                    transfer.getAmount() != null ? transfer.getAmount() : 0L,
                    transfer.getCurrency(),
                    "pending"
            );
        });
    }

    private ConnectAccountResult toConnectAccountResult(Account account) {
        boolean chargesEnabled  = Boolean.TRUE.equals(account.getChargesEnabled());
        boolean payoutsEnabled  = Boolean.TRUE.equals(account.getPayoutsEnabled());
        boolean detailsSubmitted = Boolean.TRUE.equals(account.getDetailsSubmitted());
        return new ConnectAccountResult(account.getId(), chargesEnabled, payoutsEnabled, detailsSubmitted);
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
