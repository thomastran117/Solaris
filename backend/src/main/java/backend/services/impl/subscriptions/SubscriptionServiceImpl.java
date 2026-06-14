package backend.services.impl.subscriptions;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.subscription.CreateSubscriptionRequest;
import backend.dtos.requests.subscription.ShippingAddressRequest;
import backend.dtos.requests.subscription.UpdateSubscriptionRequest;
import backend.dtos.responses.subscription.SavedPaymentMethodResponse;
import backend.dtos.responses.subscription.SetupIntentResponse;
import backend.dtos.responses.subscription.ShippingAddressResponse;
import backend.dtos.responses.subscription.SubscriptionItemResponse;
import backend.dtos.responses.subscription.SubscriptionResponse;
import backend.annotations.retry.RetryOnConcurrency;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.SavedPaymentMethod;
import backend.models.core.ShippingAddress;
import backend.models.core.Subscription;
import backend.models.core.SubscriptionItem;
import backend.models.core.User;
import backend.models.enums.BillingInterval;
import backend.models.enums.ProductStatus;
import backend.models.enums.SubscriptionStatus;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.SavedPaymentMethodRepository;
import backend.repositories.SubscriptionRepository;
import backend.repositories.UserRepository;
import backend.services.intf.orders.OrderService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.PaymentService.CustomerResult;
import backend.services.intf.payments.PaymentService.PaymentMethodInfo;
import backend.services.intf.payments.PaymentService.PriceResult;
import backend.services.intf.payments.PaymentService.SetupIntentResult;
import backend.services.intf.payments.PaymentService.SubscriptionResult;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.subscriptions.SubscriptionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SavedPaymentMethodRepository savedPaymentMethodRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PaymentService paymentService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final LoyaltyService loyaltyService;
    private final ActivityEventPublisher activityEventPublisher;
    /** Self-proxy so create() can commit the PENDING row in its own transaction before the Stripe call. */
    private SubscriptionServiceImpl self;

    public SubscriptionServiceImpl(
            SubscriptionRepository subscriptionRepository,
            SavedPaymentMethodRepository savedPaymentMethodRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            PaymentService paymentService,
            OrderService orderService,
            OrderRepository orderRepository,
            LoyaltyService loyaltyService,
            ActivityEventPublisher activityEventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.savedPaymentMethodRepository = savedPaymentMethodRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.loyaltyService = loyaltyService;
        this.activityEventPublisher = activityEventPublisher;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy SubscriptionServiceImpl self) {
        this.self = self;
    }

    /** Self-proxy for transactional self-invocation; falls back to {@code this} in plain unit tests. */
    private SubscriptionServiceImpl self() {
        return self != null ? self : this;
    }

    // -------------------------------------------------------------------------
    // Saved payment methods
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SetupIntentResponse createSetupIntent(UUID userId) {
        User user = requireUser(userId);
        String customerId = ensureStripeCustomer(user);
        SetupIntentResult result = paymentService.createSetupIntent(customerId);
        return new SetupIntentResponse(result.id(), result.clientSecret(), result.customerId());
    }

    @Override
    public List<SavedPaymentMethodResponse> listPaymentMethods(UUID userId) {
        return savedPaymentMethodRepository.findAllByUserId(userId).stream()
                .map(this::toSavedPaymentMethodResponse)
                .toList();
    }

    @Override
    @Transactional
    public void detachPaymentMethod(UUID userId, UUID savedPaymentMethodId) {
        SavedPaymentMethod spm = savedPaymentMethodRepository.findById(savedPaymentMethodId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found: " + savedPaymentMethodId));
        if (!spm.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Payment method not found: " + savedPaymentMethodId);
        }
        try {
            paymentService.detachPaymentMethod(spm.getStripePaymentMethodId());
        } catch (Exception e) {
            log.warn("Stripe detach failed for pm {}: {}", spm.getStripePaymentMethodId(), e.getMessage());
        }
        savedPaymentMethodRepository.delete(spm);
    }

    // -------------------------------------------------------------------------
    // Subscription lifecycle
    // -------------------------------------------------------------------------

    /**
     * Local-first subscription creation: a PENDING (INCOMPLETE) local row is committed BEFORE the
     * billing Stripe subscription is created, so an orphaned Stripe subscription (one the app has no
     * record of) can't happen — the local row always exists for reconciliation. Flow:
     * <ol>
     *   <li>validate + create the recurring Price (a Price doesn't bill, so an orphaned Price is harmless);</li>
     *   <li>{@code persistPendingSubscription} (T1, committed) — local row with status INCOMPLETE, no Stripe sub id;</li>
     *   <li>create the Stripe subscription (carrying the local id in metadata);</li>
     *   <li>{@code finalizeSubscription} (T2) — attach the Stripe ids and activate.</li>
     * </ol>
     * If step 3 fails the pending row is marked EXPIRED (no orphan); if step 4 fails the Stripe sub
     * is best-effort cancelled and the row stays discoverable for reconciliation.
     */
    @Override
    public SubscriptionResponse create(UUID userId, CreateSubscriptionRequest req) {
        User user = requireUser(userId);

        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + req.getProductId()));
        validateSubscriptionProduct(product);
        validateInterval(product, req.getBillingInterval(), req.getIntervalCount());

        ProductVariant variant = null;
        if (req.getVariantId() != null) {
            variant = variantRepository.findByIdAndProductId(req.getVariantId(), product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + req.getVariantId()));
            validateSubscriptionVariant(variant);
        }

        BigDecimal unitPrice = variant != null ? variant.getPrice() : product.getPrice();
        if (product.getSubscriptionDiscountPercent() != null) {
            BigDecimal multiplier = BigDecimal.ONE.subtract(
                    product.getSubscriptionDiscountPercent().movePointLeft(2));
            unitPrice = unitPrice.multiply(multiplier);
        }
        long unitAmountCents = unitPrice.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (unitAmountCents <= 0) {
            throw new BadRequestException("Subscription unit price must be positive");
        }

        String customerId = ensureStripeCustomer(user);

        // Verify the supplied payment method belongs to this customer.
        PaymentMethodInfo pmInfo;
        try {
            pmInfo = paymentService.retrievePaymentMethod(req.getPaymentMethodId());
        } catch (Exception e) {
            throw new BadRequestException("Invalid payment method: " + req.getPaymentMethodId());
        }
        if (pmInfo.customerId() == null || !pmInfo.customerId().equals(customerId)) {
            throw new BadRequestException("Payment method does not belong to this customer");
        }

        Map<String, String> stripeMeta = new HashMap<>();
        stripeMeta.put("user_id", String.valueOf(userId));
        stripeMeta.put("product_id", String.valueOf(product.getId()));

        PriceResult price = paymentService.createRecurringPrice(
                unitAmountCents,
                req.getCurrency(),
                req.getBillingInterval(),
                req.getIntervalCount(),
                product.getName(),
                stripeMeta);

        // T1: persist the PENDING local row (committed) before the billing subscription exists.
        Subscription pending = self().persistPendingSubscription(
                userId, product.getId(), variant != null ? variant.getId() : null,
                customerId, price.id(), req, unitAmountCents);

        // Create the Stripe subscription, linking it back to the local row for reconciliation.
        if (pending.getId() != null) {
            stripeMeta.put("local_subscription_id", pending.getId().toString());
        }
        SubscriptionResult stripeSub;
        try {
            stripeSub = paymentService.createSubscription(
                    customerId, price.id(), req.getQuantity(), req.getPaymentMethodId(), stripeMeta);
        } catch (Exception e) {
            // No billing subscription was created — mark the pending row EXPIRED. No orphan exists.
            safeMarkSetupFailed(pending, "Stripe createSubscription failed: " + e.getMessage());
            throw e;
        }

        // T2: attach the Stripe ids and activate.
        try {
            return self().finalizeSubscription(pending, stripeSub);
        } catch (Exception e) {
            // The Stripe subscription exists but couldn't be finalized locally. Best-effort cancel so
            // the customer isn't billed; the pending row (linked via metadata) remains for reconcile.
            try {
                paymentService.cancelSubscription(stripeSub.id(), false);
            } catch (Exception cancelEx) {
                log.error("[SUBSCRIPTION] Stripe subscription {} (local {}) finalize failed AND cancel failed "
                                + "— manual reconciliation required: {}", stripeSub.id(), pending.getId(), cancelEx.getMessage());
            }
            safeMarkSetupFailed(pending, "Finalize failed: " + e.getMessage());
            throw e;
        }
    }

    private void safeMarkSetupFailed(Subscription pending, String reason) {
        try {
            self().markSubscriptionSetupFailed(pending, reason);
        } catch (Exception ex) {
            log.error("[SUBSCRIPTION] Failed to mark pending subscription {} as EXPIRED: {}",
                    pending.getId(), ex.getMessage());
        }
    }

    /** T1: persist the PENDING (INCOMPLETE) subscription with no Stripe subscription id yet. */
    @Transactional
    public Subscription persistPendingSubscription(UUID userId, UUID productId, UUID variantId, String customerId,
                                                   String priceId, CreateSubscriptionRequest req, long unitAmountCents) {
        User user = userRepository.getReferenceById(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        ProductVariant variant = variantId != null ? variantRepository.findById(variantId).orElse(null) : null;

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setCompany(product.getCompany());
        sub.setStripeSubscriptionId(null); // assigned at finalize
        sub.setStripeCustomerId(customerId);
        sub.setStripePriceId(priceId);
        sub.setStripePaymentMethodId(req.getPaymentMethodId());
        sub.setStatus(SubscriptionStatus.INCOMPLETE);
        sub.setBillingInterval(req.getBillingInterval());
        sub.setIntervalCount(req.getIntervalCount());
        sub.setCurrency(req.getCurrency());
        sub.setUnitAmountCents(Math.multiplyExact(unitAmountCents, req.getQuantity()));
        sub.setShippingAddress(toShippingAddress(req.getShippingAddress()));

        SubscriptionItem item = new SubscriptionItem();
        item.setSubscription(sub);
        item.setProduct(product);
        item.setVariant(variant);
        item.setQuantity(req.getQuantity());
        item.setUnitPriceCents(unitAmountCents);
        sub.getItems().add(item);

        return subscriptionRepository.save(sub);
    }

    /**
     * T2: attach the Stripe subscription ids/period and activate, then publish create activity.
     * Operates on the (committed, now-detached) pending entity from T1 — {@code save} merges it.
     */
    @Transactional
    public SubscriptionResponse finalizeSubscription(Subscription sub, SubscriptionResult stripeSub) {
        sub.setStripeSubscriptionId(stripeSub.id());
        sub.setStatus(mapStripeStatus(stripeSub.status()));
        sub.setCurrentPeriodStart(stripeSub.currentPeriodStart());
        sub.setCurrentPeriodEnd(stripeSub.currentPeriodEnd());
        sub.setNextBillingAt(stripeSub.currentPeriodEnd());
        if (!sub.getItems().isEmpty()) {
            sub.getItems().get(0).setStripeSubscriptionItemId(stripeSub.firstSubscriptionItemId());
        }

        Subscription saved = subscriptionRepository.save(sub);

        for (SubscriptionItem si : saved.getItems()) {
            if (si.getProduct() == null) continue;
            UUID mkt = si.getProduct().getMarketplaceId();
            if (mkt == null) continue;
            activityEventPublisher.publish(new UserActivityEvent(
                    saved.getUser().getId(), null, si.getProduct().getId(), mkt,
                    ActivityType.SUBSCRIPTION_CREATE, Instant.now()));
        }

        return toResponse(saved);
    }

    /** Marks a pending subscription EXPIRED when setup fails before/at finalize. */
    @Transactional
    public void markSubscriptionSetupFailed(Subscription sub, String reason) {
        if (sub != null) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            log.warn("[SUBSCRIPTION] Pending subscription {} marked EXPIRED: {}", sub.getId(), reason);
        }
    }

    @Override
    public SubscriptionResponse get(UUID userId, UUID subscriptionId) {
        return toResponse(requireOwnedSubscription(userId, subscriptionId));
    }

    @Override
    public List<SubscriptionResponse> listForUser(UUID userId) {
        return subscriptionRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionResponse update(UUID userId, UUID subscriptionId, UpdateSubscriptionRequest req) {
        Subscription sub = requireOwnedSubscription(userId, subscriptionId);
        requireMutable(sub);

        if (sub.getItems().isEmpty()) {
            throw new ConflictException("Subscription has no items to update");
        }
        SubscriptionItem item = sub.getItems().get(0);

        boolean priceChanges = req.getProductId() != null
                || req.getBillingInterval() != null
                || req.getIntervalCount() != null
                || req.getVariantId() != null;

        Product product = item.getProduct();
        ProductVariant variant = item.getVariant();
        if (req.getProductId() != null && !req.getProductId().equals(product.getId())) {
            product = productRepository.findById(req.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + req.getProductId()));
            validateSubscriptionProduct(product);
            variant = null; // reset variant when swapping product
        }

        if (req.getVariantId() != null) {
            variant = variantRepository.findByIdAndProductId(req.getVariantId(), product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + req.getVariantId()));
            validateSubscriptionProduct(product);
            validateSubscriptionVariant(variant);
        }

        BillingInterval interval = req.getBillingInterval() != null ? req.getBillingInterval() : sub.getBillingInterval();
        int intervalCount = req.getIntervalCount() != null ? req.getIntervalCount() : sub.getIntervalCount();
        int quantity = req.getQuantity() != null ? req.getQuantity() : item.getQuantity();

        if (priceChanges) {
            validateInterval(product, interval, intervalCount);

            BigDecimal unitPrice = variant != null ? variant.getPrice() : product.getPrice();
            if (product.getSubscriptionDiscountPercent() != null) {
                BigDecimal mult = BigDecimal.ONE.subtract(product.getSubscriptionDiscountPercent().movePointLeft(2));
                unitPrice = unitPrice.multiply(mult);
            }
            long unitCents = unitPrice.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

            PriceResult newPrice = paymentService.createRecurringPrice(
                    unitCents,
                    sub.getCurrency(),
                    interval,
                    intervalCount,
                    product.getName(),
                    Map.of("user_id", String.valueOf(userId), "product_id", String.valueOf(product.getId())));

            SubscriptionResult result = paymentService.swapSubscriptionPrice(
                    sub.getStripeSubscriptionId(),
                    item.getStripeSubscriptionItemId(),
                    newPrice.id(),
                    quantity);

            sub.setStripePriceId(newPrice.id());
            sub.setBillingInterval(interval);
            sub.setIntervalCount(intervalCount);
            sub.setCurrentPeriodStart(result.currentPeriodStart());
            sub.setCurrentPeriodEnd(result.currentPeriodEnd());
            sub.setNextBillingAt(result.currentPeriodEnd());
            sub.setUnitAmountCents(Math.multiplyExact(unitCents, (long) quantity));
            sub.setCompany(product.getCompany());

            item.setProduct(product);
            item.setVariant(variant);
            item.setQuantity(quantity);
            item.setUnitPriceCents(unitCents);
            if (result.firstSubscriptionItemId() != null) {
                item.setStripeSubscriptionItemId(result.firstSubscriptionItemId());
            }
        } else if (req.getQuantity() != null && req.getQuantity() != item.getQuantity()) {
            SubscriptionResult result = paymentService.updateSubscriptionQuantity(
                    sub.getStripeSubscriptionId(),
                    item.getStripeSubscriptionItemId(),
                    quantity);
            item.setQuantity(quantity);
            sub.setUnitAmountCents(Math.multiplyExact(item.getUnitPriceCents(), (long) quantity));
            sub.setCurrentPeriodEnd(result.currentPeriodEnd());
            sub.setNextBillingAt(result.currentPeriodEnd());
        }

        return toResponse(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionResponse pause(UUID userId, UUID subscriptionId) {
        Subscription sub = requireOwnedSubscription(userId, subscriptionId);
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new ConflictException("Only active subscriptions can be paused");
        }
        SubscriptionResult result = paymentService.pauseSubscription(sub.getStripeSubscriptionId());
        sub.setStatus(SubscriptionStatus.PAUSED);
        sub.setPausedAt(Instant.now());
        sub.setNextBillingAt(null);
        sub.setCurrentPeriodEnd(result.currentPeriodEnd());
        return toResponse(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionResponse resume(UUID userId, UUID subscriptionId) {
        Subscription sub = requireOwnedSubscription(userId, subscriptionId);
        if (sub.getStatus() != SubscriptionStatus.PAUSED) {
            throw new ConflictException("Only paused subscriptions can be resumed");
        }
        SubscriptionResult result = paymentService.resumeSubscription(sub.getStripeSubscriptionId());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPausedAt(null);
        sub.setCurrentPeriodStart(result.currentPeriodStart());
        sub.setCurrentPeriodEnd(result.currentPeriodEnd());
        sub.setNextBillingAt(result.currentPeriodEnd());
        return toResponse(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionResponse skipNext(UUID userId, UUID subscriptionId) {
        Subscription sub = requireOwnedSubscription(userId, subscriptionId);
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new ConflictException("Only active subscriptions can skip a cycle");
        }
        SubscriptionResult result = paymentService.skipNextCycle(
                sub.getStripeSubscriptionId(),
                sub.getBillingInterval(),
                sub.getIntervalCount());
        sub.setSkipNextCycle(true);
        sub.setCurrentPeriodEnd(result.currentPeriodEnd());
        sub.setNextBillingAt(result.currentPeriodEnd());
        return toResponse(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionResponse cancel(UUID userId, UUID subscriptionId, boolean atPeriodEnd) {
        Subscription sub = requireOwnedSubscriptionForUpdate(userId, subscriptionId);
        if (sub.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new ConflictException("Subscription is already cancelled");
        }
        SubscriptionResult result = paymentService.cancelSubscription(sub.getStripeSubscriptionId(), atPeriodEnd);
        if (atPeriodEnd) {
            sub.setCancelAtPeriodEnd(true);
            sub.setCurrentPeriodEnd(result.currentPeriodEnd());
            sub.setNextBillingAt(result.currentPeriodEnd());
        } else {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setCancelledAt(Instant.now());
            sub.setNextBillingAt(null);
        }
        return toResponse(subscriptionRepository.save(sub));
    }

    // -------------------------------------------------------------------------
    // Webhook handlers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handleSubscriptionUpdated(String stripeSubscriptionId) {
        Subscription sub = subscriptionRepository.findByStripeSubscriptionIdForUpdate(stripeSubscriptionId).orElse(null);
        if (sub == null) return;

        SubscriptionResult fresh = paymentService.retrieveSubscription(stripeSubscriptionId);
        sub.setStatus(mapStripeStatus(fresh.status()));
        sub.setCurrentPeriodStart(fresh.currentPeriodStart());
        sub.setCurrentPeriodEnd(fresh.currentPeriodEnd());
        if (sub.getStatus() == SubscriptionStatus.ACTIVE) {
            sub.setNextBillingAt(fresh.currentPeriodEnd());
        }
        if (sub.getStatus() == SubscriptionStatus.CANCELLED && sub.getCancelledAt() == null) {
            sub.setCancelledAt(Instant.now());
            sub.setNextBillingAt(null);
        }
        subscriptionRepository.save(sub);
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handleInvoicePaid(String stripeInvoiceId, String stripeSubscriptionId, long amountPaidCents) {
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) return;

        Subscription sub = subscriptionRepository.findByStripeSubscriptionIdForUpdate(stripeSubscriptionId).orElse(null);
        if (sub == null) {
            log.warn("invoice.paid for unknown subscription {}", stripeSubscriptionId);
            return;
        }

        // R2-H3: cancel-at-period-end means the customer asked NOT to be billed again.
        // Stripe sometimes fires `invoice.paid` before `customer.subscription.deleted`
        // for the final period — we must not turn that into an extra renewal order, or
        // the customer gets billed for a subscription they cancelled.
        if (sub.isCancelAtPeriodEnd() || sub.getStatus() == SubscriptionStatus.CANCELLED) {
            log.info("invoice.paid skipped — subscription {} is marked for cancellation (cancelAtPeriodEnd={}, status={})",
                    sub.getId(), sub.isCancelAtPeriodEnd(), sub.getStatus());
            return;
        }

        backend.models.core.Order renewalOrder;
        try {
            renewalOrder = orderService.createRenewalOrder(sub, stripeInvoiceId, amountPaidCents);
        } catch (Exception e) {
            log.error("Failed to create renewal order for subscription {} invoice {}: {}",
                    sub.getId(), stripeInvoiceId, e.getMessage(), e);
            return;
        }

        // The renewal contained a discontinued line: createRenewalOrder restocked and cancelled the
        // order. Refund the already-charged invoice and cancel the subscription so no future renewals
        // recur the problem. Each step is best-effort/logged; we never re-activate the subscription.
        if (renewalOrder.getStatus() == backend.models.enums.OrderStatus.CANCELLED) {
            try {
                paymentService.refundInvoice(stripeInvoiceId, null);
            } catch (Exception e) {
                log.error("[SUBSCRIPTION] Failed to refund auto-cancelled renewal invoice {} (subscription {}): {}",
                        stripeInvoiceId, sub.getId(), e.getMessage());
            }
            try {
                paymentService.cancelSubscription(stripeSubscriptionId, false);
            } catch (Exception e) {
                log.error("[SUBSCRIPTION] Failed to cancel subscription {} after discontinued renewal: {}",
                        sub.getId(), e.getMessage());
            }
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setCancelAtPeriodEnd(false);
            subscriptionRepository.save(sub);
            log.warn("[SUBSCRIPTION] Subscription {} cancelled and invoice {} refunded — renewal contained discontinued items",
                    sub.getId(), stripeInvoiceId);
            return;
        }

        try {
            loyaltyService.recordOrderEarn(renewalOrder, resolveSubscriptionCompanyId(sub));
        } catch (Exception e) {
            log.error("[LOYALTY] Failed to record earn for renewal (subscription {}, invoice {}): {}",
                    sub.getId(), stripeInvoiceId, e.getMessage());
        }

        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPastDueSince(null);
        sub.setSkipNextCycle(false);
        SubscriptionResult fresh = paymentService.retrieveSubscription(stripeSubscriptionId);
        sub.setCurrentPeriodStart(fresh.currentPeriodStart());
        sub.setCurrentPeriodEnd(fresh.currentPeriodEnd());
        sub.setNextBillingAt(fresh.currentPeriodEnd());
        subscriptionRepository.save(sub);
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handleInvoicePaymentFailed(String stripeInvoiceId, String stripeSubscriptionId) {
        if (stripeSubscriptionId == null) return;
        Subscription sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).orElse(null);
        if (sub == null) return;

        sub.setStatus(SubscriptionStatus.PAST_DUE);
        if (sub.getPastDueSince() == null) {
            sub.setPastDueSince(Instant.now());
        }
        subscriptionRepository.save(sub);

        try {
            User user = sub.getUser();
            if (user != null && user.getEmail() != null) {
                // No bespoke template yet — reuse the credit-issued template body for now
                // would couple unrelated copy. Log an audit line and rely on Stripe's own
                // dunning emails until a dedicated template is added.
                log.info("Subscription {} marked PAST_DUE for user {}", sub.getId(), user.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to record PAST_DUE notification for subscription {}: {}", sub.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handleSetupIntentSucceeded(String stripeCustomerId, String stripePaymentMethodId) {
        if (stripePaymentMethodId == null || stripeCustomerId == null) return;

        if (savedPaymentMethodRepository.findByStripePaymentMethodId(stripePaymentMethodId).isPresent()) {
            return; // idempotent
        }

        User user = userRepository.findByStripeCustomerId(stripeCustomerId).orElse(null);
        if (user == null) {
            log.warn("setup_intent.succeeded for unknown stripe customer {}", stripeCustomerId);
            return;
        }

        PaymentMethodInfo pm;
        try {
            pm = paymentService.retrievePaymentMethod(stripePaymentMethodId);
        } catch (Exception e) {
            // Stripe payment method retrieval failed — the customer's card is NOT saved.
            // This requires manual reconciliation: the setup_intent.succeeded webhook fired
            // but we couldn't persist the method. Log at ERROR so on-call is paged, and
            // include all context needed to replay via support tooling.
            log.error("[RECONCILIATION REQUIRED] setup_intent.succeeded received but payment method " +
                      "could not be retrieved. customerId={} paymentMethodId={} error={}. " +
                      "Customer card was NOT saved — manual action required.",
                    stripeCustomerId, stripePaymentMethodId, e.getMessage(), e);
            return;
        }

        SavedPaymentMethod spm = new SavedPaymentMethod();
        spm.setUser(user);
        spm.setStripePaymentMethodId(stripePaymentMethodId);
        spm.setStripeCustomerId(stripeCustomerId);
        spm.setBrand(pm.brand());
        spm.setLast4(pm.last4());
        spm.setExpMonth(pm.expMonth());
        spm.setExpYear(pm.expYear());
        spm.setDefault(savedPaymentMethodRepository.findByUserIdAndIsDefaultTrue(user.getId()).isEmpty());
        savedPaymentMethodRepository.save(spm);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private Subscription requireOwnedSubscription(UUID userId, UUID subscriptionId) {
        return subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));
    }

    private Subscription requireOwnedSubscriptionForUpdate(UUID userId, UUID subscriptionId) {
        return subscriptionRepository.findByIdAndUserIdForUpdate(subscriptionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));
    }

    private void requireMutable(Subscription sub) {
        if (sub.getStatus() == SubscriptionStatus.CANCELLED || sub.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new ConflictException("Subscription is no longer modifiable");
        }
    }

    private String ensureStripeCustomer(User user) {
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }
        CustomerResult c = paymentService.createCustomer(
                user.getEmail(),
                fullName(user),
                Map.of("user_id", String.valueOf(user.getId())));
        user.setStripeCustomerId(c.id());
        userRepository.save(user);
        return c.id();
    }

    private String fullName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last  = user.getLastName()  != null ? user.getLastName()  : "";
        String name  = (first + " " + last).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private void validateInterval(Product product, BillingInterval interval, int count) {
        String allowed = product.getSubscriptionIntervals();
        if (allowed == null || allowed.isBlank()) return;
        String needle = interval.name() + ":" + count;
        for (String token : allowed.split(",")) {
            if (token.trim().equalsIgnoreCase(needle)) return;
        }
        throw new BadRequestException("Billing cadence " + needle + " is not allowed for this product");
    }

    private void validateSubscriptionProduct(Product product) {
        if (!product.isSubscribable()
                || product.getStatus() != ProductStatus.ACTIVE
                || !product.isListed()
                || !product.isPurchasable()) {
            throw new BadRequestException("Product is not available as a subscription");
        }
    }

    private void validateSubscriptionVariant(ProductVariant variant) {
        if (!variant.isPurchasable()) {
            throw new BadRequestException("Variant is not available as a subscription");
        }
    }

    private UUID resolveSubscriptionCompanyId(Subscription sub) {
        if (sub.getItems() != null && !sub.getItems().isEmpty()) {
            Product product = sub.getItems().get(0).getProduct();
            if (product != null && product.getCompany() != null) {
                if (sub.getCompany() == null || !product.getCompany().getId().equals(sub.getCompany().getId())) {
                    sub.setCompany(product.getCompany());
                }
                return product.getCompany().getId();
            }
        }
        if (sub.getCompany() != null) {
            return sub.getCompany().getId();
        }
        throw new BadRequestException("Subscription has no owning company");
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null) return SubscriptionStatus.INCOMPLETE;
        return switch (stripeStatus) {
            case "active", "trialing"        -> SubscriptionStatus.ACTIVE;
            case "paused"                    -> SubscriptionStatus.PAUSED;
            case "past_due", "unpaid"        -> SubscriptionStatus.PAST_DUE;
            case "canceled"                  -> SubscriptionStatus.CANCELLED;
            case "incomplete"                -> SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired"        -> SubscriptionStatus.EXPIRED;
            default                          -> SubscriptionStatus.INCOMPLETE;
        };
    }

    private ShippingAddress toShippingAddress(ShippingAddressRequest req) {
        return new ShippingAddress(req.getName(), req.getStreet(), req.getCity(),
                req.getState(), req.getPostalCode(), req.getCountry());
    }

    private SubscriptionResponse toResponse(Subscription sub) {
        ShippingAddressResponse addr = sub.getShippingAddress() == null ? null
                : new ShippingAddressResponse(
                        sub.getShippingAddress().getName(),
                        sub.getShippingAddress().getStreet(),
                        sub.getShippingAddress().getCity(),
                        sub.getShippingAddress().getState(),
                        sub.getShippingAddress().getPostalCode(),
                        sub.getShippingAddress().getCountry());

        List<SubscriptionItemResponse> items = sub.getItems().stream()
                .map(i -> new SubscriptionItemResponse(
                        i.getId(),
                        i.getProduct() != null ? i.getProduct().getId() : null,
                        i.getProduct() != null ? i.getProduct().getName() : null,
                        i.getVariant() != null ? i.getVariant().getId() : null,
                        i.getQuantity(),
                        i.getUnitPriceCents()))
                .toList();

        return new SubscriptionResponse(
                sub.getId(),
                sub.getStatus(),
                sub.getBillingInterval(),
                sub.getIntervalCount(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.getNextBillingAt(),
                sub.getPausedAt(),
                sub.getCancelledAt(),
                sub.isSkipNextCycle(),
                sub.isCancelAtPeriodEnd(),
                sub.getCurrency(),
                sub.getUnitAmountCents(),
                addr,
                items,
                sub.getCreatedAt(),
                sub.getUpdatedAt());
    }

    private SavedPaymentMethodResponse toSavedPaymentMethodResponse(SavedPaymentMethod spm) {
        return new SavedPaymentMethodResponse(
                spm.getId(),
                spm.getStripePaymentMethodId(),
                spm.getBrand(),
                spm.getLast4(),
                spm.getExpMonth(),
                spm.getExpYear(),
                spm.isDefault());
    }
}
