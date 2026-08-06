package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.BillingInterval;
import backend.models.enums.SubscriptionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer's recurring-order agreement. Billing is executed by Stripe; this
 * row mirrors the Stripe subscription so we can link fulfillment orders to it
 * and drive customer-facing actions (pause/skip/cancel) against our own API.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_sub_user", columnList = "user_id"),
        @Index(name = "idx_sub_stripe_id", columnList = "stripe_subscription_id", unique = true),
        @Index(name = "idx_sub_status_next_billing", columnList = "status, next_billing_at")
})
@EntityListeners(AuditingEntityListener.class)
public class Subscription {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "company_id", nullable = true)
    private Company company;

    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SubscriptionItem> items = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Stripe linkage
    // -------------------------------------------------------------------------

    /**
     * Stripe subscription id. Nullable to support the local-first create flow: a PENDING
     * (INCOMPLETE) row is persisted BEFORE the Stripe subscription exists, then this id is set at
     * finalize. The unique index permits multiple NULLs (MySQL/H2), so pending rows don't collide.
     */
    @Column(name = "stripe_subscription_id", nullable = true, length = 100, unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_customer_id", nullable = false, length = 100)
    private String stripeCustomerId;

    /** The active recurring Price in Stripe. Changes whenever interval or unit price changes. */
    @Column(name = "stripe_price_id", nullable = false, length = 100)
    private String stripePriceId;

    /** Default payment method attached to the subscription. */
    @Column(nullable = true, length = 100)
    private String stripePaymentMethodId;

    // -------------------------------------------------------------------------
    // Cycle
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.INCOMPLETE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BillingInterval billingInterval;

    /** Multiplier on the interval — e.g. intervalCount=2 with MONTH = "every 2 months". */
    @Column(nullable = false)
    private int intervalCount = 1;

    @Column(nullable = true)
    private Instant currentPeriodStart;

    @Column(nullable = true)
    private Instant currentPeriodEnd;

    /** Denormalized copy of the next scheduled billing time for fast scheduler queries. */
    @Column(nullable = true)
    private Instant nextBillingAt;

    @Column(nullable = true)
    private Instant pausedAt;

    @Column(nullable = true)
    private Instant cancelledAt;

    /** Set when the subscription first enters PAST_DUE; cleared on successful payment. */
    @Column(nullable = true)
    private Instant pastDueSince;

    /** When true, the next due invoice will be skipped (we advance the billing anchor in Stripe). */
    @Column(nullable = false)
    private boolean skipNextCycle = false;

    /** When true, Stripe will cancel the subscription at the end of the current period. */
    @Column(nullable = false)
    private boolean cancelAtPeriodEnd = false;

    // -------------------------------------------------------------------------
    // Pricing snapshot (for reporting; Stripe is still authoritative)
    // -------------------------------------------------------------------------

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    /** Unit amount (in cents) for a single cycle across all items. */
    @Column(nullable = false)
    private long unitAmountCents = 0L;

    // -------------------------------------------------------------------------
    // Fulfillment
    // -------------------------------------------------------------------------

    @Embedded
    private ShippingAddress shippingAddress;

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
