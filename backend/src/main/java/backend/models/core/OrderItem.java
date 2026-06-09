package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import backend.models.enums.FulfillmentMethod;
import backend.models.enums.FulfillmentStatus;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_item_order", columnList = "order_id"),
        @Index(name = "idx_order_item_fulfillment_loc", columnList = "fulfillment_location_id"),
        @Index(name = "idx_order_item_vendor", columnList = "vendor_id")
})
public class OrderItem {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private java.time.Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    @Column(nullable = true, length = 255)
    private String variantTitle;

    @Column(nullable = true, length = 100)
    private String variantSku;

    @Column(nullable = false)
    private int quantity;

    /** Snapshot of the product/variant price at the time of order. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Per-unit discount applied at order time. Zero if no discount was active. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /**
     * Total savings from PromotionRule-driven discounts attributed to this line
     * (line-total, not per-unit). Zero when no promotion rule touched the line.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal promotionSavings = BigDecimal.ZERO;

    /**
     * Comma-separated snapshot of promotion rule ids that applied to this line.
     * Kept as a simple string (not JSON) to stay portable across MySQL/H2 without
     * pulling {@code @JdbcTypeCode(SqlTypes.JSON)} into the item table.
     */
    @Column(name = "applied_rule_ids", nullable = true, length = 500)
    private String appliedRuleIdsCsv;

    @Column(nullable = false, length = 255)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "fulfillment_location_id", nullable = true)
    private InventoryLocation fulfillmentLocation;

    /** Snapshot of the fulfillment location name at the time of order. */
    @Column(nullable = true, length = 255)
    private String fulfillmentLocationName;

    /**
     * Per-item fulfillment lifecycle status. Starts as PENDING (stock available), BACKORDERED
     * (zero stock with backorderEnabled), or PREORDERED (zero stock with preorderEnabled and
     * company preordersEnabled). Advances through PACKED → SHIPPED → DELIVERED, or RETURNED /
     * CANCELLED on terminal paths.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.PENDING;

    /**
     * Whether this line is shipped to the customer or collected in person.
     * Defaults to DELIVERY; checkout sets PICKUP when the customer chose a store.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'DELIVERY'")
    private FulfillmentMethod fulfillmentMethod = FulfillmentMethod.DELIVERY;

    /** Non-null for bundle order items; null for regular product items. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "bundle_id", nullable = true)
    private ProductBundle bundle;

    /** Snapshot of bundle name at order time. */
    @Column(name = "bundle_name", nullable = true, length = 255)
    private String bundleName;

    /** Non-null for kit order items; null for regular product/bundle items. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "kit_id", nullable = true)
    private ProductKit kit;

    /** Snapshot of kit name at order time. */
    @Column(name = "kit_name", nullable = true, length = 255)
    private String kitName;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<OrderKitSelection> kitSelections = new java.util.ArrayList<>();

    /**
     * Denormalized FK to the MarketplaceVendor that owns this line item's product.
     * Null for standalone (non-marketplace) orders. Set at order creation time from
     * product.company so historic attribution survives product ownership changes.
     */
    @Column(name = "vendor_id", nullable = true)
    private Long vendorId;

    /** FK to the SubOrder this item belongs to. Null for non-marketplace orders. */
    @Column(name = "sub_order_id", nullable = true, columnDefinition = "BINARY(16)")
    private java.util.UUID subOrderId;
}
