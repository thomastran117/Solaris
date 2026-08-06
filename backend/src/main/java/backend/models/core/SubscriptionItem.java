package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single line on a subscription — one product (+ optional variant) at a given quantity.
 * Priced via a snapshot so that later product-price changes don't retroactively alter
 * existing subscriptions. Linked 1:1 to a Stripe subscription_item.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "subscription_items", indexes = {
        @Index(name = "idx_sub_item_subscription", columnList = "subscription_id")
})
public class SubscriptionItem {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity = 1;

    /** Snapshot of the per-unit price in cents at the time this item was attached. */
    @Column(nullable = false)
    private long unitPriceCents;

    @Column(nullable = true, length = 100)
    private String stripeSubscriptionItemId;
}
