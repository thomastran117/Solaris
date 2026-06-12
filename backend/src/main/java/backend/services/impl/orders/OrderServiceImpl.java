package backend.services.impl.orders;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import backend.annotations.retry.RetryOnConcurrency;
import backend.dtos.requests.order.CreateOrderRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CompanyOrderResponse;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.dtos.responses.risk.RiskAssessmentResponse;
import backend.dtos.responses.risk.RiskReviewResponse;
import backend.dtos.responses.risk.RiskSignalResponse;
import backend.dtos.requests.risk.RiskDecisionRequest;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.RiskStepUpRequiredException;
import backend.models.core.BundleItem;
import backend.models.core.CommissionRecord;
import backend.models.core.InventoryAdjustment;
import backend.models.core.LocationStock;
import backend.models.core.MarketplaceVendor;
import backend.models.core.Order;
import backend.models.core.OrderCompensation;
import backend.models.core.OrderItem;
import backend.models.core.SubOrder;
import backend.models.enums.SubOrderStatus;
import backend.models.enums.VendorStatus;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.ProductVariant;
import backend.models.core.Subscription;
import backend.models.core.SubscriptionItem;
import backend.models.core.User;
import backend.models.core.Company;
import backend.models.core.Coupon;
import backend.models.core.CouponRedemption;
import backend.models.core.FailedPaymentAttempt;
import backend.models.core.PromotionRedemption;
import backend.models.core.PromotionRule;
import backend.models.core.RiskAssessment;
import backend.models.core.RiskReview;
import backend.dtos.requests.order.ReturnOrderRequest;
import backend.dtos.requests.order.ShipOrderRequest;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompensationStatus;
import backend.models.enums.CompensationType;
import backend.models.enums.DiscountStatus;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.LocationType;
import backend.models.enums.OrderStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.ProductType;
import backend.events.order.GiftCardIssueRequestedEvent;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskMode;
import backend.models.enums.RiskReviewStatus;
import backend.models.core.KitSlot;
import backend.models.core.KitSlotChoice;
import backend.models.core.OrderKitSelection;
import backend.models.core.ProductKit;
import backend.repositories.BundleRepository;
import backend.repositories.CommissionRecordRepository;
import backend.repositories.ProductKitRepository;
import backend.repositories.CouponRepository;
import backend.repositories.VendorBalanceRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.OrderItemRepository;
import backend.repositories.SubOrderRepository;
import backend.services.intf.pricing.CommissionEngine;
import backend.services.intf.promotions.LoyaltyService;
import backend.repositories.CouponPerUserCountRepository;
import backend.repositories.CouponRedemptionRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.PromotionRedemptionRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.FailedPaymentAttemptRepository;
import backend.repositories.RiskAssessmentRepository;
import backend.repositories.RiskReviewRepository;
import backend.repositories.UserRepository;
import backend.repositories.PromotionPerUserCountRepository;
import backend.dtos.requests.return_.BuyerReturnItemRequest;
import backend.dtos.requests.return_.MerchantInitiateReturnRequest;
import backend.models.core.InventoryLocation;
import backend.models.enums.AllocationStrategy;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.services.impl.inventory.StockAlertService;
import backend.dtos.responses.order.OrderStatusHistoryResponse;
import backend.events.notification.NotificationEvent;
import backend.events.order.OrderFulfillmentEvent;
import backend.models.core.OrderStatusHistory;
import backend.models.enums.OrderHistoryEventType;
import backend.repositories.OrderStatusHistoryRepository;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.orders.OrderFulfillmentEventPublisher;
import backend.services.intf.orders.TrackingService;
import backend.services.intf.inventory.AllocationService;
import backend.services.intf.CacheService;
import backend.models.enums.CompanyCapability;
import backend.services.intf.auth.DeviceService;
import backend.services.intf.support.EmailService;
import backend.services.intf.auth.EmailVerificationService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.PaymentService.PaymentIntentResult;
import backend.services.intf.pricing.PricingEngine;
import backend.services.intf.returns.ReturnService;
import backend.services.intf.pricing.RiskEngine;
import backend.services.risk.RiskAssessmentResult;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import backend.configurations.environment.RiskProperties;
import backend.http.ClientInfo;
import backend.http.ClientRequestContext;
import backend.services.pricing.AppliedPromotion;
import backend.services.pricing.CartContext;
import backend.services.pricing.CartLine;
import backend.services.pricing.LineBreakdown;
import backend.services.pricing.PricingResult;
import backend.events.order.OrderReservationExpiredEvent;
import backend.events.order.SseStatusUpdateEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String LOCK_PREFIX = "lock:product:";
    private static final String VARIANT_LOCK_PREFIX = "lock:variant:";
    private static final int LOCK_RETRY_ATTEMPTS = 5;
    private static final long LOCK_RETRY_DELAY_MS = 100;

    @Value("${app.lock.ttl-seconds:60}")
    private long lockTtlSeconds;
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalAmount");

    @Value("${app.order.reservation.ttl-seconds:900}")
    private int reservationTtlSeconds;

    private final OrderRepository orderRepository;
    private final OrderCompensationRepository compensationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final LocationStockRepository locationStockRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryLocationRepository locationRepository;
    private final BundleRepository bundleRepository;
    private final ProductKitRepository kitRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CouponPerUserCountRepository couponPerUserCountRepository;
    private final PromotionRuleRepository promotionRuleRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;
    private final PricingEngine pricingEngine;
    private final PaymentService paymentService;
    private final CacheService cacheService;
    private final StockAlertService stockAlertService;
    private final EmailService emailService;
    private final AllocationService allocationService;
    private final RiskEngine riskEngine;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final FailedPaymentAttemptRepository failedPaymentAttemptRepository;
    private final RiskProperties riskProperties;
    private final DeviceService deviceService;
    private final EmailVerificationService emailVerificationService;
    private final MarketplaceVendorRepository marketplaceVendorRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommissionEngine commissionEngine;
    private final CommissionRecordRepository commissionRecordRepository;
    private final VendorBalanceRepository vendorBalanceRepository;
    private final LoyaltyService loyaltyService;
    private final ActivityEventPublisher activityEventPublisher;
    private final CompanyAccessService companyAccessService;
    private final OrderFulfillmentEventPublisher fulfillmentEventPublisher;
    private final TrackingService trackingService;
    private backend.kafka.producers.NotificationEventPublisher notificationEventPublisher;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private ReturnService returnService;
    private PromotionPerUserCountRepository promotionPerUserCountRepository;
    /** Self-proxy for transactional self-invocation (createOrder orchestrates reserve/attach across separate transactions). */
    private OrderServiceImpl self;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderCompensationRepository compensationRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            LocationStockRepository locationStockRepository,
            InventoryAdjustmentRepository adjustmentRepository,
            InventoryLocationRepository locationRepository,
            BundleRepository bundleRepository,
            ProductKitRepository kitRepository,
            UserRepository userRepository,
            CompanyRepository companyRepository,
            CouponRepository couponRepository,
            CouponRedemptionRepository couponRedemptionRepository,
            CouponPerUserCountRepository couponPerUserCountRepository,
            PromotionRuleRepository promotionRuleRepository,
            PromotionRedemptionRepository promotionRedemptionRepository,
            PricingEngine pricingEngine,
            PaymentService paymentService,
            CacheService cacheService,
            StockAlertService stockAlertService,
            EmailService emailService,
            AllocationService allocationService,
            RiskEngine riskEngine,
            RiskAssessmentRepository riskAssessmentRepository,
            RiskReviewRepository riskReviewRepository,
            FailedPaymentAttemptRepository failedPaymentAttemptRepository,
            RiskProperties riskProperties,
            DeviceService deviceService,
            EmailVerificationService emailVerificationService,
            MarketplaceVendorRepository marketplaceVendorRepository,
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository,
            CommissionEngine commissionEngine,
            CommissionRecordRepository commissionRecordRepository,
            VendorBalanceRepository vendorBalanceRepository,
            LoyaltyService loyaltyService,
            ActivityEventPublisher activityEventPublisher,
            CompanyAccessService companyAccessService,
            OrderFulfillmentEventPublisher fulfillmentEventPublisher,
            TrackingService trackingService) {
        this.orderRepository = orderRepository;
        this.compensationRepository = compensationRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.locationStockRepository = locationStockRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.locationRepository = locationRepository;
        this.bundleRepository = bundleRepository;
        this.kitRepository = kitRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.couponRepository = couponRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.couponPerUserCountRepository = couponPerUserCountRepository;
        this.promotionRuleRepository = promotionRuleRepository;
        this.promotionRedemptionRepository = promotionRedemptionRepository;
        this.pricingEngine = pricingEngine;
        this.paymentService = paymentService;
        this.cacheService = cacheService;
        this.stockAlertService = stockAlertService;
        this.emailService = emailService;
        this.allocationService = allocationService;
        this.riskEngine = riskEngine;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.riskReviewRepository = riskReviewRepository;
        this.failedPaymentAttemptRepository = failedPaymentAttemptRepository;
        this.riskProperties = riskProperties;
        this.deviceService = deviceService;
        this.emailVerificationService = emailVerificationService;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.subOrderRepository = subOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.commissionEngine = commissionEngine;
        this.commissionRecordRepository = commissionRecordRepository;
        this.vendorBalanceRepository = vendorBalanceRepository;
        this.loyaltyService = loyaltyService;
        this.activityEventPublisher = activityEventPublisher;
        this.companyAccessService = companyAccessService;
        this.fulfillmentEventPublisher = fulfillmentEventPublisher;
        this.trackingService = trackingService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setNotificationEventPublisher(backend.kafka.producers.NotificationEventPublisher publisher) {
        this.notificationEventPublisher = publisher;
    }

    /** Setter injection breaks the circular dependency: ReturnService → OrderServiceImpl → ReturnService. */
    @org.springframework.beans.factory.annotation.Autowired
    public void setReturnService(ReturnService returnService) {
        this.returnService = returnService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setPromotionPerUserCountRepository(PromotionPerUserCountRepository repo) {
        this.promotionPerUserCountRepository = repo;
    }

    /**
     * Injects this bean's own proxy so {@code createOrder} can invoke its transactional
     * helpers ({@code reserveOrder}, {@code attachPaymentIntent}, {@code failReservedOrder})
     * across distinct transactions. {@code @Lazy} breaks the self-referential cycle at startup.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy OrderServiceImpl self) {
        this.self = self;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private ApplicationEventPublisher eventPublisher;
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    private StringRedisTemplate stringRedisTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public void setOrderStatusHistoryRepository(OrderStatusHistoryRepository repo) {
        this.orderStatusHistoryRepository = repo;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setStringRedisTemplate(StringRedisTemplate template) {
        this.stringRedisTemplate = template;
    }

    /**
     * Orchestrates order creation across two transactions so the external Stripe call
     * never runs inside the stock-reservation transaction (which would pin a DB connection
     * and hold Redis stock locks across a network round-trip):
     * <ol>
     *   <li>{@code reserveOrder} (T1): validate, reserve stock, persist the RESERVED order.</li>
     *   <li>create the Stripe PaymentIntent with no DB transaction or locks held.</li>
     *   <li>{@code attachPaymentIntent} (T2): persist the intent id and send the receipt.</li>
     * </ol>
     * If the PaymentIntent call fails after T1 commits, {@code failReservedOrder} marks the
     * order FAILED and restores the already-committed stock.
     */
    @Override
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        OrderReservation reservation = self().reserveOrder(userId, request);
        // Terminal outcome inside T1 (e.g. order parked UNDER_REVIEW) needs no PaymentIntent.
        if (reservation.response() != null) {
            return reservation.response();
        }

        Order reserved = reservation.order();
        PaymentIntentResult paymentIntent;
        try {
            paymentIntent = paymentService.createPaymentIntent(
                    reservation.amountInCents(),
                    reservation.currency(),
                    null,
                    Map.of("user_id", String.valueOf(userId),
                           "order_id", String.valueOf(reserved.getId()))
            );
        } catch (Exception e) {
            // The reservation already committed, so rolling back is not an option — restore the
            // committed stock, coupon/loyalty usage, and mark the order FAILED before surfacing the
            // original error. No PaymentIntent exists yet, so there is nothing to cancel.
            failReservedOrderQuietly(reserved, "Payment intent creation failed: " + e.getMessage(), null);
            throw e;
        }

        OrderResponse response;
        try {
            response = self().attachPaymentIntent(reserved, paymentIntent);
        } catch (Exception e) {
            // The PaymentIntent exists at Stripe but could not be persisted on the order. Hand the
            // orphaned intent id to failReservedOrder so it is cancelled (and recorded as a
            // retryable PAYMENT_CANCEL compensation if the cancel itself fails) and the committed
            // stock/coupon/loyalty are restored so the customer is never charged.
            failReservedOrderQuietly(reserved,
                    "Failed to attach payment intent: " + e.getMessage(), paymentIntent.id());
            throw e;
        }

        // Receipt email is best-effort and runs AFTER the PaymentIntent is committed, so an email
        // failure can never roll back the attachment or orphan the intent.
        try {
            emailService.sendOrderReceiptEmail(reservation.email(), reservation.firstName(), response);
        } catch (Exception e) {
            log.warn("[ORDER] Failed to send receipt email for order {}: {}", reserved.getId(), e.getMessage());
        }
        return response;
    }

    /**
     * Invokes {@link #failReservedOrder} through the proxy, swallowing any compensation failure so
     * the caller can still surface the original (payment) error.
     */
    private void failReservedOrderQuietly(Order reserved, String reason, String orphanPaymentIntentId) {
        try {
            self().failReservedOrder(reserved, reason, orphanPaymentIntentId);
        } catch (Exception compEx) {
            log.error("[ORDER] Failed to compensate order {} after payment failure: {}",
                    reserved.getId(), compEx.getMessage());
        }
    }

    /**
     * Returns this bean's transactional proxy for self-invocation, falling back to {@code this}
     * when no proxy was injected (e.g. plain unit tests with no Spring context). In production
     * {@code self} is always wired by {@link #setSelf}, so the helper calls cross real
     * transaction boundaries; in unit tests the fallback simply runs the logic inline.
     */
    private OrderServiceImpl self() {
        return self != null ? self : this;
    }

    /**
     * Transaction T1 of {@link #createOrder}: validates the request, reserves stock under
     * Redis locks, and persists the order as RESERVED. Returns a {@link OrderReservation}
     * describing whether the order is terminal (already has its final response) or pending
     * a PaymentIntent. Never creates a PaymentIntent — that is done by the caller outside
     * this transaction.
     */
    @Transactional
    public OrderReservation reserveOrder(UUID userId, CreateOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<CreateOrderRequest.OrderItemRequest> itemRequests = request.getItems();

        // Premium tier: free users capped at 50 items per order
        if (user.getTier() == backend.models.enums.UserTier.FREE && itemRequests.size() > 50) {
            throw new backend.exceptions.http.PremiumRequiredException(
                    "Free accounts are limited to 50 items per order. Upgrade to Premium for up to 200 items.");
        }

        // Validate: each item must have exactly one of productId, bundleId, or kitId
        for (CreateOrderRequest.OrderItemRequest ir : itemRequests) {
            int typeCount = (ir.getProductId() != null ? 1 : 0)
                    + (ir.getBundleId() != null ? 1 : 0)
                    + (ir.getKitId() != null ? 1 : 0);
            if (typeCount == 0) {
                throw new BadRequestException("Each order item must specify either a productId, a bundleId, or a kitId");
            }
            if (typeCount > 1) {
                throw new BadRequestException("Each order item must specify exactly one of productId, bundleId, or kitId");
            }
        }

        // Resolve fulfillment method and validate address/location before acquiring stock locks
        FulfillmentMethod fulfillmentMethod = request.getFulfillmentMethod() != null
                ? request.getFulfillmentMethod() : FulfillmentMethod.DELIVERY;

        InventoryLocation resolvedPickupLocation = null;
        if (fulfillmentMethod == FulfillmentMethod.DELIVERY) {
            if (!StringUtils.hasText(request.getShipRecipientName())
                    || !StringUtils.hasText(request.getShipStreet())
                    || !StringUtils.hasText(request.getShipCity())
                    || !StringUtils.hasText(request.getShipPostalCode())
                    || !StringUtils.hasText(request.getShipCountry())) {
                throw new BadRequestException(
                        "Shipping address (recipientName, street, city, postalCode, country) is required for delivery orders");
            }
        } else {
            if (request.getPickupLocationId() == null) {
                throw new BadRequestException("A pickup location must be specified for pickup orders");
            }
            resolvedPickupLocation = locationRepository.findById(request.getPickupLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Pickup location not found with id: " + request.getPickupLocationId()));
            if (!resolvedPickupLocation.isActive()) {
                throw new BadRequestException("The selected pickup location is not currently active");
            }
            if (resolvedPickupLocation.getType() == LocationType.WAREHOUSE) {
                throw new BadRequestException("The selected location does not support in-store pickup");
            }
        }

        List<CreateOrderRequest.OrderItemRequest> productItemRequests = itemRequests.stream()
                .filter(i -> i.getBundleId() == null && i.getKitId() == null).toList();
        List<CreateOrderRequest.OrderItemRequest> bundleItemRequests = itemRequests.stream()
                .filter(i -> i.getBundleId() != null).toList();
        List<CreateOrderRequest.OrderItemRequest> kitItemRequests = itemRequests.stream()
                .filter(i -> i.getKitId() != null).toList();

        // Resolve and validate bundles before locking (fail fast)
        Map<UUID, ProductBundle> resolvedBundles = new HashMap<>();
        for (CreateOrderRequest.OrderItemRequest ir : bundleItemRequests) {
            UUID bundleId = ir.getBundleId();
            if (resolvedBundles.containsKey(bundleId)) continue;
            ProductBundle bundle = bundleRepository.findById(bundleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bundle not found with id: " + bundleId));
            if (bundle.getStatus() != backend.models.enums.ProductStatus.ACTIVE || !bundle.isListed()) {
                throw new BadRequestException("Bundle '" + bundle.getName() + "' is not available for purchase");
            }
            resolvedBundles.put(bundleId, bundle);
        }

        // Resolve and validate kits before locking (fail fast)
        Map<UUID, ProductKit> resolvedKits = new HashMap<>();
        for (CreateOrderRequest.OrderItemRequest ir : kitItemRequests) {
            UUID kitId = ir.getKitId();
            if (resolvedKits.containsKey(kitId)) continue;
            ProductKit kit = kitRepository.findById(kitId)
                    .orElseThrow(() -> new ResourceNotFoundException("Kit not found with id: " + kitId));
            if (kit.getStatus() != backend.models.enums.ProductStatus.ACTIVE || !kit.isListed()) {
                throw new BadRequestException("Kit '" + kit.getName() + "' is not available for purchase");
            }
            resolvedKits.put(kitId, kit);
        }

        // Collect product IDs from product items
        List<UUID> productIds = productItemRequests.stream()
                .map(CreateOrderRequest.OrderItemRequest::getProductId)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new))
                .stream().toList();

        // Merge constituent product IDs from all bundles into the lock set (sorted, deduplicated)
        java.util.TreeSet<UUID> allProductIdSet = new java.util.TreeSet<>(productIds);
        for (ProductBundle bundle : resolvedBundles.values()) {
            for (BundleItem bi : bundle.getItems()) {
                allProductIdSet.add(bi.getProduct().getId());
            }
        }
        // Merge constituent product IDs from all kit selections into the lock set
        for (CreateOrderRequest.OrderItemRequest ir : kitItemRequests) {
            if (ir.getKitSelections() != null) {
                for (CreateOrderRequest.KitSelectionRequest sel : ir.getKitSelections()) {
                    if (sel.getProductId() != null) allProductIdSet.add(sel.getProductId());
                }
            }
        }
        List<UUID> allProductIds = new ArrayList<>(allProductIdSet);

        // Reject same productId with different variantIds — ambiguous, can't be safely merged
        Map<UUID, UUID> seenProductVariant = new HashMap<>();
        for (CreateOrderRequest.OrderItemRequest item : productItemRequests) {
            if (item.getProductId() == null) continue;
            UUID existingVariant = seenProductVariant.put(item.getProductId(), item.getVariantId());
            if (existingVariant != null && !Objects.equals(existingVariant, item.getVariantId())) {
                throw new BadRequestException(
                    "Product id " + item.getProductId() + " appears with multiple variants in the same order — submit as separate orders");
            }
        }

        Map<UUID, Integer> quantityMap = new HashMap<>();
        Map<UUID, UUID> variantMap = new HashMap<>();  // productId -> variantId
        for (CreateOrderRequest.OrderItemRequest item : productItemRequests) {
            quantityMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            if (item.getVariantId() != null) {
                variantMap.put(item.getProductId(), item.getVariantId());
            }
        }

        // Collect variant IDs that need locks (sorted for deadlock prevention)
        List<UUID> variantIdsToLock = variantMap.values().stream().sorted().toList();

        String lockToken = UUID.randomUUID().toString();
        List<String> acquiredLocks = new ArrayList<>();
        List<Object[]> decrementedProducts = new ArrayList<>();     // [UUID id, int qty]
        List<Object[]> decrementedVariants = new ArrayList<>();     // [UUID id, int qty]
        List<Object[]> decrementedLocationStocks = new ArrayList<>(); // [UUID id, int qty]
        UUID savedOrderId = null; // set after order is persisted; used for loyalty point restore on failure
        String appliedCouponCode = null;  // set after per-user count is incremented; must be visible in catch blocks
        Coupon appliedCoupon = null;      // same — needed for count release on any failure path
        // [product, variant (null for product-level), prevStock, newStock]
        record PurchaseRecord(Product prod, ProductVariant var, int prevStock, int newStock) {}
        List<PurchaseRecord> purchaseRecords = new ArrayList<>();

        try {
            acquireLocks(allProductIds, lockToken, acquiredLocks);
            acquireVariantLocks(variantIdsToLock, lockToken, acquiredLocks);

            List<Product> products = productRepository.findAllById(productIds);
            if (products.size() != productIds.size()) {
                throw new ResourceNotFoundException("One or more products not found");
            }

            Map<UUID, Product> productMap = new HashMap<>();
            for (Product p : products) {
                productMap.put(p.getId(), p);
            }

            for (Product product : products) {
                if (product.getStatus() != ProductStatus.ACTIVE) {
                    throw new BadRequestException("Product '" + product.getName() + "' is not available for purchase");
                }
                if (!product.isPurchasable()) {
                    throw new BadRequestException("Product '" + product.getName() + "' is not available for purchase");
                }
                boolean hasVariants = variantRepository.existsByProductId(product.getId());
                UUID requestedVariantId = variantMap.get(product.getId());
                if (hasVariants && requestedVariantId == null) {
                    throw new BadRequestException("Product '" + product.getName() + "' has variants — specify a variantId");
                }
                if (!hasVariants && requestedVariantId != null) {
                    throw new BadRequestException("Product '" + product.getName() + "' has no variants");
                }
            }

            AllocationStrategy strategy = request.getAllocationStrategy() != null
                    ? request.getAllocationStrategy() : AllocationStrategy.HIGHEST_STOCK;
            Double buyerLat = request.getBuyerLatitude();
            Double buyerLng = request.getBuyerLongitude();

            List<OrderItem> orderItems = new ArrayList<>();

            for (UUID productId : productIds) {
                Product product = productMap.get(productId);
                int qty = quantityMap.get(productId);
                UUID variantId = variantMap.get(productId);

                OrderItem item = new OrderItem();
                item.setProduct(product);
                item.setProductName(product.getName());
                item.setQuantity(qty);

                // Gift card products have no stock — skip all inventory reservation
                if (product.getProductType() == ProductType.GIFT_CARD) {
                    item.setUnitPrice(product.getPrice());
                    orderItems.add(item);
                    continue;
                }

                if (variantId != null) {
                    ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                            .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));

                    if (!variant.isPurchasable()) {
                        throw new BadRequestException("Variant is not available for purchase");
                    }

                    int prevVariantStock = variant.getStock() != null ? variant.getStock() : 0;
                    int updated = variantRepository.decrementStock(variantId, qty);
                    if (updated == 0) {
                        if (variant.isBackorderEnabled()) {
                            item.setFulfillmentStatus(FulfillmentStatus.BACKORDERED);
                        } else if (variant.isPreorderEnabled() && variant.getProduct().getCompany().isPreordersEnabled()) {
                            item.setFulfillmentStatus(FulfillmentStatus.PREORDERED);
                        } else {
                            safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                            throw new ConflictException("Insufficient stock for variant of product '" + product.getName() + "'");
                        }
                    } else {
                        decrementedVariants.add(new Object[]{variantId, qty});
                        // Only record audit/alert entries for tracked stock (non-null); untracked
                        // products (stock=null) have no stock to report.
                        if (variant.getStock() != null) {
                            purchaseRecords.add(new PurchaseRecord(product, variant, prevVariantStock, prevVariantStock - qty));
                        }
                    }

                    item.setVariant(variant);
                    item.setVariantTitle(buildVariantTitle(variant));
                    item.setVariantSku(variant.getSku());
                    item.setUnitPrice(variant.getPrice());
                } else {
                    int prevProductStock = product.getStock() != null ? product.getStock() : 0;
                    int updated = productRepository.decrementStock(product.getId(), qty);
                    if (updated == 0) {
                        if (product.isBackorderEnabled()) {
                            item.setFulfillmentStatus(FulfillmentStatus.BACKORDERED);
                        } else if (product.isPreorderEnabled() && product.getCompany().isPreordersEnabled()) {
                            item.setFulfillmentStatus(FulfillmentStatus.PREORDERED);
                        } else {
                            safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                            throw new ConflictException("Insufficient stock for product '" + product.getName() + "'");
                        }
                    } else {
                        decrementedProducts.add(new Object[]{product.getId(), qty});
                        if (product.getStock() != null) {
                            purchaseRecords.add(new PurchaseRecord(product, null, prevProductStock, prevProductStock - qty));
                        }
                    }

                    item.setUnitPrice(product.getPrice());
                }

                // Location stock — skip for backordered/preordered items (no stock was reserved)
                if (item.getFulfillmentStatus() != FulfillmentStatus.BACKORDERED
                        && item.getFulfillmentStatus() != FulfillmentStatus.PREORDERED) {
                    List<AllocationService.AllocationResult> allocResults =
                            allocationService.allocate(productId, variantId, qty, strategy, buyerLat, buyerLng);

                    if (allocResults.isEmpty() && hasAnyLocationStock(productId, variantId)) {
                        safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                        throw new ConflictException("Insufficient location stock for product '" + product.getName() + "'");
                    }

                    for (AllocationService.AllocationResult r : allocResults) {
                        decrementedLocationStocks.add(new Object[]{r.locationStockId(), r.allocatedQty()});
                    }

                    if (!allocResults.isEmpty()) {
                        InventoryLocation primaryLoc = allocResults.get(0).location();
                        item.setFulfillmentLocation(primaryLoc);
                        item.setFulfillmentLocationName(primaryLoc.getName());
                    }
                }

                orderItems.add(item);
            }

            // Process bundle items (inside lock block — all constituent product IDs are already locked)
            for (CreateOrderRequest.OrderItemRequest req : bundleItemRequests) {
                ProductBundle bundle = resolvedBundles.get(req.getBundleId());
                int bundleQty = req.getQuantity();

                OrderItem bundleItem = new OrderItem();
                bundleItem.setBundle(bundle);
                bundleItem.setBundleName(bundle.getName());
                bundleItem.setProduct(null);
                bundleItem.setQuantity(bundleQty);
                bundleItem.setUnitPrice(bundle.getPrice());
                bundleItem.setProductName(bundle.getName());

                // Per-bundle decrement tracking: allows restoring only this bundle's
                // constituents if we pivot to a preorder without rolling back the whole order.
                List<Object[]> bundleDecrProd = new ArrayList<>();
                List<Object[]> bundleDecrVar  = new ArrayList<>();
                boolean bundlePreordered = false;

                for (BundleItem bi : bundle.getItems()) {
                    if (bi.getProduct().getStatus() != ProductStatus.ACTIVE || !bi.getProduct().isPurchasable()) {
                        safeRestoreAll(bundleDecrProd, bundleDecrVar, Collections.emptyList());
                        safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                        throw new BadRequestException("Bundle '" + bundle.getName() +
                            "' contains unavailable product '" + bi.getProduct().getName() + "'");
                    }
                    if (bi.getVariant() != null && !bi.getVariant().isPurchasable()) {
                        safeRestoreAll(bundleDecrProd, bundleDecrVar, Collections.emptyList());
                        safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                        throw new BadRequestException("Bundle '" + bundle.getName() +
                            "' contains an unavailable variant of '" + bi.getProduct().getName() + "'");
                    }
                    int totalQty = bundleQty * bi.getQuantity();

                    int prevStock, updated;
                    if (bi.getVariant() != null) {
                        prevStock = bi.getVariant().getStock() != null ? bi.getVariant().getStock() : 0;
                        updated = variantRepository.decrementStock(bi.getVariant().getId(), totalQty);
                    } else {
                        prevStock = bi.getProduct().getStock() != null ? bi.getProduct().getStock() : 0;
                        updated = productRepository.decrementStock(bi.getProduct().getId(), totalQty);
                    }

                    if (updated == 0) {
                        if (bundle.isPreorderEnabled() && bundle.getCompany().isPreordersEnabled()) {
                            // Restore only this bundle's already-decremented constituents;
                            // the rest of the order remains intact.
                            safeRestoreAll(bundleDecrProd, bundleDecrVar, Collections.emptyList());
                            bundlePreordered = true;
                            break;
                        }
                        safeRestoreAll(bundleDecrProd, bundleDecrVar, Collections.emptyList());
                        safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                        throw new ConflictException("Insufficient stock for bundle '" + bundle.getName() +
                                "' (product: '" + bi.getProduct().getName() + "')");
                    }

                    if (bi.getVariant() != null) {
                        bundleDecrVar.add(new Object[]{bi.getVariant().getId(), totalQty});
                    } else {
                        bundleDecrProd.add(new Object[]{bi.getProduct().getId(), totalQty});
                    }
                    // Only audit tracked stock; untracked (null) items are skipped.
                    Integer biActualStock = bi.getVariant() != null ? bi.getVariant().getStock() : bi.getProduct().getStock();
                    if (biActualStock != null) {
                        purchaseRecords.add(new PurchaseRecord(bi.getProduct(), bi.getVariant(), biActualStock, biActualStock - totalQty));
                    }
                }

                if (bundlePreordered) {
                    bundleItem.setFulfillmentStatus(FulfillmentStatus.PREORDERED);
                } else {
                    // All constituents decremented successfully — promote to global tracking
                    // so restoreItemStock / safeRestoreAll cover them on order failure.
                    decrementedProducts.addAll(bundleDecrProd);
                    decrementedVariants.addAll(bundleDecrVar);
                }

                orderItems.add(bundleItem);
            }

            // Process kit items (inside lock block — all constituent product IDs already locked)
            for (CreateOrderRequest.OrderItemRequest req : kitItemRequests) {
                ProductKit kit = resolvedKits.get(req.getKitId());
                int kitQty = req.getQuantity();
                List<CreateOrderRequest.KitSelectionRequest> selections =
                        req.getKitSelections() != null ? req.getKitSelections() : List.of();

                validateKitSelections(kit, selections);

                OrderItem kitItem = new OrderItem();
                kitItem.setKit(kit);
                kitItem.setKitName(kit.getName());
                kitItem.setProduct(null);
                kitItem.setQuantity(kitQty);
                kitItem.setProductName(kit.getName());

                List<Object[]> kitDecrProd = new ArrayList<>();
                List<Object[]> kitDecrVar  = new ArrayList<>();
                BigDecimal kitLineUnitPrice = BigDecimal.ZERO;
                List<OrderKitSelection> orderSelections = new ArrayList<>();

                for (CreateOrderRequest.KitSelectionRequest sel : selections) {
                    KitSlot slot = kit.getSlots().stream()
                            .filter(s -> s.getId().equals(sel.getSlotId()))
                            .findFirst()
                            .orElseThrow(() -> new BadRequestException("Unknown slot id: " + sel.getSlotId()));

                    KitSlotChoice choice = slot.getChoices().stream()
                            .filter(c -> c.getProduct().getId().equals(sel.getProductId())
                                    && Objects.equals(
                                            c.getVariant() != null ? c.getVariant().getId() : null,
                                            sel.getVariantId()))
                            .findFirst()
                            .orElseThrow(() -> new BadRequestException(
                                    "Invalid choice for slot '" + slot.getName() + "'"));

                    Product selProd    = choice.getProduct();
                    // Defensive: the choice's product must belong to the same company as the kit.
                    // This should always hold if kit setup is correct, but an explicit check
                    // prevents a misconfigured kit from letting an order reference a foreign product.
                    if (!selProd.getCompany().getId().equals(kit.getCompany().getId())) {
                        throw new BadRequestException("Kit configuration error: product '"
                                + selProd.getName() + "' does not belong to the kit's company");
                    }
                    ProductVariant selVar = choice.getVariant();

                    if (selProd.getStatus() != backend.models.enums.ProductStatus.ACTIVE || !selProd.isPurchasable()) {
                        safeRestoreAll(kitDecrProd, kitDecrVar, Collections.emptyList());
                        safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                        throw new BadRequestException("Kit '" + kit.getName()
                                + "' — product '" + selProd.getName() + "' is not available");
                    }
                    if (selVar != null && !selVar.isPurchasable()) {
                        safeRestoreAll(kitDecrProd, kitDecrVar, Collections.emptyList());
                        safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                        throw new BadRequestException("Kit '" + kit.getName()
                                + "' — selected variant of '" + selProd.getName() + "' is not available");
                    }

                    int totalUnits = kitQty * sel.getQuantity();
                    int updated;
                    if (selVar != null) {
                        updated = variantRepository.decrementStock(selVar.getId(), totalUnits);
                    } else {
                        updated = productRepository.decrementStock(selProd.getId(), totalUnits);
                    }

                    if (updated == 0) {
                        safeRestoreAll(kitDecrProd, kitDecrVar, Collections.emptyList());
                        safeRestoreAll(decrementedProducts, decrementedVariants, decrementedLocationStocks);
                        throw new ConflictException("Insufficient stock for '" + selProd.getName()
                                + "' in kit '" + kit.getName() + "'");
                    }

                    if (selVar != null) kitDecrVar.add(new Object[]{selVar.getId(), totalUnits});
                    else               kitDecrProd.add(new Object[]{selProd.getId(), totalUnits});

                    Integer actualStock = selVar != null ? selVar.getStock() : selProd.getStock();
                    if (actualStock != null) {
                        purchaseRecords.add(new PurchaseRecord(selProd, selVar, actualStock, actualStock - totalUnits));
                    }

                    BigDecimal base = selVar != null ? selVar.getPrice() : selProd.getPrice();
                    BigDecimal delta = choice.getPriceDelta() != null ? choice.getPriceDelta() : BigDecimal.ZERO;
                    BigDecimal effectiveUnit = base.add(delta);
                    kitLineUnitPrice = kitLineUnitPrice.add(effectiveUnit.multiply(BigDecimal.valueOf(sel.getQuantity())));

                    String variantTitle = selVar != null ? buildVariantTitle(selVar) : null;
                    OrderKitSelection oks = new OrderKitSelection();
                    oks.setOrderItem(kitItem);
                    oks.setSlot(slot);
                    oks.setSlotName(slot.getName());
                    oks.setProduct(selProd);
                    oks.setProductName(selProd.getName());
                    oks.setVariant(selVar);
                    oks.setVariantTitle(variantTitle);
                    oks.setVariantSku(selVar != null ? selVar.getSku() : null);
                    oks.setQuantity(sel.getQuantity());
                    oks.setUnitPrice(effectiveUnit);
                    orderSelections.add(oks);
                }

                decrementedProducts.addAll(kitDecrProd);
                decrementedVariants.addAll(kitDecrVar);

                kitItem.setUnitPrice(kitLineUnitPrice);
                kitItem.setKitSelections(orderSelections);
                orderItems.add(kitItem);
            }

            String currency = request.getCurrency() != null ? request.getCurrency().toLowerCase() : "usd";

            // --- Build cart lines for the pricing engine (products + bundles).
            List<CartLine> cartLines = new ArrayList<>();
            List<OrderItem> itemsInLineOrder = new ArrayList<>();
            int lineIdx = 0;
            for (OrderItem item : orderItems) {
                if (item.getProduct() != null) {
                    BigDecimal basePrice = item.getVariant() != null
                            ? item.getVariant().getPrice()
                            : item.getProduct().getPrice();
                    cartLines.add(new CartLine(
                            lineIdx++,
                            item.getProduct().getId(),
                            item.getVariant() != null ? item.getVariant().getId() : null,
                            item.getQuantity(),
                            basePrice,
                            item.getProduct().getCompany().getId(),
                            null));
                    itemsInLineOrder.add(item);
                } else if (item.getBundle() != null) {
                    cartLines.add(new CartLine(
                            lineIdx++,
                            null,
                            null,
                            item.getQuantity(),
                            item.getUnitPrice(),
                            item.getBundle().getCompany().getId(),
                            item.getBundle().getId()));
                    itemsInLineOrder.add(item);
                } else if (item.getKit() != null) {
                    // Kit lines pass through the pricing engine as a single line at their computed price.
                    // Promotion rules do not apply to kit lines (same treatment as bundles).
                    cartLines.add(new CartLine(
                            lineIdx++,
                            null,
                            null,
                            item.getQuantity(),
                            item.getUnitPrice(),
                            item.getKit().getCompany().getId(),
                            null));
                    itemsInLineOrder.add(item);
                }
            }

            // --- Coupon pre-validation: hard-fail on existence, status, window, per-user cap.
            //     minOrderAmount is enforced by the engine against the post-promotion subtotal.
            if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
                String code = request.getCouponCode().trim().toUpperCase();
                Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                        .orElseThrow(() -> new BadRequestException("Coupon code '" + code + "' is not valid"));

                Instant couponNow = Instant.now();
                boolean expired   = coupon.getEndDate()   != null && coupon.getEndDate().isBefore(couponNow);
                boolean notStarted = coupon.getStartDate() != null && coupon.getStartDate().isAfter(couponNow);
                if (coupon.getStatus() == DiscountStatus.DISABLED || expired || notStarted) {
                    throw new BadRequestException("Coupon '" + code + "' is not currently valid");
                }

                if (coupon.getMaxUsesPerUser() != null) {
                    int claimed = couponPerUserCountRepository.tryIncrementUserCount(
                            coupon.getId(), userId, coupon.getMaxUsesPerUser());
                    if (claimed == 0) {
                        throw new BadRequestException("You have already used coupon '"
                                + code + "' the maximum number of times");
                    }
                }
                appliedCouponCode = code;
                appliedCoupon = coupon;
            }

            // --- Invoke the pricing engine on product lines ---
            Set<UUID> userSegmentIds = new HashSet<>(userRepository.findSegmentIdsByUserId(userId));
            CartContext ctx = new CartContext(
                    cartLines,
                    userId,
                    userSegmentIds,
                    currency.toUpperCase(),
                    appliedCouponCode,
                    null,
                    Instant.now());
            PricingResult pricing = pricingEngine.quote(ctx);

            // If a coupon was supplied but the engine couldn't apply it (e.g. minOrderAmount not met
            // against the post-promotion subtotal), surface that as a hard error rather than silently
            // charging full price. The engine emits a warning describing why.
            if (appliedCouponCode != null && pricing.appliedCouponCode() == null) {
                String reason = pricing.warnings().stream()
                        .filter(w -> {
                            String lw = w.toLowerCase();
                            return lw.contains("coupon") || lw.contains("cart below");
                        })
                        .findFirst()
                        .orElse("Coupon '" + appliedCouponCode + "' is not applicable to this order");
                throw new BadRequestException(reason);
            }

            // Map per-line engine output back onto OrderItems (products and bundles).
            for (LineBreakdown lb : pricing.lines()) {
                OrderItem item = itemsInLineOrder.get(lb.index());
                BigDecimal basePrice = lb.unitBasePrice();
                BigDecimal perUnit = lb.quantity() > 0
                        ? lb.savings().divide(BigDecimal.valueOf(lb.quantity()), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                item.setUnitPrice(basePrice.subtract(perUnit).max(BigDecimal.ZERO));
                item.setDiscountAmount(perUnit);
                item.setPromotionSavings(lb.savings());
                if (lb.appliedRuleIds() != null && !lb.appliedRuleIds().isEmpty()) {
                    StringBuilder csv = new StringBuilder();
                    for (UUID id : lb.appliedRuleIds()) {
                        if (id == null) continue;
                        if (csv.length() > 0) csv.append(',');
                        csv.append(id);
                    }
                    item.setAppliedRuleIdsCsv(csv.length() > 0 ? csv.toString() : null);
                }
            }

            BigDecimal couponDiscountAmount = pricing.couponSavings();
            BigDecimal promotionSavings = pricing.promotionSavings();
            BigDecimal finalTotal = pricing.finalTotal();

            // --- Loyalty point redemption: pre-validate and compute discount before Stripe charge ---
            // The actual atomic deduction is deferred until after the order entity is saved (orderId needed).
            int loyaltyPointsToRedeem = request.getLoyaltyPointsToRedeem() != null
                    ? request.getLoyaltyPointsToRedeem() : 0;
            long loyaltyDiscountCents = 0L;
            UUID loyaltyCompanyId = null;
            if (loyaltyPointsToRedeem > 0) {
                loyaltyCompanyId = resolveOrderCompanyId(orderItems);
                if (loyaltyCompanyId == null) {
                    throw new BadRequestException(
                            "Cannot redeem loyalty points: unable to identify the company for this order");
                }
                var quote = loyaltyService.getRedemptionQuote(userId, loyaltyCompanyId, loyaltyPointsToRedeem);
                if (!quote.isValid()) {
                    throw new BadRequestException("Cannot redeem loyalty points: " + quote.getInvalidReason());
                }
                loyaltyDiscountCents = quote.getDiscountCents();
                finalTotal = finalTotal.subtract(BigDecimal.valueOf(loyaltyDiscountCents).movePointLeft(2))
                        .max(BigDecimal.ZERO);
            }

            // --- Premium discount: 5% off the entire order for Premium subscribers ---
            long premiumDiscountCents = 0L;
            if (user.getTier() == backend.models.enums.UserTier.PREMIUM) {
                premiumDiscountCents = finalTotal.multiply(new BigDecimal("0.05"))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();
                finalTotal = finalTotal.subtract(BigDecimal.valueOf(premiumDiscountCents).movePointLeft(2))
                        .max(BigDecimal.ZERO);
            }

            long amountInCents = finalTotal.multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            // --- Marketplace: stamp vendorId on items BEFORE cascade-save so it's persisted
            //     atomically with the order. SubOrders are wired up after the order is saved.
            boolean hasMarketplaceItems = stampVendorIds(orderItems);

            // Totals reconciliation: cross-check the pricing engine's finalTotal against
            // sum(line subtotal) - couponDiscount - loyaltyDiscount. Treat any drift
            // larger than a small rounding allowance as a logged anomaly so a recurring
            // engine bug is visible in operations, without failing the customer's order.
            reconcileOrderTotal(orderItems, finalTotal, couponDiscountAmount, loyaltyDiscountCents);

            Order order = new Order();
            order.setUser(user);
            order.setTotalAmount(finalTotal);
            order.setCouponCode(pricing.appliedCouponCode());
            order.setCouponDiscountAmount(couponDiscountAmount);
            order.setPromotionSavings(promotionSavings);
            order.setCoupon(appliedCoupon);
            order.setCurrency(currency);
            order.setStatus(OrderStatus.RESERVED);
            order.setPriorityOrder(user.getTier() == backend.models.enums.UserTier.PREMIUM);
            order.setPremiumDiscountCents(premiumDiscountCents);

            // Fulfillment method + address snapshot
            order.setFulfillmentMethod(fulfillmentMethod);
            if (fulfillmentMethod == FulfillmentMethod.DELIVERY) {
                order.setShipRecipientName(request.getShipRecipientName());
                order.setShipStreet(request.getShipStreet());
                order.setShipStreet2(request.getShipStreet2());
                order.setShipCity(request.getShipCity());
                order.setShipState(request.getShipState());
                order.setShipPostalCode(request.getShipPostalCode());
                order.setShipCountry(request.getShipCountry());
                order.setShipPhoneNumber(request.getShipPhoneNumber());
            } else {
                order.setPickupLocation(resolvedPickupLocation);
                order.setPickupLocationName(resolvedPickupLocation.getName());
            }

            // Stamp every item with the order-level fulfillment method
            for (OrderItem item : orderItems) {
                item.setFulfillmentMethod(fulfillmentMethod);
                if (fulfillmentMethod == FulfillmentMethod.PICKUP) {
                    item.setFulfillmentLocation(resolvedPickupLocation);
                    item.setFulfillmentLocationName(resolvedPickupLocation.getName());
                }
                item.setOrder(order);
            }
            order.setItems(orderItems);

            order = orderRepository.save(order);

            savedOrderId = order.getId(); // UUID

            // Publish one ORDER activity event per line item (fire-and-forget after commit).
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() == null) continue;
                UUID mkt = item.getProduct().getMarketplaceId();
                if (mkt == null) continue;
                activityEventPublisher.publish(new UserActivityEvent(
                        userId, null, item.getProduct().getId(), mkt, ActivityType.ORDER, Instant.now()));
            }

            // --- Atomically deduct loyalty points now that the order has an ID ---
            if (loyaltyPointsToRedeem > 0) {
                loyaltyService.applyRedemption(userId, loyaltyCompanyId, order.getId(), loyaltyPointsToRedeem);
                order.setLoyaltyPointsApplied(loyaltyPointsToRedeem);
                order.setLoyaltyDiscountCents(loyaltyDiscountCents);
                order = orderRepository.save(order);
            }

            // --- Create per-vendor SubOrders for marketplace carts ---
            if (hasMarketplaceItems) {
                order.setMarketplaceOrder(true);
                createSubOrders(order, orderItems);
                orderRepository.save(order);
            }

            // Atomically increment coupon usedCount and record redemption
            if (appliedCoupon != null) {
                int incremented = couponRepository.tryIncrementUsedCount(appliedCoupon.getId(), Instant.now(), DiscountStatus.ACTIVE);
                if (incremented == 0) {
                    throw new ConflictException("Coupon '" + appliedCouponCode + "' is no longer valid or has reached its usage limit. Please try another.");
                }
                CouponRedemption redemption = new CouponRedemption();
                redemption.setCoupon(appliedCoupon);
                redemption.setOrder(order);
                redemption.setUser(user);
                redemption.setDiscountAmount(couponDiscountAmount);
                redemption.setRedeemedAt(Instant.now());
                ClientInfo clientInfoForCoupon = ClientRequestContext.get();
                if (clientInfoForCoupon != null && clientInfoForCoupon.ip() != null) {
                    redemption.setIp(clientInfoForCoupon.ip());
                }
                couponRedemptionRepository.save(redemption);
            }

            // Atomically increment each fired promotion rule's usedCount + write a redemption row.
            // Losers of a concurrent race (maxUses cap exhausted between quote and here) throw
            // ConflictException; the caller's catch block restores stock via safeRestoreAll.
            if (!pricing.appliedPromotions().isEmpty()) {
                Instant redeemedAt = Instant.now();
                for (AppliedPromotion ap : pricing.appliedPromotions()) {
                    int updated = promotionRuleRepository.tryIncrementUsedCount(ap.ruleId());
                    if (updated == 0) {
                        throw new ConflictException("Promotion '" + ap.name()
                                + "' has reached its usage limit. Please try again.");
                    }
                    PromotionRule ruleRef = promotionRuleRepository.getReferenceById(ap.ruleId());
                    if (ruleRef.getMaxUsesPerUser() != null) {
                        int claimed = promotionPerUserCountRepository.tryIncrementUserCount(
                                ap.ruleId(), userId, ruleRef.getMaxUsesPerUser());
                        if (claimed == 0) {
                            throw new ConflictException("Promotion '" + ap.name()
                                    + "' has reached your personal usage limit.");
                        }
                    }
                    Company funder = companyRepository.getReferenceById(ap.fundedByCompanyId());
                    PromotionRedemption redemption = new PromotionRedemption();
                    redemption.setRule(ruleRef);
                    redemption.setOrder(order);
                    redemption.setUser(user);
                    redemption.setDiscountAmount(ap.savings());
                    redemption.setFundedByCompany(funder);
                    redemption.setRedeemedAt(redeemedAt);
                    promotionRedemptionRepository.save(redemption);
                }
            }

            // Invalidate 1h hot-product demand cache for all companies in this order.
            // The next API call within the TTL will recompute from the DB (now including this order).
            orderItems.stream()
                    .filter(item -> item.getProduct() != null)
                    .map(item -> item.getProduct().getCompany().getId())
                    .distinct()
                    .forEach(cid -> cacheService.delete("demand:hot:1h:" + cid));

            // Write reservation manifest — holds stock in Redis for reservationTtlSeconds (default 15 min).
            // Released on all terminal paths: payment success, payment failure, stale-order compensation.
            writeReservation(order.getId(), decrementedProducts, decrementedVariants, decrementedLocationStocks);

            // Record PURCHASE adjustments — order is now persisted so orderId is set.
            // previousStock is captured from the in-memory entity while the lock is held: no race condition.
            List<InventoryAdjustment> purchaseAdjs = new ArrayList<>();
            for (PurchaseRecord pr : purchaseRecords) {
                InventoryAdjustment adj = new InventoryAdjustment();
                adj.setProduct(pr.prod());
                adj.setVariant(pr.var());
                adj.setDelta(pr.newStock() - pr.prevStock()); // negative (e.g. -3)
                adj.setPreviousStock(pr.prevStock());
                adj.setNewStock(pr.newStock());
                adj.setReason(AdjustmentReason.PURCHASE);
                adj.setNote("Order #" + order.getId());
                adj.setOrderId(order.getId());
                purchaseAdjs.add(adj);
            }
            adjustmentRepository.saveAll(purchaseAdjs);

            // Low stock alerts — uses data already captured in purchaseRecords (no extra queries)
            for (PurchaseRecord pr : purchaseRecords) {
                Integer threshold = pr.var() != null
                        ? pr.var().getLowStockThreshold()
                        : pr.prod().getLowStockThreshold();
                stockAlertService.checkAndAlert(
                        pr.prod().getId(), pr.prod().getName(),
                        pr.var() != null ? pr.var().getId() : null,
                        pr.var() != null ? pr.var().getSku() : null,
                        pr.newStock(), threshold);
            }

            // --- Risk / fraud evaluation. Assessment is always persisted (SHADOW + ENFORCE);
            //     ENFORCE flips the order to UNDER_REVIEW or raises a step-up exception.
            RiskAssessment persistedAssessment = runRiskAssessment(
                    user, order, userSegmentIds, pricing,
                    request.getRiskVerificationToken());
            // riskAssessmentId is a loose FK still typed Long in Order entity — not stored until entity migrates
            // order.setRiskAssessmentId(persistedAssessment.getId());
            order.setRiskDecision(persistedAssessment.getDecision());
            order.setRiskScore(persistedAssessment.getScore());
            if (order.getStatus() == OrderStatus.UNDER_REVIEW) {
                // Block path: order is parked for merchant review. Stock stays reserved;
                // stale-order scheduler auto-releases if no decision within the TTL.
                Order savedForReview = orderRepository.save(order);
                recordHistory(savedForReview, OrderHistoryEventType.STATUS_CHANGED, null, "Order held for review");
                publishSseEvent(savedForReview, "Order held for review", "status_update");
                OrderResponse response = toResponse(savedForReview);
                emailService.sendOrderReceiptEmail(user.getEmail(), user.getFirstName(), response);
                return OrderReservation.terminal(response);
            }

            // Persist the RESERVED order (with risk fields) and hand back to the caller, which
            // creates the Stripe PaymentIntent OUTSIDE this transaction so a slow external call
            // never pins this DB connection or holds the stock locks.
            order = orderRepository.save(order);
            return OrderReservation.pending(order, amountInCents, currency,
                    user.getEmail(), user.getFirstName());
        } catch (ConflictException | ResourceNotFoundException | BadRequestException e) {
            // appliedCoupon is set only after tryIncrementUserCount succeeds (claimed > 0).
            // If it is non-null here, the per-user counter was incremented and must be undone.
            if (appliedCoupon != null) {
                releaseCouponCountIfNeeded(appliedCoupon, userId);
            }
            throw e;
        } catch (Exception e) {
            // Same guard for unexpected failures (payment error, DB error, etc.)
            if (appliedCoupon != null) {
                releaseCouponCountIfNeeded(appliedCoupon, userId);
            }
            // Look up the persisted order so safeRestoreAll can attach compensation rows
            // (these become queryable retries instead of silent stock leaks). If the
            // order wasn't saved or the lookup itself fails, safeRestoreAll falls back
            // to a high-signal log entry.
            Order persistedOrder = null;
            if (savedOrderId != null) {
                try {
                    persistedOrder = orderRepository.findById(savedOrderId).orElse(null);
                } catch (Exception lookupEx) {
                    log.warn("Could not load order {} for compensation tagging: {}",
                            savedOrderId, lookupEx.getMessage());
                }
            }
            safeRestoreAll(persistedOrder, decrementedProducts, decrementedVariants, decrementedLocationStocks);
            if (savedOrderId != null) {
                try { loyaltyService.restoreRedeemedPoints(savedOrderId); } catch (Exception ex) {
                    log.error("Failed to restore loyalty points for order {}: {}", savedOrderId, ex.getMessage());
                }
            }
            throw e;
        } finally {
            releaseLocks(acquiredLocks, lockToken);
        }
    }

    /**
     * Result of {@link #reserveOrder}. Exactly one of the two shapes is populated:
     * <ul>
     *   <li><b>terminal</b> — {@code response} is set; the order reached its final state inside
     *       T1 (e.g. parked UNDER_REVIEW) and needs no PaymentIntent.</li>
     *   <li><b>pending</b> — {@code orderId}/{@code amountInCents}/{@code currency} are set; the
     *       caller must create a PaymentIntent and attach it via {@link #attachPaymentIntent}.</li>
     * </ul>
     */
    public record OrderReservation(OrderResponse response, Order order, long amountInCents,
                                   String currency, String email, String firstName) {
        static OrderReservation terminal(OrderResponse response) {
            return new OrderReservation(response, null, 0L, null, null, null);
        }

        static OrderReservation pending(Order order, long amountInCents, String currency,
                                        String email, String firstName) {
            return new OrderReservation(null, order, amountInCents, currency, email, firstName);
        }
    }

    /**
     * Transaction T2 of {@link #createOrder}: persists the PaymentIntent id/client-secret on the
     * already-RESERVED order. Runs after the Stripe call has returned, so no external work happens
     * inside this transaction; the receipt email is sent best-effort by the caller after commit.
     */
    @Transactional
    public OrderResponse attachPaymentIntent(Order order, PaymentIntentResult paymentIntent) {
        order.setPaymentIntentId(paymentIntent.id());
        order.setPaymentClientSecret(paymentIntent.clientSecret());
        // Persist the intent only — the receipt email is sent best-effort by the caller after this
        // transaction commits, so an email failure can never roll back the attachment.
        return toResponse(orderRepository.save(order));
    }

    /**
     * Compensation path when the payment step fails after {@link #reserveOrder} has committed:
     * marks the order FAILED, releases the Redis reservation, restores the already-committed stock
     * (under product/variant locks), and undoes the committed coupon and loyalty usage. If an
     * orphaned PaymentIntent id is supplied (attach-failure path, where the id was never persisted
     * on the order), it is cancelled and — on cancel failure — recorded as a retryable
     * PAYMENT_CANCEL compensation. Mirrors the non-stock cleanup of {@link #compensateOrder} but
     * sets a FAILED terminal status with a reason.
     */
    @Transactional
    public void failReservedOrder(Order order, String reason, String orphanPaymentIntentId) {
        UUID orderId = order.getId();
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason(reason);
        // Mark compensated here: we restore the stock inline below, so the stale-order
        // scheduler (which scans FAILED + compensatedFalse) must not run compensateOrder
        // again and restore the same items a second time.
        order.setCompensated(true);
        orderRepository.save(order);
        releaseReservation(orderId);

        java.util.TreeSet<UUID> productIdSet = new java.util.TreeSet<>();
        java.util.TreeSet<UUID> variantIdSet = new java.util.TreeSet<>();
        for (OrderItem item : order.getItems()) {
            if (item.getBundle() != null) continue; // bundle constituent locks skipped — rare path
            if (item.getProduct() != null) productIdSet.add(item.getProduct().getId());
            if (item.getVariant() != null) variantIdSet.add(item.getVariant().getId());
        }
        String failLockToken = UUID.randomUUID().toString();
        List<String> failLocks = new ArrayList<>();
        try {
            acquireLocks(new ArrayList<>(productIdSet), failLockToken, failLocks);
            acquireVariantLocks(new ArrayList<>(variantIdSet), failLockToken, failLocks);
        } catch (ConflictException e) {
            // Atomic restoreStock SQL is safe without the lock; it only sharpens concurrent reads.
            log.warn("failReservedOrder: could not acquire all locks for order {} — proceeding", orderId);
        }
        try {
            for (OrderItem item : order.getItems()) {
                try {
                    restoreItemStock(item);
                    recordCancelAdjustment(item, orderId);
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            buildRestoreDetail(item) + " restored after payment-intent failure", CompensationStatus.COMPLETED);
                } catch (Exception e) {
                    log.error("failReservedOrder: failed to restore stock for item on order {}: {}", orderId, e.getMessage());
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            buildRestoreDetail(item) + " failed to restore", CompensationStatus.FAILED, e.getMessage());
                }
            }
        } finally {
            releaseLocks(failLocks, failLockToken);
        }

        // Undo non-stock side effects committed by reserveOrder so a failed checkout does not
        // consume the customer's coupon/promotion allowances or loyalty points (mirrors compensateOrder).
        releaseCouponUsage(order);
        releasePromotionUsage(order);
        try {
            loyaltyService.restoreRedeemedPoints(orderId);
        } catch (Exception e) {
            log.error("failReservedOrder: failed to restore loyalty points for order {}: {}", orderId, e.getMessage());
        }

        // Cancel an orphaned PaymentIntent that was created at Stripe but never persisted on the
        // order (attach-failure path). Record a retryable PAYMENT_CANCEL row if the cancel fails —
        // the order is marked compensated, so the scheduler would not otherwise revisit it.
        if (orphanPaymentIntentId != null) {
            try {
                paymentService.cancelPaymentIntent(orphanPaymentIntentId);
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancelled payment intent: " + orphanPaymentIntentId, CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("failReservedOrder: failed to cancel orphaned payment intent {} for order {}: {}",
                        orphanPaymentIntentId, orderId, e.getMessage());
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancel payment intent: " + orphanPaymentIntentId, CompensationStatus.FAILED, e.getMessage());
            }
        }
    }

    @Override
    public OrderResponse getOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getLatestOrder(UUID userId) {
        Order order = orderRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No orders found"));
        return toResponse(order);
    }

    @Override
    public OrderResponse reorderOrder(UUID orderId, UUID userId) {
        // Build the request in a read-only transaction (lazy item associations are touched),
        // then create the order OUTSIDE any transaction so createOrder's own two-phase
        // boundary (and its out-of-transaction Stripe call) applies unchanged.
        CreateOrderRequest reorderRequest = self().buildReorderRequest(orderId, userId);
        return createOrder(userId, reorderRequest);
    }

    @Transactional(readOnly = true)
    public CreateOrderRequest buildReorderRequest(UUID orderId, UUID userId) {
        Order original = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        List<CreateOrderRequest.OrderItemRequest> itemRequests = original.getItems().stream()
                .filter(item -> item.getProduct() != null || item.getBundle() != null)
                .map(item -> {
                    CreateOrderRequest.OrderItemRequest req = new CreateOrderRequest.OrderItemRequest();
                    if (item.getBundle() != null) {
                        req.setBundleId(item.getBundle().getId());
                    } else {
                        req.setProductId(item.getProduct().getId());
                        if (item.getVariant() != null) {
                            req.setVariantId(item.getVariant().getId());
                        }
                    }
                    req.setQuantity(item.getQuantity());
                    return req;
                })
                .collect(Collectors.toList());

        if (itemRequests.isEmpty()) {
            throw new BadRequestException("None of the items in this order are available for re-order");
        }

        CreateOrderRequest reorderRequest = new CreateOrderRequest();
        reorderRequest.setItems(itemRequests);
        reorderRequest.setCurrency(original.getCurrency());

        return reorderRequest;
    }

    @Override
    public PagedResponse<OrderResponse> getOrders(UUID userId, OrderStatus status, int page, int size, String sort, String direction) {
        if (size > 50) size = 50;

        String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortField));

        if (status != null) {
            return new PagedResponse<>(
                    orderRepository.findAllByUserIdAndStatus(userId, status, pageable).map(this::toResponse)
            );
        }
        return new PagedResponse<>(
                orderRepository.findAllByUserId(userId, pageable).map(this::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderHistory(UUID orderId, UUID userId) {
        orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return orderStatusHistoryRepository.findAllByOrderIdOrderByOccurredAtAsc(orderId)
                .stream()
                .map(h -> new OrderStatusHistoryResponse(
                        h.getId(),
                        h.getEventType().name(),
                        h.getStatus() != null ? h.getStatus().name() : null,
                        h.getOccurredAt(),
                        h.getActorId(),
                        h.getNote()))
                .toList();
    }

    /**
     * Customer-initiated cancellation. The {@code findByIdAndUserId} lookup below
     * already enforces ownership (a user cannot cancel another user's order) and
     * {@link #cancelOrderInternal} enforces the status guard (RESERVED/PAID/PACKED
     * only — anything past PACKED is fulfilment-territory and must be handled by
     * the merchant via the returns flow, not unilaterally cancelled by the customer).
     *
     * <p>Internal callers (risk-reject, payment-failure escalation, scheduled stale
     * cleanup) bypass this entry point and use {@link #cancelOrderInternal} directly
     * with the appropriate {@link backend.models.enums.CancellationReason}.
     */
    @Override
    @Transactional
    @RetryOnConcurrency
    public OrderResponse cancelOrder(UUID orderId, UUID userId) {
        return cancelOrderInternal(orderId, userId, backend.models.enums.CancellationReason.CUSTOMER_REQUEST);
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public CompanyOrderResponse cancelOrderByCompany(UUID companyId, UUID orderId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);
        Order order = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        // A merchant may only cancel an order it owns in full. Cancellation refunds the
        // entire payment and restocks every line, so allowing it on a mixed-vendor
        // marketplace order would let one company void another vendor's items. Mirrors
        // the exclusivity guard already applied to returns and risk-review.
        requireExclusiveCompanyOrder(order, orderId, companyId);
        doCancelOrder(order, backend.models.enums.CancellationReason.MERCHANT_CANCELLED, ownerId);
        return toCompanyOrderResponse(orderRepository.save(order), companyId);
    }

    /**
     * Same flow as the public {@link #cancelOrder} but lets internal callers
     * (risk reject, payment failure escalation, etc.) tag the cancellation
     * with the correct {@link backend.models.enums.CancellationReason}.
     */
    OrderResponse cancelOrderInternal(UUID orderId, UUID userId, backend.models.enums.CancellationReason reason) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        doCancelOrder(order, reason, userId);
        return toResponse(orderRepository.save(order));
    }

    /**
     * Applies all cancellation side-effects to {@code order} in place: status guard,
     * payment void/refund, stock restoration, coupon release, and Kafka event registration.
     * Does NOT call {@link backend.repositories.orders.OrderRepository#save} — callers are
     * responsible for persisting and mapping to their response type.
     */
    private void doCancelOrder(Order order, backend.models.enums.CancellationReason reason, UUID actorId) {
        // Explicit positive list — we never want to silently start accepting cancellation
        // for a newly added intermediate status (PARTIALLY_FULFILLED, UNDER_REVIEW, etc).
        // Anything not in this set must throw, even if it gets added later.
        OrderStatus preStatus = order.getStatus();
        switch (preStatus) {
            case RESERVED, PAID, PACKED, UNDER_REVIEW -> { /* allowed */ }
            default -> throw new ConflictException(
                    "Orders can only be cancelled before they are shipped (status: " + preStatus + ")");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancellationReason(reason);

        if (order.getPaymentIntentId() != null) {
            try {
                if (preStatus == OrderStatus.RESERVED) {
                    // Intent not yet captured — void it
                    paymentService.cancelPaymentIntent(order.getPaymentIntentId());
                    recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                            "Cancelled payment intent: " + order.getPaymentIntentId(), CompensationStatus.COMPLETED);
                } else {
                    // Payment already captured (PAID or PACKED) — issue a full refund
                    long amountInCents = order.getTotalAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue();
                    paymentService.refundPayment(order.getPaymentIntentId(), amountInCents);
                    recordCompensation(order, CompensationType.PAYMENT_REFUND,
                            "Refunded captured payment for order cancellation: " + order.getPaymentIntentId(), CompensationStatus.COMPLETED);
                }
            } catch (Exception e) {
                CompensationType compType = (preStatus == OrderStatus.RESERVED)
                        ? CompensationType.PAYMENT_CANCEL : CompensationType.PAYMENT_REFUND;
                log.error("Failed to {} payment intent {} for order {}: {}",
                        preStatus == OrderStatus.RESERVED ? "cancel" : "refund",
                        order.getPaymentIntentId(), order.getId(), e.getMessage());
                recordCompensation(order, compType,
                        "Failed to process payment for cancellation: " + order.getPaymentIntentId(),
                        CompensationStatus.FAILED, e.getMessage());
            }
        }

        for (OrderItem item : order.getItems()) {
            try {
                restoreItemStock(item);
                recordCancelAdjustment(item, order.getId());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        buildRestoreDetail(item) + " restored for order cancellation", CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Failed to restore stock for item on order {}: {}", order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        buildRestoreDetail(item) + " failed to restore", CompensationStatus.FAILED, e.getMessage());
            }
            item.setFulfillmentStatus(FulfillmentStatus.CANCELLED);
        }

        releaseCouponUsage(order);
        releasePromotionUsage(order);
        order.setCompensated(true);

        // Publish after the enclosing @Transactional commits — publisher registers an afterCommit hook
        fulfillmentEventPublisher.publish(new OrderFulfillmentEvent.Cancelled(
                order.getId(), order.getUser().getId(), reason, order.getCancelledAt()));

        recordHistory(order, OrderHistoryEventType.STATUS_CHANGED, actorId, reason.name());
        publishSseEvent(order, reason.name(), "status_update");
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handlePaymentSuccess(String paymentIntentId) {
        orderRepository.findByPaymentIntentId(paymentIntentId).ifPresent(initialOrder -> {
            // Atomic transition: only the first concurrent webhook delivery succeeds.
            // Returns 0 if the order is already PAID (or UNDER_REVIEW/FAILED), preventing
            // double-commission recording when the same event is delivered twice.
            int transitioned = orderRepository.transitionStatus(initialOrder.getId(), OrderStatus.RESERVED, OrderStatus.PAID);
            if (transitioned == 0) {
                // R2-H2: this is no longer a routine DEBUG. The atomic transition can fail
                // for two distinct reasons, and operators need to be able to tell them
                // apart from logs alone:
                //   1) idempotent re-delivery (status already PAID) — benign
                //   2) the order has been flipped to UNDER_REVIEW / FAILED / CANCELLED
                //      since the payment intent was created — the customer has paid for
                //      an order we won't fulfil. Manual refund required.
                Order current = orderRepository.findById(initialOrder.getId()).orElse(initialOrder);
                if (current.getStatus() == OrderStatus.PAID) {
                    log.debug("payment_intent.succeeded already processed for order {}", current.getId());
                } else {
                    log.warn("[ORPHAN-PAYMENT] payment_intent.succeeded for order {} but status is {} (not RESERVED). "
                            + "Stripe holds funds for paymentIntentId={}; merchant must reconcile (refund + re-issue if needed).",
                            current.getId(), current.getStatus(), current.getPaymentIntentId());
                }
                return;
            }
            // The atomic SQL bypassed JPA, so {@code initialOrder} is now stale (its
            // status is RESERVED in memory but PAID on disk, version unchanged). Reload
            // before further mutation so subsequent save() merges from current state
            // rather than the snapshot we loaded above.
            Order order = orderRepository.findById(initialOrder.getId()).orElse(initialOrder);
            order.setPaidAt(Instant.now());
            orderRepository.save(order);
            recordHistory(order, OrderHistoryEventType.STATUS_CHANGED, null, "Payment confirmed");
            publishSseEvent(order, "Payment confirmed", "status_update");
            eventPublisher.publishEvent(new GiftCardIssueRequestedEvent(order.getId()));
            releaseReservation(order.getId());
            if (order.isMarketplaceOrder()) {
                recordSubOrderCommission(order);
            }
            try {
                UUID companyId = resolveOrderCompanyId(order.getItems());
                loyaltyService.recordOrderEarn(order, companyId);
            } catch (Exception e) {
                log.error("[LOYALTY] Failed to record earn for order {}: {}", order.getId(), e.getMessage());
            }
        });
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handlePaymentFailure(String paymentIntentId) {
        orderRepository.findByPaymentIntentId(paymentIntentId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PAID) {
                log.warn("payment_intent.payment_failed ignored — order {} is already PAID", order.getId());
                return;
            }
            if (orderRepository.markCompensated(order.getId()) == 0) return;
            order.setCompensated(true); // keep entity in sync with DB
            releaseReservation(order.getId());

            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason("Payment failed via webhook");
            order.setCancelledAt(Instant.now());
            order.setCancellationReason(backend.models.enums.CancellationReason.PAYMENT_FAILED);

            // Feed the failed-payment velocity signal. The webhook itself has no IP;
            // recover it from the risk assessment recorded at checkout time.
            recordFailedPaymentAttempt(order, paymentIntentId, "Payment failed via webhook");

            for (OrderItem item : order.getItems()) {
                try {
                    restoreItemStock(item);
                    recordCancelAdjustment(item, order.getId());
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            buildRestoreDetail(item) + " restored for payment failure", CompensationStatus.COMPLETED);
                } catch (Exception e) {
                    log.error("Failed to restore stock for item on failed order {}: {}", order.getId(), e.getMessage());
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            buildRestoreDetail(item) + " failed to restore", CompensationStatus.FAILED, e.getMessage());
                }
            }

            releaseCouponUsage(order);
            releasePromotionUsage(order);
            orderRepository.save(order);
            recordHistory(order, OrderHistoryEventType.STATUS_CHANGED, null, "Payment failed");
            publishSseEvent(order, "Payment failed", "status_update");
        });
    }

    /**
     * Compensates a single failed/stale order. Called by the scheduler for orders
     * that were not successfully compensated inline. Each step is individually
     * wrapped so a failure in one does not prevent the others from executing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateOrder(Order order) {
        if (orderRepository.markCompensated(order.getId()) == 0) return;
        order.setCompensated(true); // keep entity in sync with DB
        releaseReservation(order.getId());

        log.info("Compensating order {} (status={})", order.getId(), order.getStatus());

        // Acquire product/variant locks (sorted to prevent deadlock) before restoring stock,
        // so concurrent createOrder calls see a consistent stock value after restoration.
        java.util.TreeSet<UUID> productIdSet = new java.util.TreeSet<>();
        java.util.TreeSet<UUID> variantIdSet = new java.util.TreeSet<>();
        for (OrderItem item : order.getItems()) {
            if (item.getBundle() != null) continue; // bundle constituent locks skipped — rare path
            if (item.getProduct() != null) productIdSet.add(item.getProduct().getId());
            if (item.getVariant() != null) variantIdSet.add(item.getVariant().getId());
        }

        String compensateLockToken = UUID.randomUUID().toString();
        List<String> compensateLocks = new ArrayList<>();
        try {
            acquireLocks(new ArrayList<>(productIdSet), compensateLockToken, compensateLocks);
            acquireVariantLocks(new ArrayList<>(variantIdSet), compensateLockToken, compensateLocks);
        } catch (ConflictException e) {
            // Best-effort: if we can't acquire locks quickly, proceed anyway.
            // The atomic restoreStock SQL is still safe; the lock only improves
            // consistency of concurrent entity reads in createOrder.
            log.warn("compensateOrder: could not acquire all locks for order {} — proceeding without full lock coverage", order.getId());
        }

        int itemsRestored = 0;
        int itemsFailed = 0;
        try {
            for (OrderItem item : order.getItems()) {
                try {
                    restoreItemStock(item);
                    recordCancelAdjustment(item, order.getId());
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            buildRestoreDetail(item) + " restored by scheduler", CompensationStatus.COMPLETED);
                    itemsRestored++;
                } catch (Exception e) {
                    itemsFailed++;
                    log.error("Scheduled compensation: failed to restore stock for item on order {}: {}", order.getId(), e.getMessage());
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            buildRestoreDetail(item) + " failed in scheduler", CompensationStatus.FAILED, e.getMessage());
                }
            }
        } finally {
            releaseLocks(compensateLocks, compensateLockToken);
        }
        if (itemsFailed > 0) {
            // FAILED compensation rows are retried by retryCompensation(). Log a prominent
            // warning so operators can see partial failures via monitoring/alerting.
            log.warn("compensateOrder: partial failure for order {} — {}/{} items restored; {} FAILED rows queued for retry",
                    order.getId(), itemsRestored, itemsRestored + itemsFailed, itemsFailed);
        }

        if (order.getPaymentIntentId() != null && order.getStatus() != OrderStatus.CANCELLED) {
            try {
                paymentService.cancelPaymentIntent(order.getPaymentIntentId());
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancelled payment intent: " + order.getPaymentIntentId(), CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Scheduled compensation: failed to cancel payment {} for order {}: {}",
                        order.getPaymentIntentId(), order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancel payment intent: " + order.getPaymentIntentId(), CompensationStatus.FAILED, e.getMessage());
            }
        }

        if (order.getStatus() == OrderStatus.RESERVED) {
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason("Compensated by scheduler — stale reserved order");
            order.setCancelledAt(Instant.now());
            order.setCancellationReason(backend.models.enums.CancellationReason.STALE_TIMEOUT);
        }
        releaseCouponUsage(order);
        releasePromotionUsage(order);
        orderRepository.save(order);

        if (order.getCancellationReason() == backend.models.enums.CancellationReason.STALE_TIMEOUT
                && eventPublisher != null) {
            User user = order.getUser();
            if (user != null) {
                List<OrderReservationExpiredEvent.AbandonedItemData> itemData = order.getItems().stream()
                        .map(i -> new OrderReservationExpiredEvent.AbandonedItemData(
                                i.getProduct() != null ? i.getProduct().getId() : null,
                                i.getProductName(),
                                i.getProduct() != null ? i.getProduct().getThumbnailUrl() : null,
                                i.getUnitPrice()))
                        .toList();
                eventPublisher.publishEvent(new OrderReservationExpiredEvent(
                        order.getId(), user.getId(), user.getEmail(), user.getFirstName(), itemData));
            }
        }

        try {
            loyaltyService.restoreRedeemedPoints(order.getId());
        } catch (Exception e) {
            log.error("Failed to restore loyalty points for compensated order {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Retries a single previously failed compensation record. Called by the scheduler
     * to ensure eventual consistency on items that failed their first compensation attempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryCompensation(OrderCompensation compensation) {
        if (compensationRepository.claimForRetry(compensation.getId()) == 0) {
            log.debug("Compensation {} already claimed by another worker — skipping", compensation.getId());
            return;
        }
        compensation.setAttempts(compensation.getAttempts() + 1);

        try {
            switch (compensation.getType()) {
                case STOCK_RESTORE -> {
                    // Fail closed: if a concrete restore target cannot be parsed from the detail
                    // string (malformed, or a multi-component [BUNDLE:]/[KIT:] line that the
                    // string format cannot reconstruct), throw so the row stays FAILED and visible
                    // rather than being silently marked COMPLETED without restoring any inventory.
                    String detail = compensation.getDetail();
                    int quantity = extractQuantityFromDetail(detail);
                    if (detail != null && detail.startsWith("[LOC:")) {
                        UUID locationStockId = extractLocationStockIdFromDetail(detail);
                        if (locationStockId == null || quantity <= 0) {
                            throw new IllegalStateException("Unparseable location stock-restore detail: " + detail);
                        }
                        locationStockRepository.restoreStock(locationStockId, quantity);
                    } else if (detail != null && detail.startsWith("[VARIANT]")) {
                        UUID variantId = extractVariantIdFromDetail(detail);
                        if (variantId == null || quantity <= 0) {
                            throw new IllegalStateException("Unparseable variant stock-restore detail: " + detail);
                        }
                        variantRepository.restoreStock(variantId, quantity);
                    } else if (detail != null && (detail.startsWith("[BUNDLE:") || detail.startsWith("[KIT:"))) {
                        throw new IllegalStateException(
                                "Cannot reconstruct multi-component stock-restore from detail: " + detail);
                    } else {
                        UUID productId = extractProductIdFromDetail(detail);
                        if (productId == null || quantity <= 0) {
                            throw new IllegalStateException("Unparseable product stock-restore detail: " + detail);
                        }
                        productRepository.restoreStock(productId, quantity);
                    }
                }
                case PAYMENT_CANCEL -> {
                    String intentId = extractIntentIdFromDetail(compensation.getDetail());
                    if (intentId == null) {
                        throw new IllegalStateException("No payment intent id in detail: " + compensation.getDetail());
                    }
                    paymentService.cancelPaymentIntent(intentId);
                }
                case PAYMENT_REFUND -> {
                    String detail = compensation.getDetail();
                    String intentId = extractIntentIdFromDetail(detail);
                    Long centsToRefund = extractRefundCentsFromDetail(detail);
                    if (intentId == null) {
                        throw new IllegalStateException("No payment intent id in detail: " + detail);
                    }
                    paymentService.refundPayment(intentId, centsToRefund);
                }
            }
            compensation.setStatus(CompensationStatus.COMPLETED);
            compensation.setCompletedAt(Instant.now());
            compensation.setErrorMessage(null);
        } catch (Exception e) {
            log.error("Compensation retry failed for id={} type={}: {}",
                    compensation.getId(), compensation.getType(), e.getMessage());
            // Reset from CLAIMED back to FAILED so the row is re-scanned on the next pass
            // (until MAX attempts) instead of being stranded in the transient CLAIMED state.
            compensation.setStatus(CompensationStatus.FAILED);
            compensation.setErrorMessage(e.getMessage());
        }

        compensationRepository.save(compensation);
    }

    private void scheduleStockCompensation(Order order, List<Object[]> decrementedProducts,
                                            List<Object[]> decrementedVariants, List<Object[]> decrementedLocationStocks) {
        for (Object[] entry : decrementedProducts) {
            UUID id = (UUID) entry[0]; int qty = (int) entry[1];
            try {
                productRepository.restoreStock(id, qty);
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Restored " + qty + " units for product " + id, CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Inline stock compensation failed for product {} on order {}: {}",
                        id, order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Restore " + qty + " units for product " + id, CompensationStatus.FAILED, e.getMessage());
            }
        }
        for (Object[] entry : decrementedVariants) {
            UUID id = (UUID) entry[0]; int qty = (int) entry[1];
            try {
                variantRepository.restoreStock(id, qty);
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "[VARIANT] Restored " + qty + " units for variant " + id, CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Inline stock compensation failed for variant {} on order {}: {}",
                        id, order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "[VARIANT] Restore " + qty + " units for variant " + id, CompensationStatus.FAILED, e.getMessage());
            }
        }
        for (Object[] entry : decrementedLocationStocks) {
            UUID id = (UUID) entry[0]; int qty = (int) entry[1];
            try {
                locationStockRepository.restoreStock(id, qty);
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "[LOC:" + id + "] Restored " + qty + " units", CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Inline location stock compensation failed for locationStockId {} on order {}: {}",
                        id, order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "[LOC:" + id + "] Restore " + qty + " units", CompensationStatus.FAILED, e.getMessage());
            }
        }
    }

    /**
     * Writes a Redis reservation manifest for the given order.
     * Key: "reserve:order:{orderId}", TTL = reservationTtlSeconds.
     * Non-critical: failures are logged and swallowed so checkout is not blocked.
     */
    private void writeReservation(UUID orderId,
                                   List<Object[]> products,
                                   List<Object[]> variants,
                                   List<Object[]> locationStocks) {
        try {
            StringBuilder sb = new StringBuilder("{\"p\":{");
            appendReservationEntries(sb, products);
            sb.append("},\"v\":{");
            appendReservationEntries(sb, variants);
            sb.append("},\"ls\":{");
            appendReservationEntries(sb, locationStocks);
            sb.append("}}");
            cacheService.set("reserve:order:" + orderId, sb.toString(), reservationTtlSeconds);
        } catch (Exception e) {
            log.warn("[RESERVE] Failed to write reservation for order {}: {}", orderId, e.getMessage());
        }
    }

    private static void appendReservationEntries(StringBuilder sb, List<Object[]> entries) {
        boolean first = true;
        for (Object[] e : entries) {
            if (!first) sb.append(',');
            sb.append('"').append(e[0]).append("\":").append(e[1]);
            first = false;
        }
    }

    /**
     * Deletes the Redis reservation manifest for this order.
     * Idempotent: safe to call even if the key has already expired or was never written.
     */
    private void releaseCouponUsage(Order order) {
        Coupon coupon = order.getCoupon();
        if (coupon == null) return;
        // Reverse the global usedCount bumped in reserveOrder so a cancelled/failed checkout does
        // not permanently consume the coupon's maxUses inventory.
        try {
            couponRepository.decrementUsedCount(coupon.getId());
        } catch (Exception e) {
            log.error("Failed to release global coupon usage for order {}: {}", order.getId(), e.getMessage());
        }
        if (coupon.getMaxUsesPerUser() != null && order.getUser() != null) {
            try {
                couponPerUserCountRepository.decrementUserCount(coupon.getId(), order.getUser().getId());
            } catch (Exception e) {
                log.error("Failed to release per-user coupon usage for order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    /**
     * Reverses the global and per-user promotion usage counters incremented in {@code reserveOrder}
     * for a cancelled/failed order, using the order's PromotionRedemption rows as the source of
     * truth. Each step is guarded so a single failure does not block the rest of the cleanup.
     */
    private void releasePromotionUsage(Order order) {
        for (PromotionRedemption pr : promotionRedemptionRepository.findAllByOrderId(order.getId())) {
            PromotionRule rule = pr.getRule();
            if (rule == null) continue;
            UUID ruleId = rule.getId();
            try {
                promotionRuleRepository.decrementUsedCount(ruleId);
            } catch (Exception e) {
                log.error("Failed to release global promotion usage for rule {} on order {}: {}",
                        ruleId, order.getId(), e.getMessage());
            }
            if (rule.getMaxUsesPerUser() != null && order.getUser() != null) {
                try {
                    promotionPerUserCountRepository.decrementUserCount(ruleId, order.getUser().getId());
                } catch (Exception e) {
                    log.error("Failed to release per-user promotion usage for rule {} on order {}: {}",
                            ruleId, order.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Decrements the per-user coupon usage counter that was incremented during
     * order creation. Called in the {@code createOrder} catch blocks so a failed
     * order does not permanently consume one of the user's allowed coupon uses.
     *
     * <p>Only acts when the coupon has a per-user cap ({@code maxUsesPerUser != null})
     * — unlimited coupons don't have a counter to undo.
     */
    private void releaseCouponCountIfNeeded(Coupon coupon, UUID userId) {
        if (coupon != null && coupon.getMaxUsesPerUser() != null) {
            try {
                couponPerUserCountRepository.decrementUserCount(coupon.getId(), userId);
            } catch (Exception e) {
                log.error("[COUPON] Failed to release per-user count for user {} coupon {}: {}",
                        userId, coupon.getId(), e.getMessage());
            }
        }
    }

    private void releaseReservation(UUID orderId) {
        try {
            cacheService.delete("reserve:order:" + orderId);
        } catch (Exception e) {
            log.warn("[RESERVE] Failed to release reservation for order {}: {}", orderId, e.getMessage());
        }
    }

    /**
     * Emergency stock restore used by the order-creation catch block. We CANNOT propagate
     * the restore failure (the caller will rethrow the original exception, and a thrown
     * exception here would shadow it), but we MUST NOT lose the audit trail either — a
     * silently-dropped restore permanently leaks inventory. Strategy:
     *   - keep going on individual failures so as much stock is restored as possible
     *   - if a savedOrderId is available, persist an OrderCompensation row tagged
     *     {@link CompensationStatus#FAILED} so the existing retry/compensation tooling
     *     (see {@link #retryCompensation}) can pick it up later
     *   - otherwise (the order was never persisted), emit a high-cardinality ERROR log
     *     that an operator can grep for during reconciliation
     */
    private void safeRestoreAll(List<Object[]> decrementedProducts, List<Object[]> decrementedVariants,
                                List<Object[]> decrementedLocationStocks) {
        Order orphan = null;
        // The order may have been saved before the exception (savedOrderId is set at
        // line ~695); look it up so we can attach compensation rows. If lookup itself
        // fails, fall back to logs.
        // We accept a `null` order — the compensation helper guards against that case.
        safeRestoreAll(orphan, decrementedProducts, decrementedVariants, decrementedLocationStocks);
    }

    private void safeRestoreAll(Order order,
                                List<Object[]> decrementedProducts,
                                List<Object[]> decrementedVariants,
                                List<Object[]> decrementedLocationStocks) {
        for (Object[] entry : decrementedProducts) {
            UUID id = (UUID) entry[0]; int qty = (int) entry[1];
            restoreOneSafe(order, "product " + id, qty,
                    () -> productRepository.restoreStock(id, qty));
        }
        for (Object[] entry : decrementedVariants) {
            UUID id = (UUID) entry[0]; int qty = (int) entry[1];
            restoreOneSafe(order, "[VARIANT] variant " + id, qty,
                    () -> variantRepository.restoreStock(id, qty));
        }
        for (Object[] entry : decrementedLocationStocks) {
            UUID id = (UUID) entry[0]; int qty = (int) entry[1];
            restoreOneSafe(order, "[LOC] locationStock " + id, qty,
                    () -> locationStockRepository.restoreStock(id, qty));
        }
    }

    private void restoreOneSafe(Order order, String subjectDescription, int qty, Runnable restore) {
        try {
            restore.run();
            if (order != null) {
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Restored " + qty + " units for " + subjectDescription,
                        CompensationStatus.COMPLETED);
            }
        } catch (Exception e) {
            // Persist the failure rather than swallowing — if the order exists, the
            // compensation row makes it queryable and retry-able. If not, log loudly
            // with enough context for a human to reconcile.
            if (order != null) {
                try {
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            "Restore " + qty + " units for " + subjectDescription,
                            CompensationStatus.FAILED, e.getMessage());
                } catch (Exception persistEx) {
                    log.error("[STOCK-LEAK] Order={} failed to restore {} units for {} AND failed to record compensation: restore={} record={}",
                            order.getId(), qty, subjectDescription, e.getMessage(), persistEx.getMessage());
                }
            } else {
                log.error("[STOCK-LEAK] Pre-save order failed to restore {} units for {}: {}",
                        qty, subjectDescription, e.getMessage());
            }
        }
    }

    private boolean hasAnyLocationStock(UUID productId, UUID variantId) {
        List<LocationStock> check = (variantId != null)
                ? locationStockRepository.findTopByVariantStockDesc(productId, variantId, PageRequest.of(0, 1))
                : locationStockRepository.findTopByProductStockDesc(productId, PageRequest.of(0, 1));
        return !check.isEmpty();
    }

    private void restoreItemStock(OrderItem item) {
        if (item.getFulfillmentStatus() == FulfillmentStatus.BACKORDERED
                || item.getFulfillmentStatus() == FulfillmentStatus.PREORDERED) return;  // no stock was decremented — nothing to restore

        if (item.getBundle() != null) {
            // Restore each constituent product's stock (no location stock for bundle items)
            for (BundleItem bi : item.getBundle().getItems()) {
                int totalQty = item.getQuantity() * bi.getQuantity();
                if (bi.getVariant() != null) {
                    variantRepository.restoreStock(bi.getVariant().getId(), totalQty);
                } else {
                    productRepository.restoreStock(bi.getProduct().getId(), totalQty);
                }
            }
            return;
        }

        if (item.getKit() != null) {
            // Restore each selected component's stock
            for (OrderKitSelection oks : item.getKitSelections()) {
                int totalQty = item.getQuantity() * oks.getQuantity();
                if (oks.getVariant() != null) {
                    variantRepository.restoreStock(oks.getVariant().getId(), totalQty);
                } else if (oks.getProduct() != null) {
                    productRepository.restoreStock(oks.getProduct().getId(), totalQty);
                }
            }
            return;
        }

        if (item.getVariant() != null) {
            variantRepository.restoreStock(item.getVariant().getId(), item.getQuantity());
        } else {
            productRepository.restoreStock(item.getProduct().getId(), item.getQuantity());
        }
        if (item.getFulfillmentLocation() != null) {
            UUID variantRef = item.getVariant() != null ? item.getVariant().getId() : null;
            locationStockRepository.findByLocationIdAndProductIdAndVariantRef(
                            item.getFulfillmentLocation().getId(), item.getProduct().getId(), variantRef)
                    .ifPresentOrElse(
                            ls -> locationStockRepository.restoreStock(ls.getId(), item.getQuantity()),
                            () -> log.warn("Location stock row missing during restore — leaked {} units. "
                                            + "location={} product={} variant={} item={}",
                                    item.getQuantity(), item.getFulfillmentLocation().getId(),
                                    item.getProduct().getId(), variantRef, item.getId()));
        }
    }

    /** Per-line tolerance (in dollars) used to build the scaled aggregate tolerance below. */
    private static final BigDecimal TOTAL_RECONCILIATION_PER_LINE_TOLERANCE = new BigDecimal("0.01");
    /** Floor — never accept less than a 2¢ drift even on tiny orders. */
    private static final BigDecimal TOTAL_RECONCILIATION_MIN_TOLERANCE = new BigDecimal("0.02");

    /**
     * Sanity-checks {@code finalTotal} against the per-line arithmetic. Any drift larger
     * than {@code max(MIN_TOLERANCE, lines * PER_LINE_TOLERANCE)} is logged at WARN with
     * the breakdown so a pricing-engine regression doesn't quietly bill customers an
     * incorrect amount.
     *
     * <p>R2-L8: scaling tolerance with line count keeps the alarm useful on both small
     * and large orders. A flat $0.02 tolerance fires too often on 100-line orders
     * (legitimate cumulative rounding) and would mask real bugs once operators
     * start ignoring it.
     *
     * <p>Intentionally non-fatal — failing live orders for sub-dollar discrepancies
     * would be a worse customer experience than the discrepancy itself; the log lights
     * up monitoring instead.
     */
    private void reconcileOrderTotal(List<OrderItem> items,
                                     BigDecimal finalTotal,
                                     BigDecimal couponDiscountAmount,
                                     long loyaltyDiscountCents) {
        BigDecimal lineSum = BigDecimal.ZERO;
        for (OrderItem item : items) {
            BigDecimal lineSubtotal = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .subtract(item.getDiscountAmount() == null ? BigDecimal.ZERO : item.getDiscountAmount());
            lineSum = lineSum.add(lineSubtotal);
        }
        BigDecimal loyaltyDiscount = BigDecimal.valueOf(loyaltyDiscountCents).movePointLeft(2);
        BigDecimal expected = lineSum
                .subtract(couponDiscountAmount == null ? BigDecimal.ZERO : couponDiscountAmount)
                .subtract(loyaltyDiscount)
                .max(BigDecimal.ZERO);
        BigDecimal drift = finalTotal.subtract(expected).abs();
        BigDecimal scaledTolerance = TOTAL_RECONCILIATION_PER_LINE_TOLERANCE
                .multiply(BigDecimal.valueOf(Math.max(1, items.size())))
                .max(TOTAL_RECONCILIATION_MIN_TOLERANCE);
        if (drift.compareTo(scaledTolerance) > 0) {
            log.warn("[TOTALS] Mismatch beyond {} tolerance — engine finalTotal={} expected={} drift={} (lineSum={}, coupon={}, loyalty={}, lines={})",
                    scaledTolerance, finalTotal, expected, drift, lineSum, couponDiscountAmount, loyaltyDiscount, items.size());
        }
    }

    private void publishSseEvent(Order order, String note, String eventType) {
        if (stringRedisTemplate == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublishSseEvent(order, note, eventType);
                }
            });
        } else {
            doPublishSseEvent(order, note, eventType);
        }
    }

    private void doPublishSseEvent(Order order, String note, String eventType) {
        try {
            SseStatusUpdateEvent event = new SseStatusUpdateEvent(
                    UUID.randomUUID(),
                    order.getId(),
                    order.getStatus().name(),
                    order.getTrackingNumber(),
                    order.getCarrier(),
                    null,
                    null,
                    note,
                    Instant.now(),
                    eventType);
            stringRedisTemplate.convertAndSend(
                    "order:stream:" + order.getId(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("[SSE] Failed to publish SSE event for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private void recordHistory(Order order, OrderHistoryEventType eventType, UUID actorId, String note) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(order.getId());
        h.setEventType(eventType);
        h.setStatus(eventType == OrderHistoryEventType.STATUS_CHANGED ? order.getStatus() : null);
        h.setOccurredAt(Instant.now());
        h.setActorId(actorId);
        h.setNote(note);
        orderStatusHistoryRepository.save(h);
    }

    private void recordCompensation(Order order, CompensationType type, String detail, CompensationStatus status) {
        recordCompensation(order, type, detail, status, null);
    }

    private void recordCompensation(Order order, CompensationType type, String detail, CompensationStatus status, String errorMessage) {
        OrderCompensation comp = new OrderCompensation();
        comp.setOrder(order);
        comp.setType(type);
        comp.setDetail(detail);
        comp.setStatus(status);
        comp.setErrorMessage(errorMessage);
        comp.setAttempts(1);
        if (status == CompensationStatus.COMPLETED) {
            comp.setCompletedAt(Instant.now());
        }
        compensationRepository.save(comp);
    }

    /**
     * Records an ORDER_CANCELLED adjustment for a single order item after stock has been restored.
     * previousStock/newStock are set to 0 — the restore is atomic and reading before it would be a
     * TOCTOU race. The delta (+qty) and orderId are the authoritative audit values.
     */
    private void recordCancelAdjustment(OrderItem item, UUID orderId) {
        try {
            if (item.getBundle() != null) {
                // One adjustment per bundle constituent
                for (BundleItem bi : item.getBundle().getItems()) {
                    int totalQty = item.getQuantity() * bi.getQuantity();
                    InventoryAdjustment adj = new InventoryAdjustment();
                    adj.setProduct(bi.getProduct());
                    adj.setVariant(bi.getVariant());
                    adj.setDelta(totalQty);
                    adj.setPreviousStock(0);
                    adj.setNewStock(0);
                    adj.setReason(AdjustmentReason.ORDER_CANCELLED);
                    adj.setNote("Bundle order #" + orderId + " cancelled — bundle: " + item.getBundleName());
                    adj.setOrderId(orderId);
                    adjustmentRepository.save(adj);
                }
                return;
            }

            InventoryAdjustment adj = new InventoryAdjustment();
            adj.setProduct(item.getProduct());
            adj.setVariant(item.getVariant());
            adj.setDelta(item.getQuantity()); // positive = stock returned
            adj.setPreviousStock(0);
            adj.setNewStock(0);
            adj.setReason(AdjustmentReason.ORDER_CANCELLED);
            adj.setNote("Order #" + orderId + " cancelled/failed");
            adj.setOrderId(orderId);
            adjustmentRepository.save(adj);
        } catch (Exception e) {
            log.warn("Failed to record cancel adjustment for order {}: {}", orderId, e.getMessage());
        }
    }

    private static String buildRestoreDetail(OrderItem item) {
        if (item.getBundle() != null) {
            return "[BUNDLE:" + item.getBundle().getId() + "] " + item.getQuantity() + " unit(s) of bundle " + item.getBundleName();
        }
        // Kit lines have a null product; label them explicitly so a failed-restore row is not
        // misrecorded as "product unknown". NOTE: kit/bundle restores are multi-component and the
        // string-based retryCompensation() path cannot reconstruct them — restoreItemStock() must
        // succeed on the first pass. Component-level compensation rows are a known follow-up.
        if (item.getKit() != null) {
            return "[KIT:" + item.getKit().getId() + "] " + item.getQuantity() + " unit(s) of kit " + item.getKitName();
        }
        if (item.getVariant() != null) {
            return "[VARIANT] " + item.getQuantity() + " units for variant " + item.getVariant().getId();
        }
        return "Restored " + item.getQuantity() + " units for product " +
                (item.getProduct() != null ? item.getProduct().getId() : "unknown");
    }

    private void acquireVariantLocks(List<UUID> sortedVariantIds, String lockToken, List<String> acquiredLocks) {
        for (UUID variantId : sortedVariantIds) {
            String lockKey = VARIANT_LOCK_PREFIX + variantId;
            boolean acquired = false;

            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(lockKey, lockToken, lockTtlSeconds)) {
                    acquiredLocks.add(lockKey);
                    acquired = true;
                    break;
                }
                try {
                    Thread.sleep(LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Order processing interrupted, please try again");
                }
            }

            if (!acquired) {
                releaseLocks(acquiredLocks, lockToken);
                throw new ConflictException("Variant is currently being purchased by another user, please try again shortly");
            }
        }
    }

    private void acquireLocks(List<UUID> sortedProductIds, String lockToken, List<String> acquiredLocks) {
        for (UUID productId : sortedProductIds) {
            String lockKey = LOCK_PREFIX + productId;
            boolean acquired = false;

            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(lockKey, lockToken, lockTtlSeconds)) {
                    acquiredLocks.add(lockKey);
                    acquired = true;
                    break;
                }
                try {
                    Thread.sleep(LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Order processing interrupted, please try again");
                }
            }

            if (!acquired) {
                releaseLocks(acquiredLocks, lockToken);
                throw new ConflictException("Product is currently being purchased by another user, please try again shortly");
            }
        }
    }

    private void releaseLocks(List<String> lockKeys, String lockToken) {
        for (String lockKey : lockKeys) {
            try {
                cacheService.unlock(lockKey, lockToken);
            } catch (Exception e) {
                log.error("Failed to release lock {}: {}", lockKey, e.getMessage());
            }
        }
    }

    private static UUID extractProductIdFromDetail(String detail) {
        try {
            int idx = detail.lastIndexOf("product ");
            if (idx >= 0) return UUID.fromString(detail.substring(idx + 8).trim());
        } catch (Exception ignored) {}
        return null;
    }

    private static String buildVariantTitle(ProductVariant variant) {
        String title = java.util.stream.Stream.of(variant.getOption1(), variant.getOption2(), variant.getOption3())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" / "));
        return title.isBlank() ? null : title;
    }

    private static UUID extractVariantIdFromDetail(String detail) {
        try {
            int idx = detail.lastIndexOf("variant ");
            if (idx >= 0) return UUID.fromString(detail.substring(idx + 8).trim());
        } catch (Exception ignored) {}
        return null;
    }

    // Parses "[LOC:some-uuid] Restored 3 units" → UUID
    private static UUID extractLocationStockIdFromDetail(String detail) {
        try {
            int start = detail.indexOf("[LOC:") + 5;
            int end = detail.indexOf("]", start);
            if (start > 4 && end > start) return UUID.fromString(detail.substring(start, end).trim());
        } catch (Exception ignored) {}
        return null;
    }

    private static int extractQuantityFromDetail(String detail) {
        try {
            int startIdx = detail.indexOf("Restore ") >= 0 ? detail.indexOf("Restore ") + 8 : detail.indexOf("Restored ") + 9;
            int endIdx = detail.indexOf(" units");
            if (startIdx > 0 && endIdx > startIdx) return Integer.parseInt(detail.substring(startIdx, endIdx).trim());
        } catch (Exception ignored) {}
        return -1;
    }

    private static String extractIntentIdFromDetail(String detail) {
        if (detail == null) return null;
        // Structured partial-refund format: "REFUND_PARTIAL:{intentId}:CENTS:{amount}"
        if (detail.startsWith("REFUND_PARTIAL:")) {
            String[] parts = detail.split(":");
            // parts[0]=REFUND_PARTIAL, parts[1]=intentId (may contain underscores), parts[2]=CENTS, parts[3]=amount
            if (parts.length >= 4) return parts[1];
        }
        int idx = detail.lastIndexOf(": ");
        if (idx >= 0) return detail.substring(idx + 2).trim();
        idx = detail.lastIndexOf("intent: ");
        if (idx >= 0) return detail.substring(idx + 8).trim();
        return null;
    }

    /**
     * Extracts the refund amount in cents from a structured PAYMENT_REFUND compensation detail.
     * Returns null for legacy full-refund records (which should call refundPayment with null).
     * Format: "REFUND_PARTIAL:{intentId}:CENTS:{amountCents}"
     */
    private static Long extractRefundCentsFromDetail(String detail) {
        if (detail == null || !detail.startsWith("REFUND_PARTIAL:")) return null;
        try {
            int centsIdx = detail.lastIndexOf(":CENTS:");
            if (centsIdx >= 0) {
                return Long.parseLong(detail.substring(centsIdx + 7).trim());
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    @Override
    @Transactional
    public void fulfillPendingBackorders(UUID productId, UUID variantId, int availableQty, UUID fulfillmentLocationId) {
        String lockToken = UUID.randomUUID().toString();
        List<String> acquiredLocks = new ArrayList<>();

        try {
            String productLockKey = LOCK_PREFIX + productId;
            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(productLockKey, lockToken, lockTtlSeconds)) {
                    acquiredLocks.add(productLockKey);
                    break;
                }
                try { Thread.sleep(LOCK_RETRY_DELAY_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            if (acquiredLocks.isEmpty()) {
                log.warn("fulfillPendingBackorders: could not acquire product lock for product={} — skipping this run", productId);
                return;
            }

            if (variantId != null) {
                String variantLockKey = VARIANT_LOCK_PREFIX + variantId;
                boolean variantAcquired = false;
                for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                    if (cacheService.tryLock(variantLockKey, lockToken, lockTtlSeconds)) {
                        acquiredLocks.add(variantLockKey);
                        variantAcquired = true;
                        break;
                    }
                    try { Thread.sleep(LOCK_RETRY_DELAY_MS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                if (!variantAcquired) {
                    log.warn("fulfillPendingBackorders: could not acquire variant lock for variant={} — skipping this run", variantId);
                    return;
                }
            }

            List<Order> backorders = (variantId != null)
                    ? orderRepository.findPaidOrdersWithBackorderedVariant(variantId, FulfillmentStatus.BACKORDERED)
                    : orderRepository.findPaidOrdersWithBackorderedProduct(productId, FulfillmentStatus.BACKORDERED);

            int remaining = availableQty;

            for (Order order : backorders) {
                if (remaining <= 0) break;

                for (OrderItem item : order.getItems()) {
                    if (item.getFulfillmentStatus() != FulfillmentStatus.BACKORDERED) continue;
                    if (!productId.equals(item.getProduct().getId())) continue;
                    if (variantId != null && (item.getVariant() == null || !variantId.equals(item.getVariant().getId()))) continue;

                    int qty = item.getQuantity();
                    if (qty > remaining) return; // FIFO: stop rather than skip to a younger order

                    int updated = (variantId != null)
                            ? variantRepository.decrementStock(variantId, qty)
                            : productRepository.decrementStock(productId, qty);

                    if (updated == 0) {
                        log.warn("fulfillPendingBackorders: decrementStock returned 0 for product={} variant={} qty={} — stopping",
                                productId, variantId, qty);
                        return;
                    }

                    remaining -= qty;
                    item.setFulfillmentStatus(FulfillmentStatus.PENDING); // ready for warehouse packing

                    if (fulfillmentLocationId != null) {
                        locationRepository.findById(fulfillmentLocationId).ifPresent(loc -> {
                            item.setFulfillmentLocation(loc);
                            item.setFulfillmentLocationName(loc.getName());
                            // Decrement location stock to match the global decrement above.
                            // Best-effort: location stock drifting low is recoverable; stopping
                            // fulfillment here is not, so we log and continue on failure.
                            UUID variantRef = variantId; // null means product-level stock
                            locationStockRepository
                                    .findByLocationIdAndProductIdAndVariantRef(loc.getId(), productId, variantRef)
                                    .ifPresent(ls -> {
                                        int rows = locationStockRepository.decrementStock(ls.getId(), qty);
                                        if (rows == 0) {
                                            log.warn("fulfillPendingBackorders: location stock insufficient for location={} product={} qty={} — location stock not decremented",
                                                    loc.getId(), productId, qty);
                                        }
                                    });
                        });
                    }

                    // Capture stock before decrement from the JPA entity (loaded in this
                    // transaction, reflects the DB value before our decrementStock call).
                    Integer prevStockVal = (variantId != null && item.getVariant() != null)
                            ? item.getVariant().getStock()
                            : (item.getProduct() != null ? item.getProduct().getStock() : null);

                    InventoryAdjustment adj = new InventoryAdjustment();
                    adj.setProduct(item.getProduct());
                    adj.setVariant(item.getVariant());
                    adj.setDelta(-qty);
                    if (prevStockVal != null) {
                        adj.setPreviousStock(prevStockVal);
                        adj.setNewStock(prevStockVal - qty);
                    } else {
                        adj.setPreviousStock(0);
                        adj.setNewStock(0);
                    }
                    adj.setReason(AdjustmentReason.BACKORDER_FULFILLED);
                    adj.setNote("Backorder fulfilled for order #" + order.getId());
                    adj.setOrderId(order.getId());
                    adjustmentRepository.save(adj);
                }

                // Order remains PAID — the merchant will advance it to PACKED once all items are ready.
                orderRepository.save(order);
            }
        } finally {
            releaseLocks(acquiredLocks, lockToken);
        }
    }

    @Override
    public PagedResponse<CompanyOrderResponse> getCompanyOrders(UUID companyId, UUID ownerId, OrderStatus status, int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (status != null) {
            return new PagedResponse<>(
                    orderRepository.findAllByProductCompanyIdAndStatus(companyId, status, pageable)
                            .map(o -> toCompanyOrderResponse(o, companyId)));
        }
        return new PagedResponse<>(
                orderRepository.findAllByProductCompanyId(companyId, pageable)
                        .map(o -> toCompanyOrderResponse(o, companyId)));
    }

    @Override
    public CompanyOrderResponse getCompanyOrder(UUID companyId, UUID orderId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Order order = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        return toCompanyOrderResponse(order, companyId);
    }

    private CompanyOrderResponse toCompanyOrderResponse(Order order, UUID companyId) {
        List<OrderItem> companyItems = order.getItems().stream()
                .filter(item -> item.getBundle() != null
                        ? companyId.equals(item.getBundle().getCompany().getId())
                        : item.getProduct() != null && companyId.equals(item.getProduct().getCompany().getId()))
                .toList();

        BigDecimal total = companyItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OrderItemResponse> itemResponses = companyItems.stream()
                .map(this::toItemResponse)
                .toList();

        return new CompanyOrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getStatus().name(),
                order.getCurrency(),
                total,
                itemResponses,
                order.getFulfillmentMethod() != null ? order.getFulfillmentMethod().name() : FulfillmentMethod.DELIVERY.name(),
                order.getPickupLocation() != null ? order.getPickupLocation().getId() : null,
                order.getPickupLocationName(),
                order.getPickupReadyAt(),
                order.getShipRecipientName(),
                order.getShipStreet(),
                order.getShipStreet2(),
                order.getShipCity(),
                order.getShipState(),
                order.getShipPostalCode(),
                order.getShipCountry(),
                order.getShipPhoneNumber(),
                order.getTrackingNumber(),
                order.getCarrier(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getReturnedAt(),
                order.getFulfillmentNote(),
                order.getRefundedAmountCents(),
                order.getAssignedDriverId(),
                order.getCreatedAt());
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                items,
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getPaymentIntentId(),
                order.getPaymentClientSecret(),
                order.getCouponCode(),
                order.getCouponDiscountAmount(),
                order.getFulfillmentMethod() != null ? order.getFulfillmentMethod().name() : FulfillmentMethod.DELIVERY.name(),
                order.getPickupLocationName(),
                order.getPickupReadyAt(),
                order.getShipRecipientName(),
                order.getShipStreet(),
                order.getShipStreet2(),
                order.getShipCity(),
                order.getShipState(),
                order.getShipPostalCode(),
                order.getShipCountry(),
                order.getShipPhoneNumber(),
                order.getTrackingNumber(),
                order.getCarrier(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getReturnedAt(),
                order.getFulfillmentNote(),
                order.getRefundedAmountCents(),
                order.getAssignedDriverId(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        List<backend.dtos.responses.order.KitSelectionResponse> kitSelections = null;
        if (item.getKit() != null && item.getKitSelections() != null) {
            kitSelections = item.getKitSelections().stream()
                    .map(oks -> new backend.dtos.responses.order.KitSelectionResponse(
                            oks.getSlot() != null ? oks.getSlot().getId() : null,
                            oks.getSlotName(),
                            oks.getProduct() != null ? oks.getProduct().getId() : null,
                            oks.getProductName(),
                            oks.getVariant() != null ? oks.getVariant().getId() : null,
                            oks.getVariantTitle(),
                            oks.getVariantSku(),
                            oks.getQuantity(),
                            oks.getUnitPrice()))
                    .toList();
        }
        return new OrderItemResponse(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductName(),
                item.getVariant() != null ? item.getVariant().getId() : null,
                item.getVariantTitle(),
                item.getVariantSku(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getFulfillmentLocation() != null ? item.getFulfillmentLocation().getId() : null,
                item.getFulfillmentLocationName(),
                item.getFulfillmentStatus(),
                item.getBundle() != null ? item.getBundle().getId() : null,
                item.getBundleName(),
                item.getKit() != null ? item.getKit().getId() : null,
                item.getKitName(),
                kitSelections,
                item.getDiscountAmount(),
                item.getFulfillmentMethod()
        );
    }

    private void validateKitSelections(ProductKit kit, List<CreateOrderRequest.KitSelectionRequest> selections) {
        Map<UUID, CreateOrderRequest.KitSelectionRequest> bySlot = new HashMap<>();
        for (CreateOrderRequest.KitSelectionRequest sel : selections) {
            if (bySlot.put(sel.getSlotId(), sel) != null) {
                throw new BadRequestException("Duplicate slot selection for slot id: " + sel.getSlotId());
            }
        }
        for (KitSlot slot : kit.getSlots()) {
            CreateOrderRequest.KitSelectionRequest sel = bySlot.get(slot.getId());
            if (sel == null) {
                if (slot.isRequired()) {
                    throw new BadRequestException("Kit '" + kit.getName()
                            + "' requires a selection for slot '" + slot.getName() + "'");
                }
                continue;
            }
            if (sel.getQuantity() < slot.getMinQty() || sel.getQuantity() > slot.getMaxQty()) {
                throw new BadRequestException("Slot '" + slot.getName() + "' quantity must be between "
                        + slot.getMinQty() + " and " + slot.getMaxQty());
            }
        }
        // Ensure no unknown slot IDs were supplied
        Set<UUID> kitSlotIds = kit.getSlots().stream().map(KitSlot::getId).collect(Collectors.toSet());
        for (UUID slotId : bySlot.keySet()) {
            if (!kitSlotIds.contains(slotId)) {
                throw new BadRequestException("Unrecognised slot id in kit '" + kit.getName() + "': " + slotId);
            }
        }
    }


    // -------------------------------------------------------------------------
    // Fulfillment transitions (merchant-facing)
    // -------------------------------------------------------------------------

    private void validateTransition(Order order, OrderStatus target, OrderStatus... allowed) {
        if (!Set.of(allowed).contains(order.getStatus())) {
            throw new ConflictException("Cannot transition order " + order.getId()
                    + " to " + target + ": current status is " + order.getStatus());
        }
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public CompanyOrderResponse markAsPacked(UUID companyId, UUID orderId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Order order = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        validateTransition(order, OrderStatus.PACKED, OrderStatus.PAID);

        for (OrderItem item : order.getItems()) {
            if (item.getFulfillmentStatus() == FulfillmentStatus.PENDING) {
                item.setFulfillmentStatus(FulfillmentStatus.PACKED);
            }
        }
        order.setStatus(OrderStatus.PACKED);
        order.setPackedAt(Instant.now());
        Order saved = orderRepository.save(order);
        recordHistory(saved, OrderHistoryEventType.STATUS_CHANGED, ownerId, null);
        publishSseEvent(saved, null, "status_update");
        return toCompanyOrderResponse(saved, companyId);
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public CompanyOrderResponse markAsPickupReady(UUID companyId, UUID orderId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Order order = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getFulfillmentMethod() != FulfillmentMethod.PICKUP) {
            throw new BadRequestException("This order is not a pickup order");
        }

        validateTransition(order, OrderStatus.PACKED, OrderStatus.PAID, OrderStatus.PACKED);

        for (OrderItem item : order.getItems()) {
            if (item.getFulfillmentStatus() == FulfillmentStatus.PENDING
                    || item.getFulfillmentStatus() == FulfillmentStatus.PACKED) {
                item.setFulfillmentStatus(FulfillmentStatus.PICKUP_READY);
            }
        }
        // Order-level status intentionally stays PACKED — PICKUP_READY is item-level only.
        order.setPickupReadyAt(Instant.now());
        Order saved = orderRepository.save(order);
        String locationName = saved.getPickupLocationName() != null ? saved.getPickupLocationName() : "store";
        String pickupNote = "Items ready for pickup at " + locationName;
        recordHistory(saved, OrderHistoryEventType.STATUS_CHANGED, ownerId, pickupNote);
        publishSseEvent(saved, pickupNote, "status_update");
        fulfillmentEventPublisher.publish(new OrderFulfillmentEvent.PickupReady(
                saved.getId(), saved.getUser().getId(), companyId,
                saved.getPickupLocationName(), saved.getPickupReadyAt()));
        return toCompanyOrderResponse(saved, companyId);
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public CompanyOrderResponse markAsShipped(UUID companyId, UUID orderId, UUID ownerId, ShipOrderRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Order order = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getFulfillmentMethod() == FulfillmentMethod.PICKUP) {
            throw new BadRequestException("Cannot ship a pickup order — use the pickup-ready endpoint instead");
        }

        validateTransition(order, OrderStatus.SHIPPED, OrderStatus.PACKED, OrderStatus.PARTIALLY_FULFILLED);

        Set<UUID> targetItemIds = (request.itemIds() != null && !request.itemIds().isEmpty())
                ? new java.util.HashSet<>(request.itemIds())
                : null;

        for (OrderItem item : order.getItems()) {
            if (item.getFulfillmentStatus() != FulfillmentStatus.PACKED) continue;
            if (targetItemIds != null && !targetItemIds.contains(item.getId())) continue;
            item.setFulfillmentStatus(FulfillmentStatus.SHIPPED);
        }

        order.setTrackingNumber(request.trackingNumber());
        if (request.carrier() != null) order.setCarrier(request.carrier());
        if (request.note() != null) order.setFulfillmentNote(request.note());
        order.setShippedAt(Instant.now());

        // Compute order-level status from item statuses
        boolean anyPacked = order.getItems().stream()
                .anyMatch(i -> i.getFulfillmentStatus() == FulfillmentStatus.PACKED
                            || i.getFulfillmentStatus() == FulfillmentStatus.PENDING);
        boolean allDoneOrShipped = order.getItems().stream()
                .allMatch(i -> i.getFulfillmentStatus() == FulfillmentStatus.SHIPPED
                            || i.getFulfillmentStatus() == FulfillmentStatus.CANCELLED
                            || i.getFulfillmentStatus() == FulfillmentStatus.BACKORDERED
                            || i.getFulfillmentStatus() == FulfillmentStatus.PREORDERED);

        if (allDoneOrShipped) {
            order.setStatus(OrderStatus.SHIPPED);
        } else if (anyPacked) {
            order.setStatus(OrderStatus.PARTIALLY_FULFILLED);
        }
        // else: remains PARTIALLY_FULFILLED if called again for remaining items

        Order saved = orderRepository.save(order);
        recordHistory(saved, OrderHistoryEventType.STATUS_CHANGED, ownerId, request.note());
        publishSseEvent(saved, request.note(), "status_update");
        String tn = saved.getTrackingNumber();
        String carrier = saved.getCarrier();
        if (tn != null) {
            fulfillmentEventPublisher.publish(new OrderFulfillmentEvent.Shipped(
                    saved.getId(), saved.getUser().getId(), companyId,
                    tn, carrier, saved.getShippedAt()));
            trackingService.registerTracking(saved.getId(), tn, carrier);
            if (notificationEventPublisher != null) {
                String firstName = saved.getUser().getFirstName();
                notificationEventPublisher.publish(new NotificationEvent.OrderShipped(
                        saved.getUser().getId(), saved.getId(), firstName, tn, carrier));
            }
        }
        return toCompanyOrderResponse(saved, companyId);
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public CompanyOrderResponse markAsDelivered(UUID companyId, UUID orderId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Order order = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getFulfillmentMethod() == FulfillmentMethod.PICKUP) {
            validateTransition(order, OrderStatus.DELIVERED, OrderStatus.PACKED);
            for (OrderItem item : order.getItems()) {
                if (item.getFulfillmentStatus() == FulfillmentStatus.PICKUP_READY
                        || item.getFulfillmentStatus() == FulfillmentStatus.PACKED
                        || item.getFulfillmentStatus() == FulfillmentStatus.PENDING) {
                    item.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
                }
            }
        } else {
            validateTransition(order, OrderStatus.DELIVERED, OrderStatus.SHIPPED, OrderStatus.PARTIALLY_FULFILLED);
            for (OrderItem item : order.getItems()) {
                if (item.getFulfillmentStatus() == FulfillmentStatus.SHIPPED) {
                    item.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
                }
            }
        }
        order.setDeliveredAt(Instant.now());
        order.setStatus(OrderStatus.DELIVERED);
        Order saved = orderRepository.save(order);
        recordHistory(saved, OrderHistoryEventType.STATUS_CHANGED, ownerId, null);
        publishSseEvent(saved, null, "status_update");
        fulfillmentEventPublisher.publish(new OrderFulfillmentEvent.Delivered(
                saved.getId(), saved.getUser().getId(), companyId, saved.getDeliveredAt()));
        if (notificationEventPublisher != null) {
            notificationEventPublisher.publish(new NotificationEvent.OrderDelivered(
                    saved.getUser().getId(), saved.getId(), saved.getUser().getFirstName()));
        }
        return toCompanyOrderResponse(saved, companyId);
    }

    @Override
    @Transactional
    public void autoMarkDeliveredByTracking(String trackingNumber) {
        Order order = orderRepository.findByTrackingNumber(trackingNumber).orElse(null);
        if (order == null || order.getStatus() == OrderStatus.DELIVERED) return;
        for (OrderItem item : order.getItems()) {
            if (item.getFulfillmentStatus() == FulfillmentStatus.SHIPPED) {
                item.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
            }
        }
        order.setDeliveredAt(Instant.now());
        order.setStatus(OrderStatus.DELIVERED);
        Order saved = orderRepository.save(order);
        recordHistory(saved, OrderHistoryEventType.STATUS_CHANGED, null, "Delivery confirmed by carrier");
        publishSseEvent(saved, "Delivery confirmed by carrier", "status_update");
        UUID companyId = saved.getItems().stream()
                .findFirst()
                .map(i -> i.getProduct().getCompany().getId())
                .orElse(null);
        fulfillmentEventPublisher.publish(new OrderFulfillmentEvent.Delivered(
                saved.getId(), saved.getUser().getId(), companyId, saved.getDeliveredAt()));
    }

    @Override
    @Transactional
    public void publishTrackingCheckpoint(String trackingNumber, String tag, Instant checkpointTime) {
        Order order = orderRepository.findByTrackingNumber(trackingNumber).orElse(null);
        if (order == null) return;
        if (stringRedisTemplate != null) {
            String dedupKey = "tracking:seen:" + trackingNumber + ":" + tag + ":" + checkpointTime;
            Boolean isNew = stringRedisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", java.time.Duration.ofHours(24));
            if (Boolean.FALSE.equals(isNew)) return;
        }
        recordHistory(order, OrderHistoryEventType.TRACKING_CHECKPOINT, null, tag);
        publishSseEvent(order, tag, "tracking_checkpoint");
    }

    @Override
    @Transactional
    public void markPickedUpByDriver(UUID orderId, UUID driverId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if (!driverId.equals(order.getAssignedDriverId())) {
            throw new backend.exceptions.http.ForbiddenException("Driver is not assigned to this order");
        }
        recordHistory(order, OrderHistoryEventType.DRIVER_PICKED_UP, driverId, "Driver picked up the order");
        publishSseEvent(order, "Driver picked up the order", "driver_checkpoint");
    }

    @Override
    @Transactional
    public void markArrivedByDriver(UUID orderId, UUID driverId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if (!driverId.equals(order.getAssignedDriverId())) {
            throw new backend.exceptions.http.ForbiddenException("Driver is not assigned to this order");
        }
        recordHistory(order, OrderHistoryEventType.DRIVER_ARRIVED, driverId, "Driver arrived at delivery address");
        publishSseEvent(order, "Driver arrived at delivery address", "driver_checkpoint");
    }

    @Override
    @Transactional
    public void markDeliveredByDriver(UUID orderId, UUID driverId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if (!driverId.equals(order.getAssignedDriverId())) {
            throw new backend.exceptions.http.ForbiddenException("Driver is not assigned to this order");
        }
        validateTransition(order, OrderStatus.DELIVERED, OrderStatus.SHIPPED, OrderStatus.PARTIALLY_FULFILLED);
        for (OrderItem item : order.getItems()) {
            if (item.getFulfillmentStatus() == FulfillmentStatus.SHIPPED) {
                item.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
            }
        }
        order.setDeliveredAt(Instant.now());
        order.setStatus(OrderStatus.DELIVERED);
        Order saved = orderRepository.save(order);
        recordHistory(saved, OrderHistoryEventType.STATUS_CHANGED, driverId, "Delivered by driver");
        publishSseEvent(saved, "Delivered by driver", "status_update");
        // Immediately clear driver location from cache — after delivery the driver's last
        // known position (potentially their home) must not remain accessible to the customer.
        cacheService.delete("delivery:location:" + orderId);
        cacheService.delete("delivery:ratelimit:" + orderId);
        UUID companyId = saved.getItems().stream()
                .findFirst()
                .map(i -> i.getProduct().getCompany().getId())
                .orElse(null);
        fulfillmentEventPublisher.publish(new OrderFulfillmentEvent.Delivered(
                saved.getId(), saved.getUser().getId(), companyId, saved.getDeliveredAt()));
    }

    @Override
    @Transactional
    public CompanyOrderResponse initiateReturn(UUID companyId, UUID orderId, UUID ownerId, ReturnOrderRequest request) {
        // Load order to translate legacy itemIds → BuyerReturnItemRequest list with full quantities
        Order order = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        List<BuyerReturnItemRequest> itemRequests = buildLegacyItemRequests(request.itemIds(), order);

        MerchantInitiateReturnRequest translated = new MerchantInitiateReturnRequest(
                itemRequests,
                null,                           // no reason in legacy request
                request.note(),
                request.restockItems(),
                request.issueRefund() ? null : 0L,  // null=auto-calc, 0=waive
                null                            // auto-select primary return location
        );

        returnService.merchantInitiateReturn(orderId, companyId, ownerId, translated);

        // Re-fetch to pick up all state changes made by ReturnService
        Order updated = orderRepository.findByIdAndProductCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return toCompanyOrderResponse(updated, companyId);
    }

    /**
     * Translates the legacy ReturnOrderRequest.itemIds (List&lt;Long&gt;) to the new request format.
     * If itemIds is null/empty all returnable items are included. Quantity defaults to the full
     * order-item quantity (the legacy API had no per-item quantity field).
     */
    private List<BuyerReturnItemRequest> buildLegacyItemRequests(List<UUID> itemIds, Order order) {
        List<OrderItem> targets;
        if (itemIds == null || itemIds.isEmpty()) {
            targets = order.getItems().stream()
                    .filter(i -> i.getFulfillmentStatus() == FulfillmentStatus.DELIVERED
                            || i.getFulfillmentStatus() == FulfillmentStatus.SHIPPED)
                    .toList();
        } else {
            Set<UUID> idSet = new java.util.HashSet<>(itemIds);
            targets = order.getItems().stream()
                    .filter(i -> idSet.contains(i.getId()))
                    .toList();
        }
        return targets.stream()
                .map(i -> new BuyerReturnItemRequest(i.getId(), i.getQuantity()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Risk / fraud engine integration
    // -------------------------------------------------------------------------

    /**
     * Assesses the in-flight order, persists the assessment, and applies the engine's
     * verdict according to the current mode.
     *
     * <ul>
     *   <li>SHADOW mode: always returns (the caller proceeds to Stripe); assessment row
     *       is still written so thresholds can be tuned against real traffic.</li>
     *   <li>ENFORCE + ALLOW: proceeds.</li>
     *   <li>ENFORCE + VERIFY: if the caller supplied a valid {@code riskVerificationToken}
     *       for this user, the token is consumed and the order proceeds. Otherwise the
     *       email step-up has already been dispatched inside the engine and we throw
     *       {@link RiskStepUpRequiredException} so the controller can surface HTTP 428.</li>
     *   <li>ENFORCE + BLOCK: the order is flipped to {@link OrderStatus#UNDER_REVIEW} and a
     *       PENDING {@link RiskReview} row is created. The Stripe call is skipped entirely.</li>
     * </ul>
     */
    private RiskAssessment runRiskAssessment(
            User user,
            Order order,
            Set<UUID> userSegmentIds,
            PricingResult pricing,
            String suppliedVerificationToken) {

        ClientInfo clientInfo = ClientRequestContext.get();
        String fingerprint = (clientInfo != null && clientInfo.userAgent() != null
                && !clientInfo.userAgent().isBlank())
                ? deviceService.computeFingerprint(clientInfo.userAgent())
                : null;

        List<UUID> companyIds = order.getItems().stream()
                .map(item -> item.getProduct() != null ? item.getProduct().getCompany().getId()
                        : (item.getBundle() != null ? item.getBundle().getCompany().getId() : null))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        RiskContext ctx = new RiskContext(
                user.getId(),
                user.getEmail(),
                user.getCreatedAt(),
                null, // lastLoginAt not currently exposed on User — fine, evaluators treat null as unknown
                userSegmentIds,
                order.getId(),
                order.getTotalAmount(),
                null, // no delivery yet on checkout
                order.getCurrency(),
                order.getCouponCode(),
                pricing != null ? pricing.couponSavings() : null,
                companyIds,
                null, // shippingCountry — GeoIP stub; wire up when Address entity lands
                clientInfo != null ? clientInfo.ip() : null,
                fingerprint,
                clientInfo != null ? clientInfo.userAgent() : null,
                clientInfo != null ? clientInfo.deviceType() : null,
                RiskAssessmentKind.CHECKOUT,
                Instant.now());

        RiskAssessmentResult result = riskEngine.assess(ctx);
        RiskMode mode = riskProperties.getMode();
        RiskAssessment saved = persistAssessment(ctx, result, mode);

        if (mode == RiskMode.SHADOW) {
            log.info("Risk(SHADOW) orderId={} action={} score={}",
                    order.getId(), result.action(), result.totalScore());
            return saved;
        }

        switch (result.action()) {
            case ALLOW -> { /* proceed */ }
            case VERIFY -> {
                if (suppliedVerificationToken == null || suppliedVerificationToken.isBlank()) {
                    throw new RiskStepUpRequiredException(order.getId(), "EMAIL");
                }
                UUID tokenUserId;
                try {
                    tokenUserId = emailVerificationService.consumeVerificationToken(suppliedVerificationToken);
                } catch (RuntimeException ex) {
                    throw new RiskStepUpRequiredException(order.getId(), "EMAIL");
                }
                if (!tokenUserId.equals(user.getId())) {
                    throw new RiskStepUpRequiredException(order.getId(), "EMAIL");
                }
                // token valid → proceed
            }
            case BLOCK -> {
                order.setStatus(OrderStatus.UNDER_REVIEW);
                RiskReview review = new RiskReview();
                review.setOrderId(order.getId());
                review.setAssessmentId(saved.getId());
                review.setStatus(RiskReviewStatus.PENDING);
                riskReviewRepository.save(review);
            }
        }
        return saved;
    }

    private RiskAssessment persistAssessment(RiskContext ctx, RiskAssessmentResult result, RiskMode mode) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setOrderId(ctx.orderId());
        assessment.setUserId(ctx.userId());
        assessment.setDecision(result.action());
        assessment.setScore(result.totalScore());
        assessment.setMode(mode);
        assessment.setKind(ctx.kind());
        assessment.setIp(ctx.clientIp());
        assessment.setDeviceFingerprint(ctx.deviceFingerprint());
        String ua = ctx.userAgent();
        if (ua != null && ua.length() > 512) {
            ua = ua.substring(0, 512);
        }
        assessment.setUserAgent(ua);
        assessment.setReasonsJson(serializeSignals(result));
        return riskAssessmentRepository.save(assessment);
    }

    private String serializeSignals(RiskAssessmentResult result) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            List<Map<String, Object>> signalsJson = new ArrayList<>();
            for (RiskSignal sig : result.signals()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", sig.type().name());
                row.put("decision", sig.decision().name());
                row.put("score", sig.scoreContribution());
                row.put("reason", sig.reason());
                signalsJson.add(row);
            }
            payload.put("signals", signalsJson);
            payload.put("warnings", result.warnings());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize risk signals", e);
            return "{}";
        }
    }

    @Override
    public PagedResponse<RiskReviewResponse> listRiskReviews(UUID companyId, UUID ownerId,
                                                             RiskReviewStatus status, int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);
        if (size > 50) size = 50;
        RiskReviewStatus effective = status != null ? status : RiskReviewStatus.PENDING;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PagedResponse<>(
                riskReviewRepository.findByCompanyIdAndStatus(companyId, effective, pageable)
                        .map(this::toRiskReviewResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public RiskAssessmentResponse getOrderRisk(UUID companyId, UUID orderId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        requireExclusiveCompanyOrder(order, orderId, companyId);
        RiskAssessment latest = riskAssessmentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No risk assessment on order " + orderId));
        return toRiskAssessmentResponse(latest);
    }

    /**
     * Approving a held order charges the customer, so — like {@link #createOrder} — the Stripe
     * PaymentIntent is created OUTSIDE any transaction: {@link #beginRiskApproval} validates in T1,
     * the intent is created with no DB connection held, and {@link #completeRiskApproval} attaches
     * it in T2. If T2 fails, the orphaned intent is cancelled.
     */
    @Override
    public OrderResponse approveRiskReview(UUID companyId, UUID orderId, UUID ownerId, RiskDecisionRequest req) {
        RiskApproval approval = self().beginRiskApproval(companyId, orderId, ownerId);

        PaymentIntentResult paymentIntent;
        try {
            paymentIntent = paymentService.createPaymentIntent(
                    approval.amountInCents(),
                    approval.currency(),
                    null,
                    Map.of("user_id", String.valueOf(approval.userId()),
                            "order_id", String.valueOf(orderId),
                            "risk_reviewed_by", String.valueOf(ownerId))
            );
        } catch (Exception e) {
            throw new backend.exceptions.http.BadGatewayException(
                    "Failed to create payment intent: " + e.getMessage());
        }

        try {
            return self().completeRiskApproval(orderId, paymentIntent, ownerId, req);
        } catch (Exception e) {
            // Intent was created in Stripe but not attached; cancel it so no orphaned hold remains.
            // Done in its own transaction so a failed cancel leaves a retryable PAYMENT_CANCEL row
            // (mirrors the normal-order failReservedOrder path — logs alone are not durable).
            try {
                self().cancelOrphanedIntent(orderId, paymentIntent.id());
            } catch (Exception ce) {
                log.error("approveRiskReview: failed to record/cancel orphaned intent {} after completion failure: {}",
                        paymentIntent.id(), ce.getMessage());
            }
            throw e;
        }
    }

    /**
     * Cancels a PaymentIntent that was created at Stripe but never attached to its order, recording
     * a {@link CompensationType#PAYMENT_CANCEL} row — COMPLETED on success, or FAILED (retryable by
     * the compensation scheduler) if the cancel call itself fails.
     */
    @Transactional
    public void cancelOrphanedIntent(UUID orderId, String intentId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        try {
            paymentService.cancelPaymentIntent(intentId);
            if (order != null) {
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancelled payment intent: " + intentId, CompensationStatus.COMPLETED);
            }
        } catch (Exception e) {
            log.error("cancelOrphanedIntent: failed to cancel payment intent {} for order {}: {}",
                    intentId, orderId, e.getMessage());
            if (order != null) {
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancel payment intent: " + intentId, CompensationStatus.FAILED, e.getMessage());
            }
        }
    }

    /** T1 of {@link #approveRiskReview}: authorize, validate review state, and compute the charge. */
    @Transactional(readOnly = true)
    public RiskApproval beginRiskApproval(UUID companyId, UUID orderId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        requireExclusiveCompanyOrder(order, orderId, companyId);
        if (order.getStatus() != OrderStatus.UNDER_REVIEW) {
            throw new ConflictException("Order is not under review (status=" + order.getStatus() + ")");
        }
        RiskReview review = riskReviewRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No pending review for order " + orderId));
        if (review.getStatus() != RiskReviewStatus.PENDING) {
            throw new ConflictException("Review already decided (status=" + review.getStatus() + ")");
        }
        long amountInCents = order.getTotalAmount().multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new RiskApproval(amountInCents, order.getCurrency(), order.getUser().getId());
    }

    /** T2 of {@link #approveRiskReview}: attach the intent, flip to RESERVED, and approve the review. */
    @Transactional
    public OrderResponse completeRiskApproval(UUID orderId, PaymentIntentResult paymentIntent,
                                              UUID ownerId, RiskDecisionRequest req) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        // Re-check under the T2 transaction in case the review was decided concurrently between phases.
        if (order.getStatus() != OrderStatus.UNDER_REVIEW) {
            throw new ConflictException("Order is not under review (status=" + order.getStatus() + ")");
        }
        RiskReview review = riskReviewRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No pending review for order " + orderId));
        if (review.getStatus() != RiskReviewStatus.PENDING) {
            throw new ConflictException("Review already decided (status=" + review.getStatus() + ")");
        }

        order.setPaymentIntentId(paymentIntent.id());
        order.setPaymentClientSecret(paymentIntent.clientSecret());
        order.setStatus(OrderStatus.RESERVED);
        orderRepository.save(order);
        recordHistory(order, OrderHistoryEventType.STATUS_CHANGED, ownerId, "Risk review approved");
        publishSseEvent(order, "Risk review approved", "status_update");

        review.setStatus(RiskReviewStatus.APPROVED);
        review.setDecidedByUserId(ownerId);
        review.setDecidedAt(Instant.now());
        if (req != null && req.getMerchantNote() != null) {
            review.setMerchantNote(req.getMerchantNote());
        }
        riskReviewRepository.save(review);

        return toResponse(order);
    }

    /** Carries the validated charge details from {@link #beginRiskApproval} to the Stripe call. */
    public record RiskApproval(long amountInCents, String currency, UUID userId) {}

    @Override
    @Transactional
    public OrderResponse rejectRiskReview(UUID companyId, UUID orderId, UUID ownerId, RiskDecisionRequest req) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        requireExclusiveCompanyOrder(order, orderId, companyId);
        if (order.getStatus() != OrderStatus.UNDER_REVIEW) {
            throw new ConflictException("Order is not under review (status=" + order.getStatus() + ")");
        }
        RiskReview review = riskReviewRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No pending review for order " + orderId));
        if (review.getStatus() != RiskReviewStatus.PENDING) {
            throw new ConflictException("Review already decided (status=" + review.getStatus() + ")");
        }

        // Delegate stock-restore + order-cancel to the existing cancel pipeline.
        OrderResponse cancelled = cancelOrderInternal(orderId, order.getUser().getId(),
                backend.models.enums.CancellationReason.RISK_REJECTED);

        review.setStatus(RiskReviewStatus.REJECTED);
        review.setDecidedByUserId(ownerId);
        review.setDecidedAt(Instant.now());
        if (req != null && req.getMerchantNote() != null) {
            review.setMerchantNote(req.getMerchantNote());
        }
        riskReviewRepository.save(review);

        return cancelled;
    }

    private RiskReviewResponse toRiskReviewResponse(RiskReview review) {
        RiskReviewResponse r = new RiskReviewResponse();
        r.setId(review.getId());
        r.setOrderId(review.getOrderId());
        r.setAssessmentId(review.getAssessmentId());
        r.setStatus(review.getStatus());
        r.setDecidedByUserId(review.getDecidedByUserId());
        r.setDecidedAt(review.getDecidedAt());
        r.setMerchantNote(review.getMerchantNote());
        r.setCreatedAt(review.getCreatedAt());
        if (review.getAssessmentId() != null) {
            riskAssessmentRepository.findById(review.getAssessmentId()).ifPresent(a -> {
                r.setScore(a.getScore());
                r.setTopReason(topReason(a));
            });
        }
        return r;
    }

    private void requireExclusiveCompanyOrder(Order order, UUID orderId, UUID companyId) {
        if (!orderBelongsExclusivelyToCompany(order, companyId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
    }

    private boolean orderBelongsExclusivelyToCompany(Order order, UUID companyId) {
        return order.getItems() != null
                && !order.getItems().isEmpty()
                && order.getItems().stream()
                .map(this::findOwningCompanyId)
                .allMatch(itemCompanyId -> companyId.equals(itemCompanyId));
    }

    private UUID findOwningCompanyId(OrderItem item) {
        if (item.getBundle() != null && item.getBundle().getCompany() != null) {
            return item.getBundle().getCompany().getId();
        }
        if (item.getKit() != null && item.getKit().getCompany() != null) {
            return item.getKit().getCompany().getId();
        }
        if (item.getProduct() != null && item.getProduct().getCompany() != null) {
            return item.getProduct().getCompany().getId();
        }
        return null;
    }

    private String topReason(RiskAssessment a) {
        String json = a.getReasonsJson();
        if (json == null || json.isBlank()) return null;
        try {
            var tree = objectMapper.readTree(json);
            var signals = tree.get("signals");
            if (signals == null || !signals.isArray()) return null;
            com.fasterxml.jackson.databind.JsonNode best = null;
            int bestScore = -1;
            for (var s : signals) {
                int sc = s.has("score") ? s.get("score").asInt() : 0;
                if (sc > bestScore) {
                    bestScore = sc;
                    best = s;
                }
            }
            return best != null && best.has("reason") ? best.get("reason").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void recordFailedPaymentAttempt(Order order, String paymentIntentId, String reason) {
        try {
            String ip = null;
            // riskAssessmentId is a loose FK still typed Long in Order entity — lookup skipped until entity migrates
            // if (order.getRiskAssessmentId() != null) {
            //     ip = riskAssessmentRepository.findById(order.getRiskAssessmentId()).map(RiskAssessment::getIp).orElse(null);
            // }
            FailedPaymentAttempt attempt = new FailedPaymentAttempt(
                    order.getUser().getId(),
                    order.getId(),
                    ip,
                    paymentIntentId,
                    reason);
            failedPaymentAttemptRepository.save(attempt);
        } catch (Exception ex) {
            // Best-effort telemetry — do not let signal capture break the webhook.
            log.warn("Failed to record FailedPaymentAttempt for orderId={}", order.getId(), ex);
        }
    }

    private RiskAssessmentResponse toRiskAssessmentResponse(RiskAssessment a) {
        List<RiskSignalResponse> signalList = new ArrayList<>();
        if (a.getReasonsJson() != null && !a.getReasonsJson().isBlank()) {
            try {
                var tree = objectMapper.readTree(a.getReasonsJson());
                var signals = tree.get("signals");
                if (signals != null && signals.isArray()) {
                    for (var s : signals) {
                        signalList.add(new RiskSignalResponse(
                                backend.models.enums.RiskSignalType.valueOf(s.get("type").asText()),
                                backend.models.enums.RiskDecision.valueOf(s.get("decision").asText()),
                                s.has("score") ? s.get("score").asInt() : 0,
                                s.has("reason") ? s.get("reason").asText() : null));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse risk assessment reasons for id={}", a.getId(), e);
            }
        }
        return new RiskAssessmentResponse(
                a.getId(),
                a.getOrderId(),
                a.getUserId(),
                a.getDecision(),
                a.getScore(),
                a.getMode(),
                a.getKind(),
                a.getIp(),
                a.getDeviceFingerprint(),
                a.getUserAgent(),
                a.getCreatedAt(),
                signalList);
    }

    // -------------------------------------------------------------------------
    // Subscription renewals
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public Order createRenewalOrder(Subscription subscription, String stripeInvoiceId, long amountPaidCents) {
        // Idempotency: invoice.paid can be delivered more than once.
        Order existing = orderRepository.findByStripeInvoiceId(stripeInvoiceId).orElse(null);
        if (existing != null) {
            return existing;
        }

        if (subscription.getItems().isEmpty()) {
            throw new BadRequestException("Cannot create renewal order: subscription has no items");
        }

        BigDecimal totalAmount = BigDecimal.valueOf(amountPaidCents)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP);

        Order order = new Order();
        order.setUser(subscription.getUser());
        order.setStatus(OrderStatus.PAID);
        order.setTotalAmount(totalAmount);
        order.setCurrency(subscription.getCurrency());
        order.setSubscription(subscription);
        order.setRenewal(true);
        order.setStripeInvoiceId(stripeInvoiceId);
        order.setPaidAt(Instant.now());
        order.setCompensated(true); // No reservation lifecycle: this order is born paid.

        // Collect and sort IDs to acquire locks in consistent order (prevents deadlock).
        java.util.TreeSet<UUID> renewalProductIds = new java.util.TreeSet<>();
        java.util.TreeSet<UUID> renewalVariantIds = new java.util.TreeSet<>();
        for (SubscriptionItem si : subscription.getItems()) {
            renewalProductIds.add(si.getProduct().getId());
            if (si.getVariant() != null) renewalVariantIds.add(si.getVariant().getId());
        }
        String renewalLockToken = UUID.randomUUID().toString();
        List<String> renewalLocks = new ArrayList<>();
        try {
            acquireLocks(new ArrayList<>(renewalProductIds), renewalLockToken, renewalLocks);
            acquireVariantLocks(new ArrayList<>(renewalVariantIds), renewalLockToken, renewalLocks);
        } catch (ConflictException e) {
            // Best-effort: proceed without lock rather than failing the entire renewal.
            // Concurrent renewals for the same subscription are rare; the BACKORDERED
            // path handles the stock=0 case gracefully.
            log.warn("createRenewalOrder: could not acquire all locks for invoice {} — proceeding without full lock coverage", stripeInvoiceId);
        }

        try {
            for (SubscriptionItem si : subscription.getItems()) {
                Product product = si.getProduct();
                int qty = si.getQuantity();

                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProduct(product);
                item.setProductName(product.getName());
                item.setQuantity(qty);
                item.setUnitPrice(BigDecimal.valueOf(si.getUnitPriceCents()).movePointLeft(2));

                if (si.getVariant() != null) {
                    ProductVariant variant = si.getVariant();
                    item.setVariant(variant);
                    item.setVariantTitle(buildVariantTitle(variant));
                    item.setVariantSku(variant.getSku());

                    int updated = variantRepository.decrementStock(variant.getId(), qty);
                    if (updated == 0) {
                        item.setFulfillmentStatus(FulfillmentStatus.BACKORDERED);
                    }
                } else {
                    int updated = productRepository.decrementStock(product.getId(), qty);
                    if (updated == 0) {
                        item.setFulfillmentStatus(FulfillmentStatus.BACKORDERED);
                    }
                }

                order.getItems().add(item);
            }
        } finally {
            releaseLocks(renewalLocks, renewalLockToken);
        }

        boolean hasMarketplaceItems = stampVendorIds(order.getItems());
        Order saved;
        try {
            saved = orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            // Concurrent invoice.paid delivery — the unique constraint on stripe_invoice_id
            // rejected the duplicate insert. Treat as idempotent success.
            return orderRepository.findByStripeInvoiceId(stripeInvoiceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Renewal order save failed on duplicate invoice but existing row not found: " + stripeInvoiceId, e));
        }

        if (hasMarketplaceItems) {
            saved.setMarketplaceOrder(true);
            createSubOrders(saved, saved.getItems());
            saved = orderRepository.save(saved);
            recordSubOrderCommission(saved);
        }

        try {
            User user = subscription.getUser();
            OrderResponse response = toResponse(saved);
            emailService.sendOrderReceiptEmail(user.getEmail(), user.getFirstName(), response);
        } catch (Exception e) {
            log.warn("Failed to send renewal receipt email for order {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    // -------------------------------------------------------------------------
    // Marketplace helpers
    // -------------------------------------------------------------------------

    /**
     * For each item whose product is listed on a marketplace, resolves the owning
     * MarketplaceVendor and stamps {@code item.vendorId}. Uses a single batch query
     * per marketplace to avoid N+1 lookups.
     *
     * <p>Returns true if at least one item was successfully attributed to a vendor — this drives
     * the {@code marketplaceOrder} flag and sub-order creation. A product carrying a
     * {@code marketplaceId} but with no matching MarketplaceVendor row is logged at ERROR (data
     * corruption) and left unattributed rather than aborting the whole order; such a line simply
     * won't produce a sub-order.
     */
    private boolean stampVendorIds(List<OrderItem> items) {
        // Group product company IDs by marketplace
        Map<UUID, Set<UUID>> marketplaceToCompanyIds = new HashMap<>();
        for (OrderItem item : items) {
            if (item.getProduct() != null && item.getProduct().getMarketplaceId() != null) {
                marketplaceToCompanyIds
                        .computeIfAbsent(item.getProduct().getMarketplaceId(), k -> new HashSet<>())
                        .add(item.getProduct().getCompany().getId());
            }
        }
        if (marketplaceToCompanyIds.isEmpty()) return false;

        // Batch-lookup vendors per marketplace, building a (marketplaceId:companyId) -> vendorId map.
        Map<String, UUID> vendorIdByKey = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : marketplaceToCompanyIds.entrySet()) {
            UUID mId = entry.getKey();
            for (MarketplaceVendor mv : marketplaceVendorRepository
                    .findByMarketplaceIdAndVendorCompanyIdIn(mId, entry.getValue())) {
                vendorIdByKey.put(mId + ":" + mv.getVendorCompany().getId(), mv.getId());
            }
        }

        // Stamp each marketplace line with its owning MarketplaceVendor id so historic
        // attribution survives later product ownership changes.
        boolean anyStamped = false;
        for (OrderItem item : items) {
            if (item.getProduct() == null || item.getProduct().getMarketplaceId() == null) continue;
            UUID vendorId = vendorIdByKey.get(
                    item.getProduct().getMarketplaceId() + ":" + item.getProduct().getCompany().getId());
            if (vendorId != null) {
                item.setVendorId(vendorId);
                anyStamped = true;
            } else {
                log.error("[MARKETPLACE] No MarketplaceVendor for product {} (marketplace {} company {}) — "
                                + "line left unattributed; commission/payout will not be recorded for it",
                        item.getProduct().getId(), item.getProduct().getMarketplaceId(),
                        item.getProduct().getCompany().getId());
            }
        }
        return anyStamped;
    }

    /**
     * Groups items by vendorId and creates one {@link SubOrder} per vendor.
     * Updates each item's {@code subOrderId}. Items with no vendorId (standalone products in a
     * mixed cart) are skipped.
     *
     * <p>Order-level discounts (coupon, loyalty, premium) are not encoded per line, so each
     * sub-order's commissionable {@code totalAmount} is the vendor's net line value scaled by
     * {@code collected / allLinesNet} (capped at 1). This pro-rates those discounts across all
     * lines by value, guaranteeing the sum of sub-order totals never exceeds what the platform
     * actually collected — so vendor commission/payout is never computed on more than was charged.
     */
    private void createSubOrders(Order order, List<OrderItem> items) {
        Map<UUID, List<OrderItem>> byVendor = items.stream()
                .filter(i -> i.getVendorId() != null)
                .collect(Collectors.groupingBy(OrderItem::getVendorId));
        if (byVendor.isEmpty()) return;

        Map<UUID, MarketplaceVendor> vendorById = marketplaceVendorRepository.findAllById(byVendor.keySet())
                .stream().collect(Collectors.toMap(MarketplaceVendor::getId, v -> v));

        // Distribute the actually-collected total across every line by value. allLinesNet is the
        // post-promotion subtotal (pre order-level discount); order.getTotalAmount() is the charged
        // amount after coupon/loyalty/premium. scale is in (0,1]; never scale up (tax/shipping could
        // push the charged total above line value — vendors are not credited platform-collected tax).
        BigDecimal allLinesNet = items.stream().map(this::lineNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = order.getTotalAmount() != null ? order.getTotalAmount() : allLinesNet;
        BigDecimal scale = (allLinesNet.signum() > 0 && collected.compareTo(allLinesNet) < 0)
                ? collected.divide(allLinesNet, 10, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        // Resolve vendors and their raw (pre-discount) subtotals, skipping unresolved vendors.
        record VendorGroup(MarketplaceVendor vendor, List<OrderItem> items, BigDecimal subtotal) {}
        List<VendorGroup> groups = new ArrayList<>();
        for (Map.Entry<UUID, List<OrderItem>> entry : byVendor.entrySet()) {
            MarketplaceVendor vendor = vendorById.get(entry.getKey());
            if (vendor == null) {
                log.warn("[MARKETPLACE] createSubOrders: vendor {} not found — {} item(s) left unattributed",
                        entry.getKey(), entry.getValue().size());
                continue;
            }
            BigDecimal subtotal = entry.getValue().stream()
                    .map(this::lineNet).reduce(BigDecimal.ZERO, BigDecimal::add);
            groups.add(new VendorGroup(vendor, entry.getValue(), subtotal));
        }
        if (groups.isEmpty()) return;

        // Largest-remainder allocation in integer cents. Independent per-vendor HALF_UP rounding
        // could otherwise sum to a few cents above the collected total; flooring then handing the
        // leftover cents to the largest fractional remainders keeps the exact-sum guarantee.
        int n = groups.size();
        long[] cents = new long[n];
        BigDecimal[] remainder = new BigDecimal[n];
        BigDecimal sumExact = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal exact = groups.get(i).subtotal().multiply(scale).movePointRight(2);
            BigDecimal floor = exact.setScale(0, RoundingMode.FLOOR);
            cents[i] = floor.longValueExact();
            remainder[i] = exact.subtract(floor);
            sumExact = sumExact.add(exact);
        }
        long sumFloor = 0;
        for (long c : cents) sumFloor += c;
        long leftover = sumExact.setScale(0, RoundingMode.HALF_UP).longValueExact() - sumFloor;
        Integer[] byRemainderDesc = new Integer[n];
        for (int i = 0; i < n; i++) byRemainderDesc[i] = i;
        java.util.Arrays.sort(byRemainderDesc, (a, b) -> remainder[b].compareTo(remainder[a]));
        for (int k = 0; k < leftover && k < n; k++) cents[byRemainderDesc[k]] += 1;

        String currency = order.getCurrency() != null ? order.getCurrency() : "USD";
        for (int i = 0; i < n; i++) {
            VendorGroup g = groups.get(i);
            BigDecimal total = BigDecimal.valueOf(cents[i]).movePointLeft(2);
            BigDecimal allocatedDiscount = g.subtotal().subtract(total).max(BigDecimal.ZERO);

            SubOrder subOrder = new SubOrder();
            subOrder.setOrder(order);
            subOrder.setMarketplaceVendor(g.vendor());
            subOrder.setMarketplaceId(g.vendor().getMarketplace().getId());
            subOrder.setStatus(SubOrderStatus.PENDING);
            subOrder.setSubtotal(g.subtotal());
            subOrder.setVendorDiscountAmount(allocatedDiscount);
            subOrder.setTotalAmount(total);
            subOrder.setCurrency(currency);
            SubOrder saved = subOrderRepository.save(subOrder);

            for (OrderItem item : g.items()) {
                item.setSubOrderId(saved.getId());
            }
        }
    }

    /**
     * Net contribution of a single line to its vendor's sub-order subtotal. {@code unitPrice}
     * is already the promotion-discounted per-unit price (see the pricing map-back in
     * {@code reserveOrder}, where {@code unitPrice = basePrice - perUnitSavings}); the line's
     * {@code discountAmount}/{@code promotionSavings} encode that same savings, so they must
     * NOT be subtracted again here. The net line revenue is simply unitPrice × quantity.
     */
    private BigDecimal lineNet(OrderItem item) {
        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    /**
     * Resolves the company ID to use for the loyalty program for a given set of order items.
     * For standalone orders, uses the first product's company. For marketplace orders, uses the marketplace company.
     */
    private UUID resolveOrderCompanyId(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return null;
        for (OrderItem item : items) {
            if (item.getProduct() != null) {
                // If there's a marketplace context, the marketplace company owns the loyalty program
                if (item.getProduct().getMarketplaceId() != null) {
                    return item.getProduct().getMarketplaceId();
                }
                return item.getProduct().getCompany().getId();
            }
        }
        // Fallback for kit-only or bundle-only carts where item.getProduct() is null.
        // Kit/bundle items set product = null explicitly during order item construction.
        for (OrderItem item : items) {
            if (item.getKit() != null && item.getKit().getCompany() != null) {
                return item.getKit().getCompany().getId();
            }
            if (item.getBundle() != null && item.getBundle().getCompany() != null) {
                return item.getBundle().getCompany().getId();
            }
        }
        return null;
    }

    /**
     * Computes and persists a {@link CommissionRecord} for each sub-order on a just-paid
     * marketplace order. Each sub-order is processed in its own {@code REQUIRES_NEW} transaction
     * ({@link #recordOneSubOrderCommission}) so the record and the vendor-balance credit either
     * both commit or both roll back — a failure can never leave a CommissionRecord without its
     * matching pending-balance credit. Per-sub-order failures are logged but do not abort the
     * surrounding payment-confirmation transaction.
     */
    private void recordSubOrderCommission(Order order) {
        Instant paidAt = order.getPaidAt();
        for (SubOrder subOrder : subOrderRepository.findAllByOrderId(order.getId())) {
            try {
                self().recordOneSubOrderCommission(subOrder.getId(), paidAt, order.getId());
            } catch (Exception e) {
                log.warn("[COMMISSION] Failed to record commission for sub_order {} on order {}: {}",
                        subOrder.getId(), order.getId(), e.getMessage());
            }
        }
    }

    /**
     * Records commission for a single sub-order atomically: sub-order snapshot, CommissionRecord,
     * and the VendorBalance pending credit all commit together (or roll back together) in a
     * dedicated transaction. Idempotent — if a CommissionRecord already exists for the sub-order
     * (unique per {@code sub_order_id}) the method returns without re-crediting the balance.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOneSubOrderCommission(UUID subOrderId, Instant paidAt, UUID orderId) {
        if (commissionRecordRepository.findBySubOrderId(subOrderId).isPresent()) {
            return; // already processed — do not credit the vendor balance a second time
        }
        SubOrder subOrder = subOrderRepository.findById(subOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("SubOrder not found: " + subOrderId));
        subOrder.setPaidAt(paidAt);

        CommissionEngine.CommissionResult result = commissionEngine.compute(subOrder);
        subOrder.setCommissionAmount(result.commissionAmount());
        subOrder.setNetVendorAmount(result.netVendorAmount());
        subOrderRepository.save(subOrder);

        CommissionRecord record = new CommissionRecord();
        record.setSubOrder(subOrder);
        record.setVendorId(subOrder.getMarketplaceVendor().getId());
        record.setMarketplaceId(subOrder.getMarketplaceId());
        record.setCommissionRate(result.commissionRate());
        record.setGrossAmount(result.grossAmount());
        record.setCommissionAmount(result.commissionAmount());
        record.setNetVendorAmount(result.netVendorAmount());
        record.setCurrency(result.currency());
        commissionRecordRepository.save(record);

        // Credit VendorBalance.pendingCents (hold period releases via scheduler).
        // R2-H5: clamp netVendor to >= 0. If a refund cycle pushes the engine to
        // return a negative net (refund > commission), we DO NOT debit the vendor's
        // balance via this path — the refund's own compensation pipeline handles
        // balance adjustments. Persisting a negative pending would silently put
        // the vendor in the red.
        long pendingCents = Math.max(0L, result.netVendorAmount()
                .multiply(BigDecimal.valueOf(100)).longValue());
        long grossCents = Math.max(0L, result.grossAmount()
                .multiply(BigDecimal.valueOf(100)).longValue());
        long commissionCents = Math.max(0L, result.commissionAmount()
                .multiply(BigDecimal.valueOf(100)).longValue());
        if (result.netVendorAmount().signum() < 0) {
            log.warn("[COMMISSION] Negative netVendor {} for sub_order {} on order {} — capping at 0; refund accounting must handle the debit",
                    result.netVendorAmount(), subOrderId, orderId);
        }
        vendorBalanceRepository.upsertPending(
                subOrder.getMarketplaceVendor().getId(),
                pendingCents, grossCents, commissionCents, result.currency());
    }
}
