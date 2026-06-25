package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.CancellationReason;
import backend.models.enums.DeliverySlotStatus;
import backend.models.enums.DeliveryWindow;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.OrderStatus;
import backend.models.enums.RiskAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_user", columnList = "user_id"),
        @Index(name = "idx_order_payment_intent", columnList = "payment_intent_id"),
        @Index(name = "idx_order_replacement_of", columnList = "replacement_of_order_id"),
        @Index(name = "idx_order_stripe_invoice", columnList = "stripe_invoice_id"),
        @Index(name = "idx_order_subscription", columnList = "subscription_id"),
        @Index(name = "idx_order_pickup_location", columnList = "pickup_location_id"),
        @Index(name = "idx_order_delivery_date", columnList = "preferred_delivery_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_order_payment_intent_id", columnNames = "payment_intent_id"),
        @UniqueConstraint(name = "uq_order_stripe_invoice_id", columnNames = "stripe_invoice_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    /**
     * Optimistic-lock guard. Concurrent status transitions, refund denormalisation and
     * fulfilment updates load-then-save an Order; without this column, two writers who
     * read at the same version would silently overwrite each other's changes.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SubOrder> subOrders = new ArrayList<>();

    /** True when at least one item in this order belongs to a marketplace vendor. */
    @Column(nullable = false)
    private boolean marketplaceOrder = false;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    /** FK to the coupon applied at checkout. Null if no coupon was used. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "coupon_id", nullable = true)
    private Coupon coupon;

    /** Snapshot of the coupon code at order time. Null if no coupon was used. */
    @Column(nullable = true, length = 50)
    private String couponCode;

    /** Amount deducted from the pre-coupon total. Zero if no coupon was applied. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal couponDiscountAmount = BigDecimal.ZERO;

    /**
     * Total savings from PromotionRule-driven discounts (stackable + non-stackable).
     * Separate from {@link #couponDiscountAmount} so settlement reports can split
     * code-redeemed savings from rule-driven savings.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal promotionSavings = BigDecimal.ZERO;

    // -------------------------------------------------------------------------
    // Sales tax (Feature 14) — destination-based, snapshotted at order time.
    // The jurisdiction snapshot is authoritative: an admin can edit/delete the
    // TaxRate row later, so taxRateId alone is not enough to explain the charge.
    // -------------------------------------------------------------------------

    /** Sales tax charged on this order, in major units. Zero when no tax applied. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** Base the tax was applied to (post-discount subtotal + taxable shipping). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    /** Fractional rate applied (e.g. 0.08875). Zero when no tax applied. */
    @Column(nullable = false, precision = 6, scale = 5)
    private BigDecimal taxRate = BigDecimal.ZERO;

    /** Destination country used for the tax decision (snapshot). Empty when no tax computed. */
    @Column(nullable = false, length = 2)
    private String taxCountry = "";

    /** Destination state used for the tax decision (snapshot). Empty for country-level/no tax. */
    @Column(nullable = false, length = 2)
    private String taxState = "";

    /** Destination postal code used for the tax decision (snapshot). Empty unless a ZIP rate matched. */
    @Column(name = "tax_postal_code", nullable = false, length = 20)
    private String taxPostalCode = "";

    /** Whether shipping was taxed for this order (snapshot of the jurisdiction flag). */
    @Column(name = "shipping_taxable", nullable = false)
    private boolean shippingTaxable = false;

    /** Why the rate was chosen — DESTINATION_MATCH / STATE_DEFAULT / COUNTRY_DEFAULT / CONFIG_FALLBACK / NONE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private backend.models.enums.TaxSource taxSource = backend.models.enums.TaxSource.NONE;

    /** Loose FK to {@code tax_rates.id} of the matched row, for traceability. Null for fallback/none. */
    @Column(name = "tax_rate_id", nullable = true, columnDefinition = "BINARY(16)")
    private java.util.UUID taxRateId;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private OrderStatus status = OrderStatus.RESERVED;

    @Column(nullable = true, length = 255)
    private String paymentIntentId;

    @Column(nullable = true, length = 255)
    private String paymentClientSecret;

    @Column(nullable = true, length = 500)
    private String failureReason;

    @Column(nullable = false)
    private boolean compensated = false;

    // -------------------------------------------------------------------------
    // SLA timestamps
    // -------------------------------------------------------------------------

    /** When Stripe webhook confirmed payment. Null while RESERVED. */
    @Column(nullable = true)
    private Instant paidAt;

    /** When the merchant marked the order as PACKED. Null until that transition. */
    @Column(nullable = true)
    private Instant packedAt;

    /** When the order was cancelled (customer, payment failure, risk reject, or scheduler). */
    @Column(nullable = true)
    private Instant cancelledAt;

    /** Why the order was cancelled. Null on non-cancelled orders. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 25)
    private CancellationReason cancellationReason;

    // -------------------------------------------------------------------------
    // Fulfillment method + pickup
    // -------------------------------------------------------------------------

    /** Order-level fulfillment method: DELIVERY (ship to address) or PICKUP (collect in store). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'DELIVERY'")
    private FulfillmentMethod fulfillmentMethod = FulfillmentMethod.DELIVERY;

    /** For PICKUP orders: the store/hybrid location the customer will collect from. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "pickup_location_id", nullable = true)
    private InventoryLocation pickupLocation;

    /** Snapshot of the pickup location name at order time. Null for DELIVERY orders. */
    @Column(nullable = true, length = 255)
    private String pickupLocationName;

    /** Timestamp when the merchant marked all PICKUP items as PICKUP_READY. */
    @Column(nullable = true)
    private Instant pickupReadyAt;

    // -------------------------------------------------------------------------
    // Scheduled delivery slot (DELIVERY orders only — preference capture, v1)
    // All nullable: an order without a requested slot flows through fulfillment unchanged.
    // -------------------------------------------------------------------------

    /** Customer's preferred delivery date. Null until a slot is requested. */
    @Column(name = "preferred_delivery_date", nullable = true)
    private LocalDate preferredDeliveryDate;

    /** Customer's preferred time window. Null if none chosen. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    private DeliveryWindow preferredDeliveryWindow;

    /** Slot lifecycle: REQUESTED → CONFIRMED / UNAVAILABLE. Null until a slot is requested. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    private DeliverySlotStatus deliverySlotStatus;

    // -------------------------------------------------------------------------
    // Shipping address snapshot (DELIVERY orders only)
    // Snapshotted at checkout so a later address change does not affect the record.
    // -------------------------------------------------------------------------

    @Column(nullable = true, length = 150)
    private String shipRecipientName;

    @Column(nullable = true, length = 255)
    private String shipStreet;

    @Column(nullable = true, length = 255)
    private String shipStreet2;

    @Column(nullable = true, length = 100)
    private String shipCity;

    @Column(nullable = true, length = 100)
    private String shipState;

    @Column(nullable = true, length = 20)
    private String shipPostalCode;

    @Column(nullable = true, length = 2)
    private String shipCountry;

    @Column(nullable = true, length = 30)
    private String shipPhoneNumber;

    // -------------------------------------------------------------------------
    // Fulfillment tracking
    // -------------------------------------------------------------------------

    /** Carrier tracking number (set when order transitions to SHIPPED). */
    @Column(nullable = true, length = 100)
    private String trackingNumber;

    /** Carrier name (e.g. "UPS", "FedEx"). Set alongside trackingNumber. */
    @Column(nullable = true, length = 60)
    private String carrier;

    /** Timestamp when all items were handed to the carrier. */
    @Column(nullable = true)
    private Instant shippedAt;

    /** Timestamp when delivery was confirmed. */
    @Column(nullable = true)
    private Instant deliveredAt;

    /** Timestamp when items were returned by the customer. */
    @Column(nullable = true)
    private Instant returnedAt;

    /** Optional note added by the merchant during fulfillment actions. */
    @Column(nullable = true, length = 500)
    private String fulfillmentNote;

    // -------------------------------------------------------------------------
    // Shipping rate selection (Feature 13)
    // -------------------------------------------------------------------------

    /** Provider's selected rate id (e.g. EasyPost rate_xxx, or "flat-rate"). Null until a rate is chosen. */
    @Column(nullable = true, length = 100)
    private String shippingRateId;

    /** Selected carrier name (e.g. "USPS", "UPS"). */
    @Column(nullable = true, length = 60)
    private String shippingCarrier;

    /** Selected service machine code. */
    @Column(nullable = true, length = 60)
    private String shippingServiceCode;

    /** Selected service display name (e.g. "Priority"). */
    @Column(nullable = true, length = 100)
    private String shippingServiceName;

    /** ISO 4217 currency the shipping rate was quoted in. */
    @Column(nullable = true, length = 3)
    private String shippingRateCurrency;

    /** Carrier estimated transit days for the selected rate; null when not provided. */
    @Column(nullable = true)
    private Integer shippingEstimatedDays;

    /** Selected shipping cost in cents, folded into {@link #totalAmount}. Zero until a rate is chosen. */
    @Column(nullable = false)
    private long shippingCostCents = 0L;

    /** When the selected rate was quoted (EasyPost rate ids expire ~15 min). */
    @Column(nullable = true)
    private Instant shippingRateQuotedAt;

    // -------------------------------------------------------------------------
    // Refund tracking
    // -------------------------------------------------------------------------

    /** Cumulative amount refunded across all Return records, in cents. Denormalized for fast status checks. */
    @Column(nullable = false)
    private long refundedAmountCents = 0L;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<Return> returns = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Support / replacement tracking
    // -------------------------------------------------------------------------

    /** Loose FK to orders.id — non-null when this order was created as a replacement for another. */
    @Column(nullable = true)
    private Long replacementOfOrderId;

    /** Amount of store credit applied at checkout, in cents. Zero if no credit was used. */
    @Column(nullable = false)
    private long creditAppliedCents = 0L;

    /** Loyalty points redeemed at checkout. Zero if no points were used. */
    @Column(nullable = false)
    private int loyaltyPointsApplied = 0;

    /** Monetary discount (cents) generated by loyalty point redemption. Zero if no points were used. */
    @Column(nullable = false)
    private long loyaltyDiscountCents = 0L;

    /** True when the buyer held a Premium subscription at the time of order creation. */
    @Column(nullable = false)
    private boolean priorityOrder = false;

    /** 5% Premium discount applied at checkout (in cents). Zero for FREE-tier orders. */
    @Column(nullable = false)
    private long premiumDiscountCents = 0L;

    // -------------------------------------------------------------------------
    // Risk / fraud engine
    // -------------------------------------------------------------------------

    /** Total score from the most recent checkout risk assessment (0–100+). Null if engine never ran. */
    @Column(nullable = true)
    private Integer riskScore;

    /** Engine verdict at checkout. Persisted even in SHADOW mode so we can compare to actual status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 10)
    private RiskAction riskDecision;

    /** Loose FK to {@code risk_assessments.id}. Not a JPA relationship to keep assessment lifecycle independent. */
    @Column(nullable = true)
    private Long riskAssessmentId;

    // -------------------------------------------------------------------------
    // Subscription linkage
    // -------------------------------------------------------------------------

    /** Non-null when this order was created as a fulfillment for a recurring subscription. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "subscription_id", nullable = true)
    private Subscription subscription;

    /** True when this order was auto-generated by a subscription renewal (invoice.paid). */
    @Column(nullable = false)
    private boolean isRenewal = false;

    /** Stripe invoice ID that paid for this order (subscription renewals only). Used for idempotency. */
    @Column(name = "stripe_invoice_id", nullable = true, length = 100)
    private String stripeInvoiceId;

    /**
     * B2B quote this order was converted from (Feature 12). Unique so a quote can convert to at most
     * one order — the DB constraint makes {@code createOrderFromQuote} idempotent even if the accept
     * lock expires mid-flight or a post-creation step fails and the buyer retries.
     */
    @Column(name = "b2b_quote_id", nullable = true, unique = true, columnDefinition = "BINARY(16)")
    private UUID b2bQuoteId;

    @Column(name = "assigned_driver_id", nullable = true, columnDefinition = "BINARY(16)")
    private UUID assignedDriverId;

    // -------------------------------------------------------------------------
    // Audit
    // -------------------------------------------------------------------------

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
