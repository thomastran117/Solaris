package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import org.hibernate.annotations.BatchSize;

import backend.models.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products", indexes = {
        @Index(name = "idx_product_company", columnList = "company_id"),
        @Index(name = "idx_product_sku_company", columnList = "sku, company_id", unique = true),
        @Index(name = "idx_product_marketplace", columnList = "marketplace_id"),
        @Index(name = "idx_products_status_scheduled_publish_at", columnList = "status, scheduled_publish_at"),
        @Index(name = "idx_product_pinned_until", columnList = "pinned_until")
})
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Non-null when this product is listed on a marketplace. Null for standalone (non-marketplace) products.
     * The FK points to the marketplace Company (the operator), not the vendor Company.
     */
    @Column(name = "marketplace_id", nullable = true, columnDefinition = "BINARY(16)")
    private UUID marketplaceId;

    /** When true, the vendor has enabled this product for marketplace display. Ignored when marketplaceId is null. */
    @Column(nullable = false)
    private boolean marketplaceListed = false;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = true, length = 100)
    private String sku;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = true, precision = 12, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(nullable = true, length = 100)
    private String category;

    @Column(nullable = true, length = 100)
    private String brand;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = true, length = 500)
    private String thumbnailUrl;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("displayOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("position ASC")
    private List<ProductOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("displayOrder ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("displayOrder ASC")
    private List<ProductAttribute> attributes = new ArrayList<>();

    @Column(nullable = true)
    private Integer stock;

    @Column(nullable = true)
    private Integer lowStockThreshold;

    /** Alert when stock falls to this percentage of maxStock (0–100). Null = no percent threshold. */
    @Column(nullable = true)
    private Integer lowStockThresholdPercent;

    /** Maximum / initial stock capacity. Used as denominator for percent threshold calculation. */
    @Column(nullable = true)
    private Integer maxStock;

    /** When true, a PENDING RestockRequest is automatically created when stock breaches a threshold. */
    @Column(nullable = false)
    private boolean autoRestockEnabled = false;

    /** Units to request in the auto-generated RestockRequest. Required when autoRestockEnabled is true. */
    @Column(nullable = true)
    private Integer autoRestockQty;

    @Column(nullable = true, precision = 10, scale = 3)
    private BigDecimal weight;

    @Column(nullable = true, length = 10)
    private String weightUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    /**
     * When {@link #status} is {@link ProductStatus#SCHEDULED}, the product flips to
     * {@link ProductStatus#ACTIVE} at or after this instant via {@code ProductSchedulingWorker}.
     */
    @Column(name = "scheduled_publish_at", nullable = true)
    private Instant scheduledPublishAt;

    /** Set the first time the product transitions to {@link ProductStatus#ACTIVE}. */
    @Column(name = "published_at", nullable = true)
    private Instant publishedAt;

    @Column(nullable = false)
    private boolean featured = false;

    @Column(nullable = false)
    private boolean purchasable = true;

    @Column(nullable = false)
    private boolean backorderEnabled = false;

    @Column(nullable = false)
    private boolean preorderEnabled = false;

    @Column(nullable = true)
    private Instant preorderExpectedDate;

    @Column(nullable = false)
    private boolean listed = true;

    // -------------------------------------------------------------------------
    // Merchandising — pin/boost ranking signals consumed by storefront search.
    // See backend.services.impl.collections.CollectionServiceImpl for the
    // per-collection equivalents (CollectionProduct.pinnedRank / boostWeight).
    // -------------------------------------------------------------------------

    /** Manual relevance multiplier (1–10) applied via Elasticsearch function_score. Null = neutral. */
    @Column(name = "boost_weight", nullable = true)
    private Integer boostWeight;

    /**
     * When non-null and in the future, the product is forced to the top tier of any storefront
     * listing (search, category, collection). The expiry is mirrored in the ES document so the
     * filter clause can use a range query; a reindex after expiry releases the pin.
     */
    @Column(name = "pinned_until", nullable = true)
    private Instant pinnedUntil;

    /** Tie-breaker among pinned products — lower value surfaces first. Ignored when not pinned. */
    @Column(name = "pinned_rank", nullable = true)
    private Integer pinnedRank;

    // -------------------------------------------------------------------------
    // Subscription / recurring orders
    // -------------------------------------------------------------------------

    /** When true, this product can be purchased as a recurring subscription. */
    @Column(nullable = false)
    private boolean subscribable = false;

    /**
     * Allowed billing cadences when subscribed, encoded as comma-separated
     * {@code INTERVAL:COUNT} pairs (e.g. {@code "MONTH:1,MONTH:3,WEEK:2"}).
     * Null/blank means every cadence is allowed when {@link #subscribable} is true.
     */
    @Column(nullable = true, length = 255)
    private String subscriptionIntervals;

    /**
     * Optional subscriber discount applied to {@link #price} when ordered on a subscription.
     * E.g. {@code 10.00} = 10% off. Null = no discount.
     */
    @Column(nullable = true, precision = 5, scale = 2)
    private BigDecimal subscriptionDiscountPercent;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = true, updatable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = true, columnDefinition = "BINARY(16)")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
